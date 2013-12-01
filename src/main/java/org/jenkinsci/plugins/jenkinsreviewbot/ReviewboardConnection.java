/*
Copyright (c) 2013 VMware, Inc. All Rights Reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to
deal in the Software without restriction, including without limitation the
rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
sell copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
IN THE SOFTWARE.
*/

package org.jenkinsci.plugins.jenkinsreviewbot;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.SimpleHttpConnectionManager;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.*;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: ymeymann
 * Date: 8/25/13 12:31 AM
 */
public class ReviewboardConnection {

  private static final long HOUR = 60 * 60 * 1000;

  private final HttpClient http;

  private final String reviewboardURL;
  private final String reviewboardUsername;
  private final String reviewboardPassword;

  private static final Pattern digitsPattern = Pattern.compile("\\d+");

  public ReviewboardConnection(String url, String user, String password) {
    reviewboardURL = url + (url.endsWith("/")?"":"/");
    reviewboardUsername = user;
    reviewboardPassword = password;
    http = new HttpClient();
    initializeAuthentication();
  }

  private void initializeAuthentication() {
    String host = extractHost(reviewboardURL);
    http.getState().setCredentials(new AuthScope(host, 443, "Web API"),
        new UsernamePasswordCredentials(reviewboardUsername, reviewboardPassword));
    http.getParams().setAuthenticationPreemptive(true);
  }

  public boolean logout() {
    PostMethod post = new PostMethod(reviewboardURL + "api/json/accounts/logout/");
    try {
      int response = http.executeMethod(post);
      return response == 200;
    } catch (Exception e) {
      //ignore
      return false;
    }
  }

  public void close() {
    ((SimpleHttpConnectionManager)http.getHttpConnectionManager()).shutdown();
  }

  String extractHost(String url) {
    // e.g. 'https://reviewboard.eng.vmware.com/' -> 'reviewboard.eng.vmware.com'
    int startIndex = 0;
    int temp = url.indexOf("://");
    if (temp >= 0) startIndex = temp + 3;
    int endIndex = url.indexOf('/', startIndex);
    if (endIndex < 0) endIndex = url.length();
    return url.substring(startIndex, endIndex);
  }

  public void ensureAuthentication()  throws IOException {
    int status = ensureAuthentication(true);
    if (status != 200) throw new IOException("HTTP status=" + status);
  }

  private int ensureAuthentication(boolean withRetry) throws IOException {
    try {
      GetMethod url = new GetMethod(reviewboardURL + "api/"); //suspicion that session does not always exist
      url.setDoAuthentication(true);
      return http.executeMethod(url);
    } catch (IOException e) {
      //trying to recover from failure...
      if (withRetry) return retryAuthentication(); else throw e;
    }
  }

  private int retryAuthentication() throws IOException {
    initializeAuthentication();
    return ensureAuthentication(false);
  }


  private GetMethod execDiffMethod(String url) throws IOException {
    ensureAuthentication();
    String diffUrl = buildApiUrl(url, "diffs");
    Response d = unmarshalResponse(diffUrl, Response.class);
    if (d.count < 1) throw new RuntimeException("Review " + url + " has no diffs");
//    String diffUrl = url.concat("diff/raw/");
    GetMethod diff = new GetMethod(diffUrl + d.count + "/");
    diff.setRequestHeader("Accept", "text/x-patch");
    http.executeMethod(diff);
    return diff;
  }

  String getDiffAsString(String url) throws IOException {
    GetMethod get = execDiffMethod(url);
    String res = get.getResponseBodyAsString();
    return res;
  }

  InputStream getDiff(String url) throws IOException {
    GetMethod get = execDiffMethod(url);
    InputStream res = get.getResponseBodyAsStream();
    return res;
  }

  public boolean postComment(String url, String msg, boolean shipIt) throws IOException {
    ensureAuthentication();
    String postUrl = buildApiUrl(url, "reviews");
    PostMethod post = new PostMethod(postUrl);
    NameValuePair[] data = {
        new NameValuePair("body_top", msg),
        new NameValuePair("public", "true"),
        new NameValuePair("ship_it", String.valueOf(shipIt))
    };
    post.setRequestBody(data);
    int response = http.executeMethod(post);
    return response == 200;
  }

  String buildApiUrl(String url, String what) {
    //e.g. https://reviewboard.eng.vmware.com/r/474115/ ->
    //     https://reviewboard.eng.vmware.com/api/review-requests/474115/${what}/
    int splitPoint = url.indexOf("/r/");
    StringBuilder sb = new StringBuilder(url.length() + 25);
    sb.append(url.substring(0, splitPoint));
    sb.append("/api/review-requests/");
    int idIndex = splitPoint + 3;
    sb.append(url.substring(idIndex, url.indexOf('/', idIndex)));
    sb.append('/');
    if (what != null && !what.isEmpty()) sb.append(what).append('/');
    String res = sb.toString();
    return res;
  }

  String buildReviewUrl(String value) {
    Matcher m = digitsPattern.matcher(value);
    String number = m.find() ? m.group() : "0";
    return reviewNumberToUrl(number);
  }

  String reviewNumberToUrl(String number) {
    StringBuilder sb = new StringBuilder();
    sb.append(reviewboardURL).append("r/").append(number).append('/');
    return sb.toString();
  }

  String getBranch(String url) throws IOException {
    ensureAuthentication();
    ReviewRequest response = unmarshalResponse(buildApiUrl(url, ""), ReviewRequest.class);
    String branch = response.request.branch;
    return branch == null || branch.isEmpty() ? "master" : branch;
  }

