package org.jenkinsci.plugins.jenkinsreviewbot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * User: ymeymann
 * Date: 3/8/14 7:57 PM
 */
public class UtilTest {
  @Test
  public void testHostParsing() throws Exception {
    ReviewboardConnection.Host h ;

    h = new ReviewboardConnection("http://www.google.com", "", "").getHost();
    assertEquals("www.google.com", h.host);
    assertEquals(80, h.port);

    h = new ReviewboardConnection("https://my.reviewboard.com", "", "").getHost();
    assertEquals("my.reviewboard.com", h.host);
    assertEquals(443, h.port);

    h = new ReviewboardConnection("http://foo.com:8800", "", "").getHost();
    assertEquals("foo.com", h.host);
    assertEquals(8800, h.port);

    h = new ReviewboardConnection("bar.com", "", "").getHost();
    assertEquals("bar.com", h.host);
    assertEquals(-1, h.port);

  }


}
