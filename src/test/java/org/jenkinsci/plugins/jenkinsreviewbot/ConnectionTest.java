package org.jenkinsci.plugins.jenkinsreviewbot;

import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

/**
 * User: ymeymann
 * Date: 9/13/13 12:52 AM
 */
@Ignore
public class ConnectionTest {

  ReviewboardConnection con =
      new ReviewboardConnection(
          System.getProperty("reviewboard.url", "https://reviewboard.eng.vmware.com/"),
          System.getProperty("reviewboard.user"),
          System.getProperty("reviewboard.pwd"));

  @Test
  public void testConnection() throws Exception {
    con.ensureAuthentication();
  }

  @Test(expected=IOException.class)
  public void testFailConnection() throws Exception {
    ReviewboardConnection bad =
        new ReviewboardConnection(System.getProperty("reviewboard.url", "https://reviewboard.eng.vmware.com/"),
            System.getProperty("reviewboard.user"), "foobar");
    bad.ensureAuthentication();
  }

  @Test
  public void testDiff() throws Exception {
    ReviewboardConnection.DiffHandle diff = con.getDiff("https://reviewboard.eng.vmware.com/r/475848/");
    try { assertNotNull(diff.getString()); } finally { diff.close(); }
  }

  @Test
  public void testPending() throws Exception {
    int count = con.getPendingReviews(1, true, -1).size();
    System.out.println(count);
  }

  @Test
  public void testBranchAndRepo1() throws Exception {
    Map<String, String> m = con.getProperties("https://reviewboard.eng.vmware.com/r/514656/");
    assertEquals("master", m.get("REVIEW_BRANCH"));
    assertEquals("act", m.get("REVIEW_REPOSITORY"));
  }

  @Test
  public void testBranchAndRepo2() throws Exception {
    Map<String, String> m = con.getProperties("https://reviewboard.eng.vmware.com/r/643087/");
    assertEquals("origin/master", m.get("REVIEW_BRANCH"));
    assertEquals("itfm-cloud", m.get("REVIEW_REPOSITORY"));
  }

}
