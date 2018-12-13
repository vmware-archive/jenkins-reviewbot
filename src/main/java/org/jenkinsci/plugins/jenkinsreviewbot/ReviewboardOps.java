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
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.jenkinsci.plugins.jenkinsreviewbot.util.Review;

import javax.annotation.Nullable;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by ymeymann on 24/04/15.
 */
public class ReviewboardOps {

  private static final long HOUR = 60 * 60 * 1000;
  private static ReviewboardOps instance = new ReviewboardOps(new HttpClient(new MultiThreadedHttpConnectionManager()));

  private final HttpClient http;

  ReviewboardOps(HttpClient http) { this.http = http; }

  public static ReviewboardOps getInstance() { return instance; }

  /* --------------------- logout ----------------------- */

  public static boolean logout(ReviewboardConnection con, HttpClient http) {
    PostMethod post = new PostMethod(con.getReviewboardURL() + "api/json/accounts/logout/");
    post.setDoAuthentication(true);
    try {
      int response = http.executeMethod(post);
      return response == 200;
    } catch (Exception e) {
      //ignore
      return false;
    } finally {
      post.releaseConnection();
    }
  }

//  public void close() {
//    final HttpConnectionManager connectionManager = http.getHttpConnectionManager();
//    if (connectionManager instanceof SimpleHttpConnectionManager) {
//      ((SimpleHttpConnectionManager) connectionManager).shutdown();
//    } else if (connectionManager instanceof MultiThreadedHttpConnectionManager) {
//      ((MultiThreadedHttpConnectionManager) connectionManager).shutdown();
//    }
//  }

  /* ------------- ensure authentication ---------------- */

  private static synchronized void initializeAuthentication(ReviewboardConnection con, HttpClient http) {
    ReviewboardConnection.Host host = con.getHost();
    http.getState().setCredentials(new AuthScope(host.host, host.port, AuthScope.ANY_REALM),
            new UsernamePasswordCredentials(con.getReviewboardUsername(), con.getReviewboardPassword()));
    http.getParams().setAuthenticationPreemptive(true);
  }

  public void ensureAuthentication(ReviewboardConnection con)  throws IOException {
    ensureAuthentication(con, http);
  }

  public static void ensureAuthentication(ReviewboardConnection con, HttpClient http)  throws IOException {
    int status = ensureAuthentication(con, http, true);
    if (status != 200) throw new IOException("HTTP status=" + status);
  }

  private static int ensureAuthentication(ReviewboardConnection con, HttpClient http, boolean withRetry) throws IOException {
    initializeAuthentication(con, http);
    GetMethod url = new GetMethod(con.getReviewboardURL() + "api/session/");
    try {
      url.setDoAuthentication(true);
      int res = http.executeMethod(url);
      return res;
    } catch (IOException e) {
      //trying to recover from failure...
      if (withRetry) return retryAuthentication(con, http); else throw e;
    } finally {
      url.releaseConnection();
    }
  }

  private static int retryAuthentication(ReviewboardConnection con, HttpClient http) throws IOException {
    initializeAuthentication(con, http);
    return ensureAuthentication(con, http, false);
  }

  /* --------------------- get diff --------------------- */

  public DiffHandle getDiff(String url) throws IOException {
    return getDiff(ReviewboardConnection.fromConfiguration(), url);
  }

  public DiffHandle getDiff(ReviewboardConnection con, String url) throws IOException {
    return new DiffHandle(con, http, url);
  }

  class DiffHandle implements Closeable {
    private final String url;
    private final ReviewboardConnection con;
    private final HttpClient http;
    private GetMethod get = null;
    private DiffHandle(ReviewboardConnection con, HttpClient http, String url) {
      this.url = url;
      this.con = con;
      this.http = http;
    }
    InputStream getStream() throws IOException {
      if (get == null) get = execDiffMethod(con, http, url);
      InputStream res = get.getResponseBodyAsStream();
      return res;
    }
    String getString() throws IOException {
      if (get == null) get = execDiffMethod(con, http, url);
      String res = get.getResponseBodyAsString();
      return res;
    }
    public void close() throws IOException {
      if (get != null) get.releaseConnection();
    }
  }

