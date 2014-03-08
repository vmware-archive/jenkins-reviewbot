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

    h = ReviewboardConnection.extractHostAndPort("http://www.google.com");
    assertEquals("www.google.com", h.host);
    assertEquals(80, h.port);

    h = ReviewboardConnection.extractHostAndPort("https://my.reviewboard.com");
    assertEquals("my.reviewboard.com", h.host);
    assertEquals(443, h.port);

    h = ReviewboardConnection.extractHostAndPort("http://foo.com:8800");
    assertEquals("foo.com", h.host);
    assertEquals(8800, h.port);

    h = ReviewboardConnection.extractHostAndPort("bar.com");
    assertEquals("bar.com", h.host);
    assertEquals(-1, h.port);

  }


}
