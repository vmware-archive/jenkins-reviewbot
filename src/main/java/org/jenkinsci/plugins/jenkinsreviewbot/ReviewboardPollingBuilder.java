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

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.plugins.jenkinsreviewbot.util.Review;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.CheckForNull;

import javax.annotation.Nullable;
import java.util.*;

/**
 * User: ymeymann
 * Date: 9/3/13 1:12 AM
 */
public class ReviewboardPollingBuilder extends Builder implements SimpleBuildStep {

  @CheckForNull
  private String reviewbotJobName;
  private String checkBackPeriod = "1";
  private int reviewbotRepoId = -1;
  private boolean restrictByUser = true;
  private Set<String> processedReviews = new HashSet<String>();
  private boolean disableAdvanceNotice = false;
  private Map<String, Date> processedReviewDates = new HashMap<String, Date>();

  @DataBoundConstructor
  public ReviewboardPollingBuilder(String reviewbotJobName) {
    this.reviewbotJobName = reviewbotJobName;
  }

  @DataBoundSetter
  public void setReviewbotJobName(String reviewbotJobName) {
    this.reviewbotJobName = reviewbotJobName;
  }

  @DataBoundSetter
  public void setCheckBackPeriod(String checkBackPeriod) {
    this.checkBackPeriod = checkBackPeriod;
  }

  @DataBoundSetter
  public void setReviewbotRepoId(int reviewbotRepoId) {
    this.reviewbotRepoId = reviewbotRepoId;
  }

  @DataBoundSetter
  public void setRestrictByUser(boolean restrictByUser) {
    this.restrictByUser = restrictByUser;
  }

  @DataBoundSetter
  public void setDisableAdvanceNotice(boolean disableAdvanceNotice) {
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

  private Set<Review.Slim> getRichProcessedReviews() {
    if (processedReviews == null) return Collections.emptySet();
    HashSet<Review.Slim> res = new HashSet<Review.Slim>();
    for (String r: processedReviews) {
      res.add(new Review.Slim(r, processedReviewDates == null ? null : processedReviewDates.get(r)));
    }
    return res;
  }

  private void updateProcessed(Collection<Review.Slim> reviews) {
    processedReviews = new HashSet<String>(Collections2.transform(reviews, new Function<Review.Slim, String>() {
      public String apply(@Nullable Review.Slim input) { return input.getUrl(); }
    }));
    if (processedReviewDates == null) { processedReviewDates = new HashMap<String, Date>(); }
    else { processedReviewDates.clear(); }
    for (Review.Slim r: reviews) {
      if (r.getLastUpdate() != null) processedReviewDates.put(r.getUrl(), r.getLastUpdate());
    }
  }

  @Override
  public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) {
    listener.getLogger().println("Looking for reviews that need building...");
    long period = checkBackPeriod != null && !checkBackPeriod.isEmpty() ? Long.parseLong(checkBackPeriod) : 1L;
    listener.getLogger().println("Going to check reviews updated during last " + period + " hour(s): ");
    ReviewboardConnection con = ReviewboardConnection.fromConfiguration();
    try {
      listener.getLogger().println("Query: " + con.getPendingReviewsUrl(restrictByUser, reviewbotRepoId));
      Collection<Review.Slim> reviews = ReviewboardOps.getInstance().getPendingReviews(con, period, restrictByUser, reviewbotRepoId);
      listener.getLogger().println("Got " + reviews.size() + " reviews");
      Set<Review.Slim> unprocessedReviews = new HashSet<Review.Slim>(reviews);
      if (processedReviews != null) { //apparently, it is null when de-serialized from previous version of plugin... DUH!
        Set<Review.Slim> richProcessed = getRichProcessedReviews();
        unprocessedReviews.removeAll(richProcessed);
      }
      listener.getLogger().println("After removing previously processed, left with " + unprocessedReviews.size() + " reviews");
      updateProcessed(reviews);
      if (unprocessedReviews.isEmpty()) return;
      Jenkins jenkins = Jenkins.getInstance();
      Job job = jenkins.getItem(reviewbotJobName, jenkins, Job.class);
      if (job == null) {
        throw new AbortException("ERROR: Job named " + reviewbotJobName + " not found");
      }
      listener.getLogger().println("Found job " + reviewbotJobName);
      for (Review.Slim review : unprocessedReviews) {
        listener.getLogger().println(review.getUrl());
        if (!disableAdvanceNotice) ReviewboardOps.getInstance().postComment(con, review.getUrl(), Messages.ReviewboardPollingBuilder_Notice(), false, false);
        ParameterizedJobMixIn.scheduleBuild2(job, -1, new ParametersAction(new ReviewboardParameterValue(review.getUrl())));
      }
    } catch (Exception e) {
      e.printStackTrace(listener.getLogger());
    }
  }

  @Override
  public DescriptorImpl getDescriptor() {
    return (DescriptorImpl) super.getDescriptor();
  }

  @Extension @Symbol("checkForReviews")
  public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

    private Map<String, Integer> repositories = Collections.emptyMap();

    public DescriptorImpl() {
      load();
    }

    private Map<String, Integer> loadRepositories() {
      try {
        return ReviewboardOps.getInstance().getRepositories();
      } catch (Exception e) {
        // TODO how do we properly log this?
        e.printStackTrace();
        return Collections.emptyMap();
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
      for (Job project: Jenkins.getInstance().getAllItems(Job.class)) {
        items.add(project.getName());
      }
      return items;
    }

    public ListBoxModel doFillReviewbotRepoIdItems() {
      // populate list of repositories with (name, id)
      ListBoxModel items = new ListBoxModel();
      // select option to allow polling requests for all repositories
      items.add("-- any --", "-1");
      Map<String, Integer> localRepositories = this.repositories;
      boolean disableCache = ReviewboardNotifier.DESCRIPTOR.getDisableRepoCache();
      //if no repositories or caching disabled - try to load them again
      if (localRepositories.isEmpty() || disableCache) {
        localRepositories = loadRepositories();
      }
      //cache repository list
      repositories = localRepositories;
      // select options to filter by repository id.
      for (Map.Entry<String, Integer> e: localRepositories.entrySet()) {
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
