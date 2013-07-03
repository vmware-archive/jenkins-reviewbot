package org.jenkinsci.plugins.jenkinsreviewbot;

import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.Secret;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;

/**
* User: ymeymann
* Date: 6/27/13 3:55 PM
*/
public class ReviewboardDescriptor extends BuildStepDescriptor<Publisher> {

  private final HttpClient http = new HttpClient();

  private String reviewboardURL;
  private String reviewboardUsername;
  private Secret reviewboardPassword;

  public ReviewboardDescriptor() {
    super(ReviewboardNotifier.class);
    load();
  }

  @Override
  public String getDisplayName() {
    return Messages.ReviewboardNotifier_DisplayName();
  }

  @Override
  public boolean isApplicable(Class clazz) {
    return true;
  }

  @Override
  public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
    return super.newInstance(req, formData);
  }

  public String getReviewboardURL() {
    return reviewboardURL;
  }

  public String getReviewboardUsername() {
    return reviewboardUsername;
  }

  public String getReviewboardPassword() {
    return Secret.toString(reviewboardPassword);
  }

  @Override
  public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
//    String previousUrl = getReviewboardURL();
//    String previousUsername = getReviewboardUsername();
//    String previousPassword = getReviewboardPassword();
    reviewboardURL =      formData.getString("reviewboardURL");
    reviewboardUsername = formData.getString("reviewboardUsername");
    reviewboardPassword = Secret.fromString(formData.getString("reviewboardPassword"));
//    if (!reviewboardURL.equalsIgnoreCase(previousUrl) ||
//        !reviewboardUsername.equals(previousUsername) ||
//        !reviewboardPassword.equals(previousPassword)) {
//      logout(http, previousUrl);
//    }
    initializeAuthentication(http, getReviewboardURL(), getReviewboardUsername(), getReviewboardPassword());
    save();
    return super.configure(req,formData);
  }

  private void initializeAuthentication(HttpClient http, String url, String username, String password) {
    http.getParams().setAuthenticationPreemptive(true);
    String host = extractHost(url);
    http.getState().setCredentials(new AuthScope(host, 443, "Web API"),
                                   new UsernamePasswordCredentials(username, password));
  }

  public FormValidation doTestConnection(@QueryParameter("reviewboardURL")      final String reviewboardURL,
                                         @QueryParameter("reviewboardUsername") final String reviewboardUsername,
                                         @QueryParameter("reviewboardPassword") final String reviewboardPassword)
                        throws IOException, ServletException {
    try {
      HttpClient http = new HttpClient();
      initializeAuthentication(http, reviewboardURL, reviewboardUsername, reviewboardPassword);
      ensureAuthentication(http, reviewboardURL);
//      logout(http, reviewboardURL);
      return FormValidation.ok("Success");
    } catch (Exception e) {
      return FormValidation.error("Client error : "+e.getMessage());
    }
  }

  private boolean logout(HttpClient http, String url) {
    PostMethod post = new PostMethod(url + "api/json/accounts/logout/");
    try {
      int response = http.executeMethod(post);
      return response == 200;
    } catch (Exception e) {
      //ignore
      return false;
    }
  }

  private static String extractHost(String url) {
    // e.g. 'https://reviewboard.eng.vmware.com/' -> 'reviewboard.eng.vmware.com'
    int startIndex = 0;
    int temp = url.indexOf("://");
    if (temp >= 0) startIndex = temp + 3;
    int endIndex = url.indexOf('/', startIndex);
    if (endIndex < 0) endIndex = url.length();
    return url.substring(startIndex, endIndex);
  }

  public void ensureAuthentication() throws IOException {
    try {
      ensureAuthentication(http, getReviewboardURL());
    } catch (IOException e) {
      //trying to recover from failure...
      initializeAuthentication(http, getReviewboardURL(), getReviewboardUsername(), getReviewboardPassword());
      ensureAuthentication(http, getReviewboardURL());
    }
  }

  public void ensureAuthentication(HttpClient http, String reviewboardUrl) throws IOException {
    GetMethod url = new GetMethod(reviewboardUrl + "api/session");
    url.setDoAuthentication(true);
    http.executeMethod(url);
  }

  public String getDiffAsString(String url) throws IOException {
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

  private static String buildReviewApiUrl(String url) {
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

}
