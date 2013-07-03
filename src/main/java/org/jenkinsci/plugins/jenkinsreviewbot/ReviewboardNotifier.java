package org.jenkinsci.plugins.jenkinsreviewbot;

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

  @DataBoundConstructor
  public ReviewboardNotifier(boolean shipItOnSuccess) {
    this.shipItOnSuccess = shipItOnSuccess;
  }

  public boolean getShipItOnSuccess() {
    return shipItOnSuccess;
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

  private boolean notifyReviewboard(BuildListener listener, AbstractBuild<?, ?> build) {
    listener.getLogger().println("Going to notify reviewboard about " + build.getDisplayName());
    ParametersAction paramAction = build.getAction(ParametersAction.class);
    ReviewboardParameterValue rbParam = (ReviewboardParameterValue)paramAction.getParameter("review.url");
    String url = rbParam.getLocation();
    Result result = build.getResult();
    try {
      String link = build.getEnvironment(listener).get("BUILD_URL");
      boolean patchFailed = rbParam.isPatchFailed();
      boolean success = result.equals(Result.SUCCESS);
      boolean unstable = result.equals(Result.UNSTABLE);
      String msg = patchFailed ? Messages.ReviewboardNotifier_PatchError():
                   success     ? Messages.ReviewboardNotifier_BuildSuccess() + " " + link:
                   unstable    ? Messages.ReviewboardNotifier_BuildUnstable() + " " + link:
                                 Messages.ReviewboardNotifier_BuildFailure() + " " + link;

      DESCRIPTOR.postComment(url, msg, success && getShipItOnSuccess());
    } catch (Exception e) {
      listener.getLogger().println("Error posting to reviewboard: " + e.toString());
    }
    return true;
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
