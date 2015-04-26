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

import org.apache.commons.httpclient.auth.AuthScope;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: ymeymann
 * Date: 8/25/13 12:31 AM
 */
public class ReviewboardConnection {

  private final String reviewboardURL;
  private final String reviewboardUsername;
  private final String reviewboardPassword;

  private static final Pattern digitsPattern = Pattern.compile("\\d+");

  public static ReviewboardConnection fromConfiguration() {
    ReviewboardDescriptor d = ReviewboardNotifier.DESCRIPTOR;
    return new ReviewboardConnection(d.getReviewboardURL(), d.getReviewboardUsername(), d.getReviewboardPassword());
  }

  public ReviewboardConnection(String url, String user, String password) {
    reviewboardURL = url + (url.endsWith("/")?"":"/");
    reviewboardUsername = user;
    reviewboardPassword = password;
  }

  public String getReviewboardURL() {
    return reviewboardURL;
  }

  public String getReviewboardUsername() {
    return reviewboardUsername;
  }

  public String getReviewboardPassword() {
    return reviewboardPassword;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ReviewboardConnection that = (ReviewboardConnection) o;

    if (reviewboardURL != null ? !reviewboardURL.equals(that.reviewboardURL) : that.reviewboardURL != null)
      return false;
    if (reviewboardUsername != null ? !reviewboardUsername.equals(that.reviewboardUsername) : that.reviewboardUsername != null)
      return false;
    return !(reviewboardPassword != null ? !reviewboardPassword.equals(that.reviewboardPassword) : that.reviewboardPassword != null);

  }

  @Override
  public int hashCode() {
    int result = reviewboardURL != null ? reviewboardURL.hashCode() : 0;
    result = 31 * result + (reviewboardUsername != null ? reviewboardUsername.hashCode() : 0);
    result = 31 * result + (reviewboardPassword != null ? reviewboardPassword.hashCode() : 0);
    return result;
  }

  public static class Host {
    final String host;
    final int port;
    Host(String host, int port) { this.host = host; this.port = port; }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Host host1 = (Host) o;

      if (port != host1.port) return false;
      return !(host != null ? !host.equals(host1.host) : host1.host != null);

    }

    @Override
    public int hashCode() {
      int result = host != null ? host.hashCode() : 0;
      result = 31 * result + port;
      return result;
    }
  }

  public Host getHost() {
    // e.g. 'https://reviewboard.eng.vmware.com/' -> 'reviewboard.eng.vmware.com'
    String url = reviewboardURL;
    int startIndex = 0;
    int temp = url.indexOf("://");
    if (temp >= 0) startIndex = temp + 3;
    int endIndex = url.indexOf('/', startIndex);
    if (endIndex < 0) endIndex = url.length();
    String hostAndPort = url.substring(startIndex, endIndex);

    int colon = hostAndPort.indexOf(':');
    String host = colon >= 0 ? hostAndPort.substring(0, colon) : hostAndPort;
    int port = colon >= 0 ?
        Integer.parseInt(hostAndPort.substring(colon + 1)) :
        url.startsWith("http:") ? 80 :
        url.startsWith("https:") ? 443 :
        AuthScope.ANY_PORT;
    return new Host(host, port);
  }

  public String buildApiUrl(String url, String what) {
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

  public String getDiffsUrl(long id) {
    return buildApiUrlFromId(id, "diffs");
  }

  public String getPendingReviewsUrl(boolean onlyToJenkinsUser, int repoid) {
    //e.g. https://reviewboard.eng.vmware.com/api/review-requests/?to-users=...
    StringBuilder sb = new StringBuilder(128);
    sb.append(reviewboardURL).append("api/review-requests/");
    sb.append('?').append("status=pending");
    if (onlyToJenkinsUser) sb.append('&').append("to-users=").append(reviewboardUsername);
    sb.append('&').append("max-results=200");
    if (repoid >= 0) {
      // user selected to filter by repository
      // rationale is that different repository means different test job.
      sb.append('&').append("repository=" + repoid);
    }
    return sb.toString();
  }

  public String getCommentsUrl(long id) {
    return buildApiUrlFromId(id, "reviews");
  }

  public String getRepositoriesUrl() {
    // e.g. https://reviewboard.eng.vmware.com/api/repositories/
    return reviewboardURL.concat("api/repositories/?max-results=200");
  }

  public String buildApiUrlFromId(long id, String what) {
    StringBuilder sb = new StringBuilder(128);
    sb.append(reviewboardURL).append("api/review-requests/").append(id).append('/');
    if (what != null && !what.isEmpty()) sb.append(what).append('/');
    return sb.toString();
  }

}
