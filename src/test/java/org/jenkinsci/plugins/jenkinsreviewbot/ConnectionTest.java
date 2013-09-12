package org.jenkinsci.plugins.jenkinsreviewbot;

import org.junit.Ignore;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

/**
 * User: ymeymann
 * Date: 9/13/13 12:52 AM
 */
@Ignore
public class ConnectionTest {

  ReviewboardConnection con =
      new ReviewboardConnection(System.getProperty("reviewboard.url"),
          System.getProperty("reviewboard.user"),
          System.getProperty("reviewboard.pwd"));

  @Test
  public void testDiff() throws Exception {
    String diff = con.getDiffAsString("https://reviewboard.eng.vmware.com/r/475848/");
    assertNotNull(diff);
  }

  @Test
  public void testPending() throws Exception {
    int count = con.getPendingReviews(1).size();
  }

  @Test
  public void testBranch() throws Exception {
    String branch = con.getBranch("https://reviewboard.eng.vmware.com/r/514656/");
    assertEquals("master", branch);
  }
}
