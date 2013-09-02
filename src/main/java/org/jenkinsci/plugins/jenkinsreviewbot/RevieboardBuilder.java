package org.jenkinsci.plugins.jenkinsreviewbot;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collection;

/**
 * User: ymeymann
 * Date: 9/3/13 1:12 AM
 */
public class RevieboardBuilder extends Builder {

  private final String jobName;

  @DataBoundConstructor
  public RevieboardBuilder(String jobName) {
    this.jobName = jobName;
  }

  public String getJobName() {
    return jobName;
  }

  @Override
  public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
    listener.getLogger().println("Looking for reviews that need building...");
    try {
      Collection<String> reviews = ReviewboardNotifier.DESCRIPTOR.getConnection().getPendingReviews();
      listener.getLogger().println("Got " + reviews.size() + " reviews");
      for (String review : reviews) {
        listener.getLogger().println(review);
        //TODO spawn the job
      }
      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  @Override
  public DescriptorImpl getDescriptor() {
    // see Descriptor javadoc for more about what a descriptor is.
    return (DescriptorImpl)super.getDescriptor();
  }

  @Extension
  public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
    public DescriptorImpl() {
      load();
    }
    @Override
    public String getDisplayName() {
      return Messages.ReviewboardBuilder_DisplayName();
    }
    @Override
    public boolean isApplicable(Class type) {
      return true;
    }
  }

}
