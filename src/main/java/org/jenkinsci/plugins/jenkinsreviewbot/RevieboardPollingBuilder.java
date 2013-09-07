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

import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collection;

/**
 * User: ymeymann
 * Date: 9/3/13 1:12 AM
 */
public class RevieboardPollingBuilder extends Builder {

  private final String reviewbotJobName;
  private final String checkBackPeriod;

  @DataBoundConstructor
  public RevieboardPollingBuilder(String reviewbotJobName, String checkBackPeriod) {
    this.reviewbotJobName = reviewbotJobName;
    this.checkBackPeriod = checkBackPeriod;
  }

  public String getReviewbotJobName() {
    return reviewbotJobName;
  }

  public String getCheckBackPeriod() {
    return checkBackPeriod;
  }

  @Override
  public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
    listener.getLogger().println("Looking for reviews that need building...");
    long period = checkBackPeriod != null && !checkBackPeriod.isEmpty() ? Long.parseLong(checkBackPeriod) : 1L;
    listener.getLogger().println("Going to check reviews updated during last " + period + " hour(s)");
    try {
      Collection<String> reviews = connection().getPendingReviews(period);
      listener.getLogger().println("Got " + reviews.size() + " reviews");
      for (String review : reviews) {
        listener.getLogger().println(review);
        Cause cause = new Cause.UpstreamCause(build); //TODO not sure what should be put here
        Project p = Jenkins.getInstance().getItem(reviewbotJobName, Jenkins.getInstance(), Project.class);
        p.scheduleBuild2(p.getQuietPeriod(),
            cause,
            new ParametersAction(new ReviewboardParameterValue("review.url", review)));
      }
      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  private static ReviewboardConnection connection() {
    return ReviewboardNotifier.DESCRIPTOR.getConnection();
  }

  @Override
  public DescriptorImpl getDescriptor() {
    return (DescriptorImpl) super.getDescriptor();
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
