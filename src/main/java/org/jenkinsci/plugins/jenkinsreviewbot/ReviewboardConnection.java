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

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: ymeymann
 * Date: 8/25/13 12:31 AM
 */
public class ReviewboardConnection {

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
    http.getParams().setAuthenticationPreemptive(true);
    String host = extractHost(reviewboardURL);
    http.getState().setCredentials(new AuthScope(host, 443, "Web API"),
        new UsernamePasswordCredentials(reviewboardUsername, reviewboardPassword));
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
      GetMethod url = new GetMethod(reviewboardURL + "api/session");
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


  String getDiffAsString(String url) throws IOException {
    ensureAuthentication();
    String diffUrl = url.concat("diff/raw/");
    GetMethod diff = new GetMethod(diffUrl);
    http.executeMethod(diff);
    String res = diff.getResponseBodyAsString();
    return res;
  }

  public boolean postComment(String url, String msg, boolean shipIt) throws IOException {
    ensureAuthentication();
    String postUrl = buildReviewApiUrl(url);
    PostMethod post = new PostMethod(postUrl);
    NameValuePair[] data = {
        new NameValuePair("body_top", msg),
        new NameValuePair("public", "true"),
        new NameValuePair("ship_it", String.valueOf(shipIt))
    };
    post.setRequestBody(data);
    int response = http.executeMethod(post);
    String responseBody = post.getResponseBodyAsString();
    return response == 200;
  }

  String buildReviewApiUrl(String url) {
    //e.g. https://reviewboard.eng.vmware.com/r/474115/ ->
    //     https://reviewboard.eng.vmware.com/api/review-requests/474115/reviews/
    int splitPoint = url.indexOf("/r/");
    StringBuilder sb = new StringBuilder(url.length() + 25);
    sb.append(url.substring(0, splitPoint));
    sb.append("/api/review-requests/");
    int idIndex = splitPoint + 3;
    sb.append(url.substring(idIndex, url.indexOf('/', idIndex)));
    sb.append("/reviews/");
    String res = sb.toString();
    return res;
  }

  String buildReviewUrl(String value) {
    StringBuilder sb = new StringBuilder();
    Matcher m = digitsPattern.matcher(value);
    String number = m.find() ? m.group() : "0";
    sb.append(reviewboardURL);
    sb.append("r/").append(number).append('/');
    return sb.toString();
  }


}
