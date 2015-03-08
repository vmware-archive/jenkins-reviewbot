package org.jenkinsci.plugins.jenkinsreviewbot;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

/**
 * User: ymeymann
 * Date: 3/8/2015 10:49 PM
 */
public class ReviewboardApplyPatch extends Builder {
  @DataBoundConstructor
  public ReviewboardApplyPatch() {}

  @Override
  public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
    if (!ReviewboardNotifier.DESCRIPTOR.getDisableAutoApply()) {
      listener.getLogger().println("Patch already applied. Ignoring");
      return true;
    }

    listener.getLogger().println("Applying "+ ReviewboardNote.encodeTo("the diff"));
    ParametersAction paramAction = build.getAction(ParametersAction.class);
    ParameterValue param = paramAction.getParameter("review.url");
    ReviewboardParameterValue rbParam = (ReviewboardParameterValue) param;
    if (rbParam == null) throw new UnsupportedOperationException("review.url parameter is null or invalid");
    FilePath patch = build.getWorkspace().child(ReviewboardParameterValue.LOCATION);
    try {
      patch.act(new ReviewboardParameterValue.ApplyTask());
      return true;
    } catch (Exception e) {
      listener.getLogger().println("Failed to apply patch due to:");
      e.printStackTrace(listener.getLogger());
      rbParam.setPatchFailed(true);
      throw new AbortException("Failed to apply patch");
    }
  }

  @Override
  public DescriptorImpl getDescriptor() {
    return (DescriptorImpl)super.getDescriptor();
  }

  @Extension
  public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

    public DescriptorImpl() {
      load();
    }

    @Override
    public String getDisplayName() {
      return "Apply patch";
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
      return true;
    }

  }

}
