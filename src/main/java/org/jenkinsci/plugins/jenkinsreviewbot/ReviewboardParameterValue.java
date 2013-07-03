package org.jenkinsci.plugins.jenkinsreviewbot;

import com.cloudbees.diff.ContextualPatch;
import com.cloudbees.diff.PatchException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FileParameterValue;
import hudson.model.ParameterValue;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildWrapper;
import hudson.util.VariableResolver;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: ymeymann
 * Date: 6/2/13 7:40 PM
 */
public class ReviewboardParameterValue extends ParameterValue {

  private final String url;
  private boolean patchFailed = false;

  @DataBoundConstructor
  public ReviewboardParameterValue(String name, String value) {
    super("review.url");
    url = buildReviewUrl(value);
  }

  public String getLocation() {
    return url;
  }

  @Override
  public String toString() {
    return "review.url='" + url + "'";
  }

  private static final String LOCATION = "patch.diff";
  private static final Pattern digitsPattern = Pattern.compile("\\d+");

//  //TODO replace with a configurable parameter
//  private static final String rb_url = System.getProperty("REVIEWBOARD_URL", "https://reviewboard.eng.vmware.com/");

  @Override
  public BuildWrapper createBuildWrapper(AbstractBuild<?,?> build) {
    return new ReviewboardBuildWrapper() ;
  }

  private File getLocationUnderBuild(AbstractBuild build) {
    return new File(build.getRootDir(), "fileParameters/" + LOCATION);
  }

  public boolean isPatchFailed() {
    return patchFailed;
  }

  private void setPatchFailed(boolean patchFailed) {
    this.patchFailed = patchFailed;
  }

  // copied from PatchParameterValue
  @Override
  public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
    // no environment variable
  }

  // copied from PatchParameterValue
  @Override
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

  private FileItem getDiffFile() {
    File patchFile = null;
    try {
      patchFile = new File(LOCATION);
      String diff = ReviewboardNotifier.DESCRIPTOR.getDiffAsString(url);
      savePatch(patchFile, diff);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return new FileParameterValue.FileItemImpl(patchFile);
  }

  private void savePatch(File patchFile, String diff) throws IOException {
    if (!patchFile.exists()) patchFile.createNewFile();
    Writer w = new BufferedWriter(new FileWriter(patchFile));
    w.write(diff);
    w.close();
  }

  private String buildReviewUrl(String value) {
    StringBuilder sb = new StringBuilder();
    if (!value.startsWith("http")) {
      Matcher m = digitsPattern.matcher(value);
      String number = m.find() ? m.group() : "0";
      sb.append(ReviewboardNotifier.DESCRIPTOR.getReviewboardURL());
      sb.append("r/").append(number).append('/');
    } else {
      sb.append(value);
      if (sb.charAt(sb.length() - 1) != '/' ) sb.append('/');
    }
    return sb.toString();
  }

  private void applyPatch(BuildListener listener, FilePath patch) throws IOException, InterruptedException {
    listener.getLogger().println("Applying "+ ReviewboardNote.encodeTo("the diff"));
    List<ContextualPatch.PatchReport> reports = patch.act(new ApplyTask());
    for (ContextualPatch.PatchReport r : reports) {
      if (r.getFailure()!=null) {
        listener.getLogger().println("Failed to patch "+r.getFile()+" due to "+r.getFailure().toString());
        setPatchFailed(true);
        throw new IOException("Failed to patch "+r.getFile(), r.getFailure());
      }
    }
  }

//  copied from FileParameterValue
//  @Override
//  public void buildEnvVars(AbstractBuild<?,?> build, EnvVars env) {
//    env.put(name,url);
//  }

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
        patch.copyFrom(getDiffFile());
        patch.copyTo(new FilePath(getLocationUnderBuild(build)));
        if (patch.exists()) {
          applyPatch(listener, patch);
        }
      }
      return new BuildWrapper.Environment() {};
    }
  }

  static class ApplyTask implements FilePath.FileCallable<List<ContextualPatch.PatchReport>> {
    private static final long serialVersionUID = 1L;

    public List<ContextualPatch.PatchReport> invoke(File diff, VirtualChannel channel) throws IOException, InterruptedException {
      ContextualPatch patch = ContextualPatch.create(diff,diff.getParentFile());
      try {
        List<ContextualPatch.PatchReport> reports = patch.patch(false);
        return reports;
      } catch (PatchException e) {
        throw new IOException("Failed to apply the patch: "+diff,e);
      }
    }
  }

}
