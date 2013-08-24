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

//  private final HttpClient http = new HttpClient();
  private volatile ReviewboardConnection connection;

  private String reviewboardURL;
  private String reviewboardUsername;
  private Secret reviewboardPassword;

  public ReviewboardDescriptor() {
    super(ReviewboardNotifier.class);
    load();
  }

  public synchronized ReviewboardConnection getConnection() {
    if (connection == null) {
      connection = new ReviewboardConnection(getReviewboardURL(), getReviewboardUsername(), getReviewboardPassword());
    }
    return connection;
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
    reviewboardURL =      formData.getString("reviewboardURL");
    reviewboardUsername = formData.getString("reviewboardUsername");
    reviewboardPassword = Secret.fromString(formData.getString("reviewboardPassword"));
//    ReviewboardConnection oldConnection = connection;
//    if (oldConnection != null) {
//      oldConnection.logout();
//    }
    if (reviewboardURL == null || reviewboardURL.isEmpty()) {
      connection = null;
    } else {
      connection = new ReviewboardConnection(getReviewboardURL(), getReviewboardUsername(), getReviewboardPassword());
    }
    save();
    return super.configure(req,formData);
  }

  public FormValidation doTestConnection(@QueryParameter("reviewboardURL")      final String reviewboardURL,
                                         @QueryParameter("reviewboardUsername") final String reviewboardUsername,
                                         @QueryParameter("reviewboardPassword") final String reviewboardPassword)
                        throws IOException, ServletException {
    ReviewboardConnection con = null;
    try {
      con = new ReviewboardConnection(reviewboardURL, reviewboardUsername, reviewboardPassword);
      con.ensureAuthentication();
      return FormValidation.ok("Success");
    } catch (Exception e) {
      return FormValidation.error("Client error : "+e.getMessage());
    } finally {
      if (con != null) con.logout();
    }
  }

}
