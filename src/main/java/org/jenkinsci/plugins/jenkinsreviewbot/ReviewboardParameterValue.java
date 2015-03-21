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

import com.cloudbees.diff.ContextualPatch;
import com.cloudbees.diff.PatchException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildWrapper;
import hudson.util.IOException2;
import hudson.util.VariableResolver;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.*;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: ymeymann
 * Date: 6/2/13 7:40 PM
 */
public class ReviewboardParameterValue extends ParameterValue {

  private final String url;
  private boolean patchFailed = false;
  private transient volatile ReviewboardConnection connection = null;

  @DataBoundConstructor
  public ReviewboardParameterValue(String name, String value) {
    super("review.url");
    url = buildReviewUrl(value);
  }

  static ReviewboardParameterValue wrap(StringParameterValue rhs) {
    try {
      Field $value = StringParameterValue.class.getDeclaredField("value");
      $value.setAccessible(true);
      ReviewboardParameterValue v = new ReviewboardParameterValue(rhs.getName(), (String)$value.get(rhs));
      return v;
    } catch (NoSuchFieldException e) {
      throw new Error(e);
    } catch (IllegalAccessException e) {
      throw new Error(e);
    }
  }



  public String getLocation() {
    return url;
  }

  @Override
  public String toString() {
    return "review.url='" + url + "'";
  }

  static final String LOCATION = "patch.diff";

  @Override
  public BuildWrapper createBuildWrapper(AbstractBuild<?,?> build) {
    return new ReviewboardBuildWrapper() ;
  }

  private File getLocationUnderBuild(AbstractBuild<?,?> build) {
    return new File(build.getRootDir(), "fileParameters/" + LOCATION);
  }

  public boolean isPatchFailed() {
    return patchFailed;
  }

  void setPatchFailed(boolean patchFailed) {
    this.patchFailed = patchFailed;
  }

  // copied from PatchParameterValue
//  @Override
//  public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
//    // no environment variable
//  }

  // copied from PatchParameterValue
  @Override
  @SuppressWarnings("unchecked")
  public VariableResolver<String> createVariableResolver(AbstractBuild<?, ?> build) {
    return VariableResolver.NONE;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ReviewboardParameterValue that = (ReviewboardParameterValue) o;

    if (url != null ? !url.equals(that.url) : that.url != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (url != null ? url.hashCode() : 0);
    return result;
  }

//  private FileItem getDiffFile() {
//    File patchFile = null;
//    try {
//      File tempDir = new File(System.getProperty("java.io.tmpdir"));
//      patchFile = new File(tempDir, LOCATION);
//      String diff = getConnection().getDiffAsString(url);
//      savePatch(patchFile, diff);
//    } catch (IOException e) {
//      e.printStackTrace();
//    }
//    return new FileParameterValue.FileItemImpl(patchFile);
//  }

  synchronized ReviewboardConnection getConnection() {
    if (connection == null) {
      ReviewboardDescriptor d = ReviewboardNotifier.DESCRIPTOR;
      connection = new ReviewboardConnection(d.getReviewboardURL(),
          d.getReviewboardUsername(),
          d.getReviewboardPassword(),
          new MultiThreadedHttpConnectionManager());
    }
    return connection;
  }

  private void savePatch(File patchFile, String diff) throws IOException {
    if (!patchFile.exists()) patchFile.createNewFile();
    Writer w = new BufferedWriter(new FileWriter(patchFile));
    w.write(diff);
    w.close();
  }

  private String buildReviewUrl(String value) {
    //if full url is given, just make sure iit ends with /
    //but if a number is given, construct the url from number based on configured Reviewboard home URL
    if (!value.startsWith("http")) {
      return getConnection().buildReviewUrl(value);
    } else {
      StringBuilder sb = new StringBuilder(value);
      if (sb.charAt(sb.length() - 1) != '/' ) sb.append('/');
      return sb.toString();
    }
  }

  private void applyPatch(BuildListener listener, FilePath patch) throws IOException, InterruptedException {
    if (ReviewboardNotifier.DESCRIPTOR.getDisableAutoApply()) {
      listener.getLogger().println("Skipping automatic patch application");
      return;
    }

    listener.getLogger().println("Applying "+ ReviewboardNote.encodeTo("the diff"));
    try {
      patch.act(new ApplyTask());
    } catch (IOException e) {
      listener.getLogger().println("Failed to apply patch due to:");
      e.printStackTrace(listener.getLogger());
      setPatchFailed(true);
      throw e;
    }
  }

//  copied from FileParameterValue
  @Override
  public void buildEnvVars(AbstractBuild<?,?> build, EnvVars env) {
    env.put("REVIEW_URL",url);
//    String branch = "master";
    Map<String, String> props = Collections.emptyMap();
    try {
      props = getConnection().getProperties(url);
    } catch (IOException e) {
      e.printStackTrace();
    }
    env.putAll(props);
//    env.put("REVIEW_BRANCH", branch);
  }

//  copied from FileParameterValue
//  @Override
//  public VariableResolver<String> createVariableResolver(AbstractBuild<?, ?> build) {
//    return new VariableResolver<String>() {
//      public String resolve(String name) {
//        return ReviewboardParameterValue.this.name.equals(name) ? url : null;
//      }
//    };
//  }

  class ReviewboardBuildWrapper extends BuildWrapper {
    @Override
    public BuildWrapper.Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
      if (!StringUtils.isEmpty(url)) {
        FilePath patch = build.getWorkspace().child(LOCATION);
        patch.delete();
        patch.getParent().mkdirs();
        ReviewboardConnection.DiffHandle diff = getConnection().getDiff(url);
        try {
          patch.copyFrom(diff.getStream()); //getDiffFile()
          patch.copyTo(new FilePath(getLocationUnderBuild(build)));
        } finally {
          diff.close();
        }
        if (patch.exists()) {
          applyPatch(listener, patch);
        }
      }
      return new BuildWrapper.Environment() {
        @Override
        public boolean tearDown( AbstractBuild build, BuildListener listener ) throws IOException, InterruptedException {
          if (connection != null) connection.close();//consider to remove, since for multi-threaded connection this will do nothing
          return super.tearDown(build, listener);
        }
      };
    }
  }

  static class ApplyTask implements FilePath.FileCallable<Void> {
    private static final long serialVersionUID = 1L;

    public Void invoke(File diff, VirtualChannel channel) throws IOException, InterruptedException {
      ContextualPatch patch = ContextualPatch.create(diff,diff.getParentFile());
      try {
        List<ContextualPatch.PatchReport> reports = patch.patch(false);
        for (ContextualPatch.PatchReport r : reports) {
          Throwable failure = r.getFailure();
          if (failure != null)
            throw new IOException("Failed to patch " + r.getFile(), failure);
        }
      } catch (PatchException e) {
        throw new IOException2("Failed to apply the patch: "+diff,e);
      }
      return null;
    }
  }

}
