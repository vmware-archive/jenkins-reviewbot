package org.jenkinsci.plugins.jenkinsreviewbot;

import org.junit.Test;

import java.io.InputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;

import static org.junit.Assert.assertEquals;

/**
 * Test XML processing,
 */
public class XmlTest {
    private static final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");


    /**
     * Test the parsing of repository data from Reviewboard.
     */
    @Test
    public void testRepositories() throws Exception {
        ReviewboardXmlProcessor.Response res = parseFile("repositories.xml",
            ReviewboardXmlProcessor.Response.class);

        // 2 repositories expected
        assertEquals(2, res.count);

        // First repository
        ReviewboardXmlProcessor.Item repo1 = res.repositories.array.get(0);
        assertEquals(1, repo1.id);
        assertEquals("Repository 1", repo1.name);
        assertEquals("Git", repo1.tool);
        assertEquals("https://github.com/repos/repo1", repo1.path);

        // Second repository
        ReviewboardXmlProcessor.Item repo2 = res.repositories.array.get(1);
        assertEquals(2, repo2.id);
        assertEquals("Repository 2", repo2.name);
        assertEquals("Subversion", repo2.tool);
        assertEquals("https://github.com/repos/repo2", repo2.path);
    }

    /**
     * Test the parsing of pending reviews data from Reviewboard.
     */
    @Test
    public void testPendingReviews() throws Exception {
        ReviewboardXmlProcessor.ReviewsResponse res = parseFile("pending-reviews.xml",
            ReviewboardXmlProcessor.ReviewsResponse.class);

        // 2 reviews expected
        assertEquals(2, res.total);

        // First review
        ReviewboardXmlProcessor.ReviewItem req1 = res.requests.array.get(0);
        assertEquals(94, req1.id);
        assertEquals("2021-12-13 12:50:26", formatter.format(req1.lastUpdated));

        // Second review
        ReviewboardXmlProcessor.ReviewItem req2 = res.requests.array.get(1);
        assertEquals(35, req2.id);
        assertEquals("2020-06-02 09:52:11", formatter.format(req2.lastUpdated));
    }

    /**
     * Test the parsing of diffs data from Reviewboard.
     */
    @Test
    public void testDiffs() throws Exception {
        ReviewboardXmlProcessor.Response res = parseFile("diffs.xml",
            ReviewboardXmlProcessor.Response.class);

        // 6 diffs expected
        assertEquals(6, res.count);
    }

    /**
     * Test the parsing of comments data from Reviewboard.
     */
    @Test
    public void testComments() throws Exception {
        ReviewboardXmlProcessor.Response res = parseFile("comments.xml",
            ReviewboardXmlProcessor.Response.class);

        // 3 reviews expected
        assertEquals(3, res.count);

        // First review
        ReviewboardXmlProcessor.Item review1 = res.reviews.array.get(0);
        assertEquals("jenkins", review1.links.user.title);
        assertEquals("2021-08-13 16:39:12", formatter.format(review1.timestamp));

        // Second review
        ReviewboardXmlProcessor.Item review2 = res.reviews.array.get(1);
        assertEquals("sally", review2.links.user.title);
        assertEquals("2021-08-16 08:30:26", formatter.format(review2.timestamp));

        // Third review
        ReviewboardXmlProcessor.Item review3 = res.reviews.array.get(2);
        assertEquals("rupert", review3.links.user.title);
        assertEquals("2021-08-16 09:15:07", formatter.format(review3.timestamp));
    }

    /**
     * Load a resource file and parse using the ReviewboardXmlProcessor.
     */
    private <T> T parseFile(String filename, Class<T> type) {
        try {
            InputStream input = getClass().getResourceAsStream(filename);
            return ReviewboardXmlProcessor.process(input, type);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}