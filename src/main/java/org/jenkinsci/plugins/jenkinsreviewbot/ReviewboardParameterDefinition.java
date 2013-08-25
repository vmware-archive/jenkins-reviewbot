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
import hudson.cli.CLICommand;
import hudson.model.ParameterValue;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
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

  public ParameterValue createValue(String value) {
    return wrap((StringParameterValue) super.createValue(value));
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