  private GetMethod execDiffMethod(ReviewboardConnection con, HttpClient http, String url) throws IOException {
    ensureAuthentication(con, http);
    String diffUrl = con.buildApiUrl(url, "diffs");
    Response d = getResponse(http, diffUrl, Response.class);
    if (d.count < 1) throw new RuntimeException("Review " + url + " has no diffs");
//    String diffUrl = url.concat("diff/raw/");
    GetMethod diff = new GetMethod(diffUrl + d.count + "/");
    diff.setDoAuthentication(true);
    diff.setRequestHeader("Accept", "text/x-patch");
    http.executeMethod(diff);
    return diff;
  }

  /* ------------------ get properties ------------------- */

  public Map<String,String> getProperties(String url) throws IOException {
    return getProperties(ReviewboardConnection.fromConfiguration(), url);
  }

  public Map<String,String> getProperties(ReviewboardConnection con, String url) throws IOException {
    ensureAuthentication(con, http);
    ReviewRequest response = getResponse(http, con.buildApiUrl(url, ""), ReviewRequest.class);
    String branch = response.request.branch;
    Map<String,String> m = new HashMap<String,String>();
    m.put("REVIEW_BRANCH", branch == null || branch.isEmpty() ? "master" : branch);
    String repo = response.request.links.repository.title;
    m.put("REVIEW_REPOSITORY", repo == null || repo.isEmpty() ? "unknown" : repo);
    String submitter = response.request.links.submitter.title;
    m.put("REVIEW_USER", submitter == null || submitter.isEmpty() ? "unknown" : submitter);
    return m;
  }

  /* ---------------- get pending reviews ---------------- */

  public Collection<Review.Slim> getPendingReviews(final ReviewboardConnection con, long periodInHours,
                                                   boolean restrictByUser, int repoid)
          throws IOException, JAXBException, ParseException {
    // Connect to Reviewboard
    ensureAuthentication(con, http);
    ReviewsResponse response = getResponse(http, con.getPendingReviewsUrl(restrictByUser, repoid), ReviewsResponse.class);
    // Get a list of the responses
    List<ReviewItem> list = response.requests.array;
    if (list == null || list.isEmpty()) return Collections.emptyList();
    Collections.sort(list, Collections.reverseOrder());
    // Filter reviews out if too old
    long period = periodInHours >= 0 ? periodInHours * HOUR : HOUR;
    final long coldThreshold = list.get(0).lastUpdated.getTime() - period;
    Collection<ReviewItem> hot = Collections2.filter(list, new Predicate<ReviewItem>() {
      public boolean apply(ReviewItem input) {
        return input.lastUpdated.getTime() >= coldThreshold; //check that the review is not too old
      }
    });
    // Turn ReviewItem into Review
    Collection<Review> hotRich = Collections2.transform(hot, new Function<ReviewItem, Review>() {
      public Review apply(@Nullable ReviewItem input) {
        Response d = getResponse(http, con.getDiffsUrl(input.id), Response.class);
        Date lastUploadTime = d.count < 1 ? null : d.diffs.array.get(d.count - 1).timestamp;
        String url = con.reviewNumberToUrl(Long.toString(input.id));
        return new Review(url, lastUploadTime, input);
      }
    });
    // Remove reviews requests that have already been reviewed by this program
    Collection<Review> unhandled = Collections2.filter(hotRich, new Predicate<Review>() {
      public boolean apply(Review input) {
        if (input.getLastUpdate() == null) return false; //no diffs found
        String commentsUrl = con.getCommentsUrl(input.getInput().id);
        Response c = getResponse(http, commentsUrl, Response.class);
        for (Item r : c.reviews.array) {
          if (con.getReviewboardUsername().equals(r.links.user.title) &&
                  r.timestamp.after(input.getLastUpdate())) {
            return false;
          }
        }
        return true;
      }
    });
    // Turn Review into Review.Slim
    Collection<Review.Slim> pendingReviews = Collections2.transform(unhandled, new Function<Review, Review.Slim>() {
      public Review.Slim apply(@Nullable Review input) { return input.trim(); }
    });
    return pendingReviews;
  }