  Collection<String> getPendingReviews(long periodInHours) throws IOException, JAXBException, ParseException {
    ensureAuthentication();
    ReviewsResponse response = unmarshalResponse(getRequestsUrl(), ReviewsResponse.class);
    List<ReviewItem> list = response.requests.array;
    if (list == null || list.isEmpty()) return Collections.emptyList();
    Collections.sort(list, Collections.reverseOrder());
    long period = periodInHours >= 0 ? periodInHours * HOUR : HOUR;
    final long coldThreshold = list.get(0).lastUpdated.getTime() - period;
    Collection<ReviewItem> hot = Collections2.filter(list, new Predicate<ReviewItem>(){
      public boolean apply(ReviewItem input) {
        return input.lastUpdated.getTime() >= coldThreshold; //check that the review is not too old
      }
    });
    Collection<ReviewItem> unhandled = Collections2.filter(hot, new NeedsBuild());
    Collection<String> res = Collections2.transform(unhandled, new Function<ReviewItem, String>() {
      public String apply(ReviewItem input) {
        return reviewNumberToUrl(Long.toString(input.id));
      }
    });
    return res;
  }

  private InputStream getXmlContent(String requestsUrl) throws IOException {
    GetMethod requests = new GetMethod(requestsUrl);
    requests.setRequestHeader("Accept", "application/xml");
    http.executeMethod(requests);
    return requests.getResponseBodyAsStream();
  }

  private String getRequestsUrl() {
    //e.g. https://reviewboard.eng.vmware.com/api/review-requests/?to-users=...
    StringBuilder sb = new StringBuilder(128);
    sb.append(reviewboardURL).append("api/review-requests/");
    sb.append('?').append("to-users=").append(reviewboardUsername);
    sb.append('&').append("status=pending");
    sb.append('&').append("max-results=200");
    return sb.toString();
  }

  private String getDiffsUrl(long id) {
    return buildApiUrlFromId(id, "diffs");
  }

  private String getCommentsUrl(long id) {
    return buildApiUrlFromId(id, "reviews");
  }

  private String buildApiUrlFromId(long id, String what) {
    StringBuilder sb = new StringBuilder(128);
    sb.append(reviewboardURL).append("api/review-requests/").append(id).append('/');
    if (what != null && !what.isEmpty()) sb.append(what).append('/');
    return sb.toString();
  }

  private <T> T unmarshalResponse(String requestUrl, Class<T> clazz) {
    try {
      InputStream res = getXmlContent(requestUrl);
      JAXBContext jaxbContext = JAXBContext.newInstance(clazz);
      Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
      InputStreamReader reader = new InputStreamReader(res);
      return clazz.cast(unmarshaller.unmarshal(reader));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private class NeedsBuild implements Predicate<ReviewItem> {
    public boolean apply(ReviewItem input) {
      Response d = unmarshalResponse(getDiffsUrl(input.id), Response.class);
      if (d.count < 1) return false; //no diffs found
      Date lastUploadTime = d.diffs.array.get(d.count - 1).timestamp;
      Response c = unmarshalResponse(getCommentsUrl(input.id), Response.class);
      //no comments from this user after last diff upload
      for (Item r : c.reviews.array) {
        if (reviewboardUsername.equals(r.links.user.title) &&
            r.timestamp.after(lastUploadTime)) {
          return false;
        }
      }
      return true;
    }
  }

  @XmlRootElement(name = "rsp")
  public static class ReviewRequest {
    @XmlElement(name = "review_request")
    ReviewItem request;
  }
  @XmlRootElement(name = "rsp")
  public static class ReviewsResponse {
    @XmlElement(name = "review_requests")
    ReviewsRequests requests;
    @XmlElement(name = "total_results")
    String total;
    @XmlElement
    String stat;
  }
  public static class ReviewsRequests {
    @XmlElementWrapper
    @XmlElement(name = "item")
    List<ReviewItem> array;
  }
  public static class ReviewItem implements Comparable<ReviewItem> {
    @XmlJavaTypeAdapter(MyDateAdapter.class)
    @XmlElement(name = "last_updated")
    Date lastUpdated;
    @XmlElement
    String branch;
    @XmlElement
    long id;

    public int compareTo(ReviewItem o) {
      try {
        return lastUpdated.compareTo(o.lastUpdated);
      } catch (Exception e) {
        return -1;
      }
    }
  }
  @XmlRootElement(name = "rsp")
  public static class Response {
    @XmlElement(name = "total_results")
    int count;
    @XmlElement
    Items diffs;
    @XmlElement
    Items reviews;
  }
  public static class Items {
    @XmlElementWrapper
    @XmlElement(name = "item")
    List<Item> array;
  }
  public static class Item {
    @XmlJavaTypeAdapter(MyDateAdapter.class)
    @XmlElement
    Date timestamp;
    @XmlElement
    Links links;
  }
  public static class Links {
    @XmlElement
    User user;
  }
  public static class User {
    @XmlElement
    String title;
  }

  public static class MyDateAdapter extends XmlAdapter<String, Date> {
    private static final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public Date unmarshal(String v) throws Exception {
      try {
        return javax.xml.bind.DatatypeConverter.parseDateTime(v).getTime();
      } catch (IllegalArgumentException iae) { //to support Reviewboard version 1.6
        try {
          return formatter.parse(v);
        } catch (ParseException e) {
          throw new RuntimeException(e);
        }
      }
    }

    @Override
    public String marshal(Date v) throws Exception { //isn't really used
      Calendar c = GregorianCalendar.getInstance();
      c.setTime(v);
      return javax.xml.bind.DatatypeConverter.printDateTime(c);
//      return formatter.format(v);
    }

  }

}
