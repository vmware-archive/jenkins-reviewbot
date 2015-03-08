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

  private String reviewboardURL;
  private String reviewboardUsername;
  private Secret reviewboardPassword;

  private boolean disableRepoCache = false;
  private boolean disableAutoApply = false;

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

  public boolean getDisableRepoCache() {
    return disableRepoCache;
  }

  public boolean getDisableAutoApply() {
    return disableAutoApply;
  }

  @Override
  public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
    reviewboardURL =      formData.getString("reviewboardURL");
    reviewboardUsername = formData.getString("reviewboardUsername");
    reviewboardPassword = Secret.fromString(formData.getString("reviewboardPassword"));
    if (formData.containsKey("disableRepoCache")) {
      disableRepoCache = formData.getBoolean("disableRepoCache");
    }
    if (formData.containsKey("disableAutoApply")) {
      disableAutoApply = formData.getBoolean("disableAutoApply");
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
