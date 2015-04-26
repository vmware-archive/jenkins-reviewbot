package org.jenkinsci.plugins.jenkinsreviewbot;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.SimpleHttpConnectionManager;
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
    ReviewboardOps.getInstance().ensureAuthentication(con);
  }

  @Test(expected=IOException.class)
  public void testFailConnection() throws Exception {
    SimpleHttpConnectionManager simple = new SimpleHttpConnectionManager();
    HttpClient http = new HttpClient(simple);
    ReviewboardConnection bad =
        new ReviewboardConnection(System.getProperty("reviewboard.url", "https://reviewboard.eng.vmware.com/"),
            System.getProperty("reviewboard.user"), "foobar");
    try {
      ReviewboardOps.ensureAuthentication(bad, http);
    } finally {
      simple.shutdown();
    }

  }

  @Test
  public void testDiff() throws Exception {
    ReviewboardOps.DiffHandle diff = ReviewboardOps.getInstance().getDiff(con, "https://reviewboard.eng.vmware.com/r/475848/");
    try { assertNotNull(diff.getString()); } finally { diff.close(); }
  }

  @Test
  public void testPending() throws Exception {
    int count = ReviewboardOps.getInstance().getPendingReviews(con, 1, true, -1).size();
    System.out.println(count);
  }

  @Test
  public void testBranchAndRepo1() throws Exception {
    Map<String, String> m = ReviewboardOps.getInstance().getProperties(con, "https://reviewboard.eng.vmware.com/r/514656/");
    assertEquals("master", m.get("REVIEW_BRANCH"));
    assertEquals("act", m.get("REVIEW_REPOSITORY"));
  }

  @Test
  public void testBranchAndRepo2() throws Exception {
    Map<String, String> m = ReviewboardOps.getInstance().getProperties(con, "https://reviewboard.eng.vmware.com/r/643087/");
    assertEquals("origin/master", m.get("REVIEW_BRANCH"));
    assertEquals("itfm-cloud", m.get("REVIEW_REPOSITORY"));
  }

  @Test
  public void testSubmitter() throws Exception {
    Map<String, String> m = ReviewboardOps.getInstance().getProperties(con, "https://reviewboard.eng.vmware.com/r/475848/");
    assertEquals("ymeymann", m.get("REVIEW_USER"));
  }
}
