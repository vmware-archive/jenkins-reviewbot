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
