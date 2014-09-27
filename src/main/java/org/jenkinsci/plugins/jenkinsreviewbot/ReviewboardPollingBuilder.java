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
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.*;

/**
 * User: ymeymann
 * Date: 9/3/13 1:12 AM
 */
public class ReviewboardPollingBuilder extends Builder {

  private final String reviewbotJobName;
  private final String checkBackPeriod;
  private final int reviewbotRepoId;
  private boolean restrictByUser = true;
  private Set<String> processedReviews = new HashSet<String>();
  private final boolean disableAdvanceNotice;

  @DataBoundConstructor
  public ReviewboardPollingBuilder(String reviewbotJobName, String checkBackPeriod,
                                   String reviewbotRepoId, boolean restrictByUser, boolean disableAdvanceNotice) {
    this.reviewbotRepoId = reviewbotRepoId == null || reviewbotRepoId.isEmpty() ? -1 : Integer.parseInt(reviewbotRepoId);
    this.restrictByUser = restrictByUser;
    this.reviewbotJobName = reviewbotJobName;
    this.checkBackPeriod = checkBackPeriod;
    this.disableAdvanceNotice = disableAdvanceNotice;
  }

  public String getReviewbotJobName() {
    return reviewbotJobName;
  }

  public String getCheckBackPeriod() {
    return checkBackPeriod;
  }

  public int getReviewbotRepoId() { return reviewbotRepoId; }

  public boolean getRestrictByUser() { return restrictByUser; }

  public boolean getDisableAdvanceNotice() { return disableAdvanceNotice; }

  public String getJenkinsUser() { return ReviewboardNotifier.DESCRIPTOR.getReviewboardUsername(); }

  @Override
  public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
    listener.getLogger().println("Looking for reviews that need building...");
    long period = checkBackPeriod != null && !checkBackPeriod.isEmpty() ? Long.parseLong(checkBackPeriod) : 1L;
    listener.getLogger().println("Going to check reviews updated during last " + period + " hour(s): ");
    ReviewboardDescriptor d = ReviewboardNotifier.DESCRIPTOR;
    ReviewboardConnection con = new ReviewboardConnection(d.getReviewboardURL(),
                                                          d.getReviewboardUsername(), d.getReviewboardPassword());
    try {
      listener.getLogger().println("Query: " + con.getPendingReviewsUrl(restrictByUser, reviewbotRepoId));
      Collection<String> reviews = con.getPendingReviews(period, restrictByUser, reviewbotRepoId);
      listener.getLogger().println("Got " + reviews.size() + " reviews");
      Set<String> unprocessedReviews = new HashSet<String>(reviews);
      unprocessedReviews.removeAll(processedReviews);
      listener.getLogger().println("After removing previously processed, left with " + unprocessedReviews.size() + " reviews");
      if (unprocessedReviews.isEmpty()) return true;
      processedReviews = new HashSet<String>(reviews);
      Cause cause = new Cause.UpstreamCause((Run<?,?>)build); //TODO not sure what should be put here
      listener.getLogger().println("Setting cause to this build");
      Jenkins jenkins = Jenkins.getInstance();
      AbstractProject project = jenkins.getItem(reviewbotJobName, jenkins, AbstractProject.class);
      if (project == null) {
        listener.getLogger().println("ERROR: Job named " + reviewbotJobName + " not found");
        return false;
      }
      listener.getLogger().println("Found job " + reviewbotJobName);
      for (String review : unprocessedReviews) {
        listener.getLogger().println(review);
        if (!disableAdvanceNotice) con.postComment(review, Messages.ReviewboardPollingBuilder_Notice(), false);
        project.scheduleBuild2(project.getQuietPeriod(),
            cause,
            new ParametersAction(new ReviewboardParameterValue("review.url", review)));
      }
      return true;
    } catch (Exception e) {
      e.printStackTrace(listener.getLogger());
      return false;
    } finally {
      if (con != null) con.close();
    }
  }

  @Override
  public DescriptorImpl getDescriptor() {
    return (DescriptorImpl) super.getDescriptor();
  }

  @Extension
  public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

    private Map<String, Integer> repositories = Collections.emptyMap();

    public DescriptorImpl() {
      load();
    }

    private void loadRepositories() {
      ReviewboardDescriptor d = ReviewboardNotifier.DESCRIPTOR;
      ReviewboardConnection con = new ReviewboardConnection(d.getReviewboardURL(), d.getReviewboardUsername(), d.getReviewboardPassword());
      try {
        repositories = con.getRepositories();
      } catch (Exception e) {
        // TODO how do we properly log this?
        e.printStackTrace();
      } finally {
        if (con != null) con.close();
      }
    }

    @Override
    public String getDisplayName() {
      return Messages.ReviewboardBuilder_DisplayName();
    }

    @Override
    public boolean isApplicable(Class type) {
      return true;
    }

    public ListBoxModel doFillReviewbotJobNameItems() {
      ListBoxModel items = new ListBoxModel();
      for (AbstractProject project: Jenkins.getInstance().getAllItems(AbstractProject.class)) {
        items.add(project.getName());
      }
      return items;
    }

    public ListBoxModel doFillReviewbotRepoIdItems() {
      // populate list of repositories with (name, id)
      ListBoxModel items = new ListBoxModel();
      // select option to allow polling requests for all repositories
      items.add("-- any --", "-1");
      //if no repositories - try to load them again
      if (repositories.isEmpty()) loadRepositories();
      // select options to filter by repository id.
      for (Map.Entry<String, Integer> e: repositories.entrySet()) {
        items.add(e.getKey(), e.getValue().toString());
      }
      return items;
    }

    @Initializer(before= InitMilestone.PLUGINS_STARTED)
    public static void addAliases() {
      Items.XSTREAM2.addCompatibilityAlias(
          "org.jenkinsci.plugins.jenkinsreviewbot.RevieboardPollingBuilder", ReviewboardPollingBuilder.class);
    }
  }

}
