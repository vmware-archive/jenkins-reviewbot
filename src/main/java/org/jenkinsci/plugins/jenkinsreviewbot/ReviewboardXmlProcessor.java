package org.jenkinsci.plugins.jenkinsreviewbot;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Process XML input streams and map to classes.
 */
public class ReviewboardXmlProcessor {

  /**
   * Process input stream, parsing the XML and mapping to provided class.
   */
  public static <T> T process(InputStream res, Class<T> clazz)
  {
    try
    {
      JAXBContext jaxbContext = JAXBContext.newInstance(clazz);
      Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
      InputStreamReader reader = new InputStreamReader(res);
      return clazz.cast(unmarshaller.unmarshal(reader));
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
  }

  /* ------------------ Utility classes ------------------ */
  @XmlRootElement(name = "rsp")
  public static class ReviewRequest {
    @XmlElement(name = "review_request")
    ReviewItem request;
  }

  @XmlRootElement(name = "rsp")
  public static class ReviewsResponse {
    @XmlElement(name = "review_requests")
    ReviewsRequests requests;
    @XmlElement(name = "total_results")
    int total;
    @XmlElement
    String stat;
  }

  public static class ReviewsRequests {
    @XmlElementWrapper
    @XmlElement(name = "item")
    List<ReviewItem> array;
  }

  public static class ReviewItem implements Comparable<ReviewItem> {
    @XmlJavaTypeAdapter(MyDateAdapter.class)
    @XmlElement(name = "last_updated")
    Date lastUpdated;
    @XmlElement
    String branch;
    @XmlElement
    long id;
    @XmlElement
    Links links;

    public int compareTo(ReviewItem o) {
      try {
        return lastUpdated.compareTo(o.lastUpdated);
      } catch (Exception e) {
        return -1;
      }
    }
  }

  @XmlRootElement(name = "rsp")
  public static class Response {
    @XmlElement(name = "total_results")
    int count;
    @XmlElement
    Items diffs;
    @XmlElement
    Items reviews;
    @XmlElement
    Items repositories;
    @XmlElement
    Links links;
  }

  public static class Items {
    @XmlElementWrapper
    @XmlElement(name = "item")
    List<Item> array;
  }

  public static class Item {
    @XmlJavaTypeAdapter(MyDateAdapter.class)
    @XmlElement
    Date timestamp;
    @XmlElement
    Links links;
    // for repositories
    @XmlElement
    int id;
    @XmlElement
    String name;
    @XmlElement
    String tool;
    @XmlElement
    String path;
  }

  public static class Links {
    @XmlElement
    User user;
    @XmlElement
    Repository repository;
    @XmlElement
    User submitter;
    @XmlElement
    Link next;
  }

  public static class Repository {
    @XmlElement
    String title;
  }

  public static class User {
    @XmlElement
    String title;
  }

  public static class Link {
    @XmlElement
    String href;
  }

  public static class MyDateAdapter extends XmlAdapter<String, Date> {
    private static final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public Date unmarshal(String v) throws Exception {
      try {
        return javax.xml.bind.DatatypeConverter.parseDateTime(v).getTime();
      } catch (IllegalArgumentException iae) { // to support Reviewboard version 1.6
        try {
          return formatter.parse(v);
        } catch (ParseException e) {
          throw new RuntimeException(e);
        }
      }
    }

    @Override
    public String marshal(Date v) throws Exception {
      Calendar c = GregorianCalendar.getInstance();
      c.setTime(v);
      return javax.xml.bind.DatatypeConverter.printDateTime(c);
    }

  }
}