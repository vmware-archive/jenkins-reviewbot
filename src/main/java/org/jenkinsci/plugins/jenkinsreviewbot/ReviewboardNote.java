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
import hudson.MarkupText;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;
import hudson.model.AbstractBuild;
import hudson.model.ParametersAction;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: ymeymann
 * Date: 6/5/13 4:48 PM
 */
public class ReviewboardNote extends ConsoleNote {
  private final int length;

  public ReviewboardNote(int length) {
    this.length = length;
  }

  @Override
  public ConsoleAnnotator annotate(Object context, MarkupText text, int charPos) {
    if (context instanceof AbstractBuild) {
      AbstractBuild<?,?> b = (AbstractBuild) context;
      ParametersAction pa = b.getAction(ParametersAction.class);
      if (pa!=null) {
        ReviewboardParameterValue p = (ReviewboardParameterValue) pa.getParameter("review.url");
        if (p!=null) {
          text.addHyperlink(charPos,charPos+length,p.getLocation());
        }
      }
    }
    return null;
  }

  public static String encodeTo(String text) {
    try {
      return new ReviewboardNote(text.length()).encode()+text;
    } catch (IOException e) {
      // impossible, but don't make this a fatal problem
      LOGGER.log(Level.WARNING, "Failed to serialize "+ReviewboardNote.class,e);
      return text;
    }
  }

  @Extension
  public static final class DescriptorImpl extends ConsoleAnnotationDescriptor {
    public String getDisplayName() {
      return "Link to patch.diff";
    }
  }

  private static final Logger LOGGER = Logger.getLogger(ReviewboardNote.class.getName());
}
