package org.jenkinsci.plugins.jenkinsreviewbot;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

/**
 * Process XML input streams and map to classes.
 */
public class ReviewboardXmlProcessor {
  /**
   * Process input stream, parsing the XML and mapping to provided class.
   */
  public static <T> T process(InputStream res, Class<T> clazz)
  {
    // List of XML classes
    final Class<?>[] classes = new Class[] {
      ReviewRequest.class,
      ReviewsResponse.class,
      ReviewsRequests.class,
      ReviewItem.class,
      Response.class,
      Items.class,
      Item.class,
      Links.class,
      Repository.class,
      User.class,
      Link.class
    };

    try
    {
      XStream xstream = new XStream();

      // Process the expected class, and allow the full set of XML classes to be loaded
      xstream.processAnnotations(clazz);
      xstream.setClassLoader(clazz.getClassLoader());
      xstream.allowTypes(classes);

      // Not all elements are defined in the XML classes, so allow unknown elements
      xstream.ignoreUnknownElements();

      return clazz.cast(xstream.fromXML(res));
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
  }

  /* ------------------ Utility classes ------------------ */
  @XStreamAlias("rsp")
  public class ReviewRequest {
    @XStreamAlias("review_request")
    ReviewItem request;
  }

  @XStreamAlias("rsp")
  public class ReviewsResponse {
    @XStreamAlias("review_requests")
    ReviewsRequests requests;
    @XStreamAlias("total_results")
    int total;
    String stat;
  }

  public class ReviewsRequests {
    List<ReviewItem> array;
  }

  @XStreamAlias("item")
  public class ReviewItem implements Comparable<ReviewItem> {
    @XStreamConverter(MyDateAdapter.class)
    @XStreamAlias("last_updated")
    Date lastUpdated;
    String branch;
    long id;
    Links links;

    public int compareTo(ReviewItem o) {
      try {
        return lastUpdated.compareTo(o.lastUpdated);
      } catch (Exception e) {
        return -1;
      }
    }
  }

  @XStreamAlias("rsp")
  public class Response {
    @XStreamAlias("total_results")
    int count;
    Items diffs;
    Items reviews;
    Items repositories;
    Links links;
  }

  public class Items {
    List<Item> array;
  }

  @XStreamAlias("item")
  public class Item {
    @XStreamConverter(MyDateAdapter.class)
    Date timestamp;
    Links links;
    // for repositories
    int id;
    String name;
    String tool;
    String path;
  }

  public class Links {
    User user;
    Repository repository;
    User submitter;
    Link next;
  }

  public class Repository {
    String title;
  }

  public class User {
    String title;
  }

  public class Link {
    String href;
  }

  public static class MyDateAdapter implements Converter {
    private static final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");

    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
      // Not required
    }

    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
      try {
        return formatter.parse(reader.getValue());
      } catch (ParseException e) {
        throw new RuntimeException(e);
       }
     }

    public boolean canConvert(Class type) {
      return type.equals(Date.class);
    }
  }
}