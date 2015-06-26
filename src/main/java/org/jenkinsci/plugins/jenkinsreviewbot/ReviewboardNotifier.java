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

import com.google.common.base.Strings;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.model.*;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

/**
 * User: ymeymann
 * Date: 6/3/13 10:09 PM
 */
public class ReviewboardNotifier extends Notifier implements MatrixAggregatable {

  private final boolean shipItOnSuccess;
  private boolean useMarkdown = false;
  private String customMessage = null;

  @DataBoundConstructor
  public ReviewboardNotifier(boolean shipItOnSuccess, boolean useMarkdown, String customMessage) {
    this.shipItOnSuccess = shipItOnSuccess;
    this.useMarkdown = useMarkdown;
    this.customMessage = customMessage;
  }

  public boolean getShipItOnSuccess() {
    return shipItOnSuccess;
  }

  public boolean getUseMarkdown() {
    return useMarkdown;
  }

  public String getCustomMessage() { return customMessage; }

  public BuildStepMonitor getRequiredMonitorService() {
    return BuildStepMonitor.STEP;
  }

  public MatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
    return new MatrixAggregator(build, launcher, listener) {
      @Override
      public boolean endBuild() throws InterruptedException, IOException {
        return notifyReviewboard(listener, this.build);
      }
    };
  }

  private boolean notifyReviewboard(BuildListener listener, AbstractBuild<?, ?> build) {
    listener.getLogger().println("Going to notify reviewboard about " + build.getDisplayName());
    ParametersAction paramAction = build.getAction(ParametersAction.class);
    ParameterValue param = paramAction.getParameter("review.url");
    ReviewboardParameterValue rbParam =
      param instanceof ReviewboardParameterValue ? (ReviewboardParameterValue)param :
      //for backwards compatibility
      param instanceof StringParameterValue ? ReviewboardParameterValue.wrap((StringParameterValue)param) :
      null;
    if (rbParam == null) throw new UnsupportedOperationException("review.url parameter is null or invalid");
    String url = rbParam.getLocation();
    Result result = build.getResult();
    boolean patchFailed = rbParam.isPatchFailed();
    boolean success = result.equals(Result.SUCCESS);
    boolean unstable = result.equals(Result.UNSTABLE);

    try {
      EnvVars env = build.getEnvironment(listener);
      String link = env.get("BUILD_URL");
      link = decorateLink(build.getFullDisplayName(), link);
      String msg = patchFailed ? Messages.ReviewboardNotifier_PatchError() + " " + link:
                   success     ? Messages.ReviewboardNotifier_BuildSuccess() + " " + link:
                   unstable    ? Messages.ReviewboardNotifier_BuildUnstable() + " " + link:
                                 Messages.ReviewboardNotifier_BuildFailure() + " " + link;
      if (!Strings.isNullOrEmpty(customMessage)) {
        msg = msg + "\n" + env.expand(customMessage);
      }
      ReviewboardOps.getInstance().postComment(url, msg, success && getShipItOnSuccess(), useMarkdown);
    } catch (Exception e) {
      listener.getLogger().println("Error posting to reviewboard: " + e.toString());
    }
    return true;
  }

  private String decorateLink(String name, String link) {
    return useMarkdown ? "["+name+"]("+link.trim()+")." : link;
  }

  @Override
  public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
    return notifyReviewboard(listener, build);
  }

  @Override
  public boolean needsToRunAfterFinalized() {
    return true;
  }

  @Override
  public ReviewboardDescriptor getDescriptor() {
    return DESCRIPTOR;
  }

  @Extension
  public static final ReviewboardDescriptor DESCRIPTOR = new ReviewboardDescriptor();

}
