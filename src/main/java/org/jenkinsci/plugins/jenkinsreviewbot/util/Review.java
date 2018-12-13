package org.jenkinsci.plugins.jenkinsreviewbot.util;

import org.jenkinsci.plugins.jenkinsreviewbot.ReviewboardConnection;
import org.jenkinsci.plugins.jenkinsreviewbot.ReviewboardOps;

import java.io.Serializable;

import java.util.Date;

/**
 * User: ymeymann
 * Date: 10/2/2014 7:00 PM
 */
public class Review {
  private final String url;
  private final Date lastUpdate;
  private final ReviewboardOps.ReviewItem input;

  public Review(String url, Date lastUpdate, ReviewboardOps.ReviewItem input) {
    this.url = url;
    this.lastUpdate = lastUpdate;
    this.input = input;
  }

  public String getUrl() {
    return url;
  }

  public Date getLastUpdate() {
    return lastUpdate;
  }

  public ReviewboardOps.ReviewItem getInput() {
    return input;
  }

  public Slim trim() {
    return new Slim(url, lastUpdate);
  }

  public static class Slim implements Serializable {
    private final String url;
    private final Date lastUpdate;

    public Slim(String url, Date lastUpdate) {
      this.url = url;
      this.lastUpdate = lastUpdate;
    }

    public String getUrl() {
      return url;
    }

    public Date getLastUpdate() {
      return lastUpdate;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Slim slim = (Slim) o;

      if (!url.equals(slim.url)) return false;
      if (lastUpdate != null ? !lastUpdate.equals(slim.lastUpdate) : slim.lastUpdate != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = url.hashCode();
      result = 31 * result + (lastUpdate != null ? lastUpdate.hashCode() : 0);
      return result;
    }
  }

}
