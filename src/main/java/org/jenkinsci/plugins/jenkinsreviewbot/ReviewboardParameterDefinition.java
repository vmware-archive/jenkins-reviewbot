package org.jenkinsci.plugins.jenkinsreviewbot;

import hudson.Extension;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.lang.reflect.Field;

/**
 * User: ymeymann
 * Date: 6/2/13 5:21 PM
 */
public class ReviewboardParameterDefinition extends StringParameterDefinition {

  @DataBoundConstructor
  public ReviewboardParameterDefinition() {
    super("review.url", "");
  }

  @Override
  public String getDescription() {
    return Messages.ReviewboardParameterDefinition_Description();
  }

  @Override
  public ReviewboardParameterValue createValue(StaplerRequest req, JSONObject jo) {
    return wrap((StringParameterValue) super.createValue(req, jo));
  }

  private ReviewboardParameterValue wrap(StringParameterValue rhs) {
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

  @Extension
  public static class DescriptorImpl extends StringParameterDefinition.DescriptorImpl {
    @Override
    public String getDisplayName() {
      return Messages.ReviewboardParameterDefinition_DisplayName();
    }
  }

}