  /* ----------------- post comment -------------------- */

  public boolean postComment(String url, String msg, boolean shipIt, boolean markdown) throws IOException {
    return postComment(ReviewboardConnection.fromConfiguration(), url, msg, shipIt, markdown);
  }

  public boolean postComment(ReviewboardConnection con, String url, String msg, boolean shipIt, boolean markdown)
          throws IOException {
    ensureAuthentication(con, http);
    String postUrl = con.buildApiUrl(url, "reviews");
    PostMethod post = new PostMethod(postUrl);
    post.setDoAuthentication(true);
    NameValuePair[] data = {
            new NameValuePair("body_top", msg),
            new NameValuePair("public", "true"),
            new NameValuePair("ship_it", String.valueOf(shipIt))
    };
    if (markdown) {
      List<NameValuePair> l = new LinkedList<NameValuePair>(Arrays.asList(data));
      l.add(new NameValuePair("body_top_text_type", "markdown"));
      l.add(new NameValuePair("text_type",          "markdown")); //some Reviewboard versions require it
      data = l.toArray(new NameValuePair[l.size()]);
    }
    post.setRequestBody(data);
    int response;
    try {
      response = http.executeMethod(post);
    } finally {
      post.releaseConnection();
    }
    return response == 200;
  }

  /* ------------------ get repositories ----------------- */

  public Map<String, Integer> getRepositories() throws IOException, JAXBException, ParseException {
    return getRepositories(ReviewboardConnection.fromConfiguration());
  }

  public Map<String, Integer> getRepositories(ReviewboardConnection con) throws IOException, JAXBException, ParseException {
    ensureAuthentication(con, http);
    return getRepositories(con.getRepositoriesUrl());
  }

  private SortedMap<String, Integer> getRepositories(String url) throws IOException, JAXBException, ParseException {
    ReviewboardConnection con = ReviewboardConnection.fromConfiguration();
    ensureAuthentication(con, http);
    Response response = getResponse(http, url, Response.class);
    SortedMap<String, Integer> map = new TreeMap<String, Integer>(String.CASE_INSENSITIVE_ORDER);
    if (response.count > 0) {
      for (Item i : response.repositories.array) {
        map.put(i.name, i.id);
      }
      if (response.links.next != null) {
        map.putAll(getRepositories(response.links.next.href));
      }
    }
    return map;
  }

  /* ------------------- unmarshalling ------------------- */

  private static  <T> T getResponse(HttpClient http, String requestUrl, Class<T> clazz) {
    GetMethod request = new GetMethod(requestUrl);
    int code;
    try {
      request.setDoAuthentication(true);
      request.setRequestHeader("Accept", "application/xml");
      code = http.executeMethod(request);
      if (code == 200) {
        InputStream res = request.getResponseBodyAsStream();
        JAXBContext jaxbContext = JAXBContext.newInstance(clazz);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        InputStreamReader reader = new InputStreamReader(res);
        return clazz.cast(unmarshaller.unmarshal(reader));
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      request.releaseConnection();
    }
    throw new RuntimeException("Accessing the URL " + requestUrl + " failed with code " + code);
  }

  /* ------------------ utility classes ------------------ */

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
    @XmlElement
    Links links;

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
    @XmlElement
    Items repositories;
    @XmlElement
    Links links;
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
    // for repositories
    @XmlElement
    int id;
    @XmlElement
    String name;
    @XmlElement
    String tool;
    @XmlElement
    String path;
  }
  public static class Links {
    @XmlElement
    User user;
    @XmlElement
    Repository repository;
    @XmlElement
    User submitter;
    @XmlElement
    Link next;
  }
  public static class Repository {
    @XmlElement
    String title;
  }
  public static class User {
    @XmlElement
    String title;
  }
  public static class Link {
    @XmlElement
    String href;
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
