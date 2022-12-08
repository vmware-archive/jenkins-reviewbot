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
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.model.*;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.Util;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.CheckForNull;

import java.io.IOException;

/**
 * User: ymeymann
 * Date: 6/3/13 10:09 PM
 */
public class ReviewboardNotifier extends Notifier implements MatrixAggregatable, SimpleBuildStep {

  private boolean shipItOnSuccess = false;
  private boolean useMarkdown = false;
  @CheckForNull
  private String customMessage;

  @DataBoundConstructor
  public ReviewboardNotifier() {}

  @DataBoundSetter
  public void setShipItOnSuccess(boolean shipItOnSuccess) {
    this.shipItOnSuccess = shipItOnSuccess;
  }

  @DataBoundSetter
  public void setUseMarkdown(boolean useMarkdown) {
    this.useMarkdown = useMarkdown;
  }

  @DataBoundSetter
  public void setCustomMessage(@CheckForNull String customMessage) {
    this.customMessage = Util.fixNull(customMessage);
  }

  public boolean getShipItOnSuccess() {
    return shipItOnSuccess;
  }

  public boolean getUseMarkdown() {
    return useMarkdown;
  }

  @CheckForNull
  public String getCustomMessage() {
    return customMessage;
  }

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

  private boolean notifyReviewboard(TaskListener listener, Run<?, ?> run) throws IOException {
    listener.getLogger().println("Going to notify reviewboard about " + run.getDisplayName());
    ParametersAction paramAction = run.getAction(ParametersAction.class);
    ParameterValue param = paramAction.getParameter("review.url");
    ReviewboardParameterValue rbParam =
      param instanceof ReviewboardParameterValue ? (ReviewboardParameterValue)param :
      //for backwards compatibility
      param instanceof StringParameterValue ? ReviewboardParameterValue.wrap((StringParameterValue)param) :
      null;
    if (rbParam == null) throw new UnsupportedOperationException("review.url parameter is null or invalid");
    String url = rbParam.getLocation();
    if (run.getResult() == null) {
      if (run.isBuilding()) throw new AbortException("Cannot get the result of the build: it's still building");
      else throw new AbortException("Cannot get build result");
    }

    Result result = run.getResult();
    boolean success = result.equals(Result.SUCCESS);
    boolean unstable = result.equals(Result.UNSTABLE);

    try {
      EnvVars env = run.getEnvironment(listener);
      String link = env.get("BUILD_URL");
      link = decorateLink(run.getFullDisplayName(), link);
      String msg = success     ? Messages.ReviewboardNotifier_BuildSuccess() + " " + link:
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
  public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
    notifyReviewboard(listener, run);
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
