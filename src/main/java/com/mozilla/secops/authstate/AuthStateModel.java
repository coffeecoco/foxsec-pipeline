package com.mozilla.secops.authstate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mozilla.secops.GeoUtil;
import com.mozilla.secops.state.StateCursor;
import com.mozilla.secops.state.StateException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import org.joda.time.DateTime;

/** Manages and stores authentication state information for a given user identity. */
public class AuthStateModel {
  private String subject;
  private Map<String, ModelEntry> entries;

  /** Response to {@link AuthStateModel} GeoVelocity analysis request */
  public static class GeoVelocityResponse {
    private final Long timeDifference;
    private final Double kmDistance;
    private final Boolean maxKmPerSExceeded;

    private String previousSource;
    private String currentSource;

    /**
     * Get previous source address
     *
     * @return String, or null if not set in response
     */
    public String getPreviousSource() {
      return previousSource;
    }

    /**
     * Get current source address
     *
     * @return String, or null if not set in response
     */
    public String getCurrentSource() {
      return currentSource;
    }

    /**
     * Set previous source address
     *
     * @param previousSource Previous source address
     * @return this for chaining
     */
    public GeoVelocityResponse withPreviousSource(String previousSource) {
      this.previousSource = previousSource;
      return this;
    }

    /**
     * Set current source address
     *
     * @param currentSource Current source address
     * @return this for chaining
     */
    public GeoVelocityResponse withCurrentSource(String currentSource) {
      this.currentSource = currentSource;
      return this;
    }

    /**
     * Get difference in time in seconds
     *
     * @return Long
     */
    public Long getTimeDifference() {
      return timeDifference;
    }

    /**
     * Return true if max KM/s was exceeded
     *
     * @return Boolean
     */
    public Boolean getMaxKmPerSecondExceeded() {
      return maxKmPerSExceeded;
    }

    /**
     * Get distance between points in KM
     *
     * @return Double
     */
    public Double getKmDistance() {
      return kmDistance;
    }

    /**
     * Create new GeoVelocityResponse
     *
     * @param timeDifference Time difference in seconds
     * @param kmDistance Distance between points in KM
     */
    public GeoVelocityResponse(Long timeDifference, Double kmDistance, Boolean maxKmPerSExceeded) {
      this.timeDifference = timeDifference;
      this.kmDistance = kmDistance;
      this.maxKmPerSExceeded = maxKmPerSExceeded;
    }
  }

  /** Represents a single known source for authentication for a given user */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ModelEntry {
    private Double longitude;
    private Double latitude;
    private DateTime timestamp;
    private String userAgent;

    /**
     * Set user agent field
     *
     * @param userAgent User agent
     */
    public void setUserAgent(String userAgent) {
      this.userAgent = userAgent;
    }

    /**
     * Get user agent field
     *
     * @return User agent, or null if unset
     */
    @JsonProperty("useragent")
    public String getUserAgent() {
      return userAgent;
    }

    /**
     * Set model latitude field
     *
     * @param latitude Latitude double value
     */
    public void setLatitude(Double latitude) {
      this.latitude = latitude;
    }

    /**
     * Get model latitude field
     *
     * @return model latitude double value
     */
    @JsonProperty("latitude")
    public Double getLatitude() {
      return latitude;
    }

    /**
     * Set model longitude field
     *
     * @param longitude longitude double value
     */
    public void setLongitude(Double longitude) {
      this.longitude = longitude;
    }

    /**
     * Get model longitude field
     *
     * @return model longitude double value
     */
    @JsonProperty("longitude")
    public Double getLongitude() {
      return longitude;
    }

    /**
     * Get timestamp of entry
     *
     * @return Timestamp as DateTime
     */
    @JsonProperty("timestamp")
    public DateTime getTimestamp() {
      return timestamp;
    }

    /**
     * Set timestamp of entry
     *
     * @param ts Entry timestamp
     */
    public void setTimestamp(DateTime ts) {
      timestamp = ts;
    }
  }

  /**
   * Information used in a model update request
   *
   * <p>At a minimum, the IP address field must be set.
   *
   * <p>If the timestamp field is not set, the update will occur using the current time as the
   * timestamp.
   *
   * <p>Latitude, longitude, and user agent should be set if known, otherwise should be null.
   */
  public static class ModelEntryUpdate {
    /** IP address to update model with */
    public String ipAddress;

    /** Timestamp to associate with entry */
    public DateTime timestamp;

    /** IP address GeoIP latitude */
    public Double latitude;

    /** IP address GeoIP longitude */
    public Double longitude;

    /** An optional user agent to associate with the update */
    public String userAgent;
  }

  /**
   * Update state entry for user to indicate authentication from address
   *
   * <p>Note this function does not write the new state, set must be called to make changes
   * permanent.
   *
   * <p>This variant of the method will use the current time as the timestamp for the authentication
   * event, instead of accepting a parameter indicating the timestamp to associate with the event.
   *
   * <p>This variant of updateEntry does not support including a user agent with the update, pass
   * ModelEntryUpdate for that.
   *
   * @param ipaddr IP address to update state with
   * @param latitude IP address's latitude
   * @param longitude IP address's longitude
   * @return True if the IP address was unknown, otherwise false
   */
  public Boolean updateEntry(String ipaddr, Double latitude, Double longitude) {
    return updateEntry(ipaddr, new DateTime(), latitude, longitude);
  }

  /**
   * Update state entry for user to indicate authentication from address setting specified timestamp
   * on the entry
   *
   * <p>Note this function does not write the new state, set must be called to make changes
   * permanent.
   *
   * <p>This variant of updateEntry does not support including a user agent with the update, pass
   * ModelEntryUpdate for that.
   *
   * @param ipaddr IP address to update state with
   * @param timestamp Timestamp to associate with update
   * @param latitude IP address's latitude
   * @param longitude IP address's longitude
   * @return True if the IP address was unknown, otherwise false
   */
  public Boolean updateEntry(String ipaddr, DateTime timestamp, Double latitude, Double longitude) {
    ModelEntryUpdate m = new ModelEntryUpdate();
    m.ipAddress = ipaddr;
    m.timestamp = timestamp;
    m.latitude = latitude;
    m.longitude = longitude;
    return updateEntry(m);
  }

  /**
   * Update state entry for user to indicate authentication from address setting specified timestamp
   * on the entry
   *
   * <p>Note this function does not write the new state, set must be called to make changes
   * permanent.
   *
   * @param request ModelEntryUpdate
   * @return True if the IP address was unknown, otherwise false
   */
  public Boolean updateEntry(ModelEntryUpdate request) {
    if (request.ipAddress == null) {
      throw new IllegalArgumentException("ipAddress in ModelEntryUpdate cannot be null");
    }
    DateTime timestamp = request.timestamp;
    if (timestamp == null) {
      timestamp = new DateTime();
    }
    ModelEntry ent = entries.get(request.ipAddress);
    if (ent == null) { // New entry for this user model
      ent = new ModelEntry();
      ent.setTimestamp(timestamp);
      ent.setLatitude(request.latitude);
      ent.setLongitude(request.longitude);
      ent.setUserAgent(request.userAgent);
      entries.put(request.ipAddress, ent);
      return true;
    }

    // Known entry, update it
    ent.setTimestamp(timestamp);
    ent.setLatitude(request.latitude);
    ent.setLongitude(request.longitude);
    ent.setUserAgent(request.userAgent);
    return false;
  }

  /**
   * Get entries associated with model
   *
   * @return Map of model entries
   */
  @JsonProperty("entries")
  public Map<String, ModelEntry> getEntries() {
    return entries;
  }

  /**
   * Set entries associated with model
   *
   * @param entries Map
   */
  public void setEntries(Map<String, ModelEntry> entries) {
    this.entries = entries;
  }

  /**
   * Get subject associated with model
   *
   * @return Subject string
   */
  @JsonProperty("subject")
  public String getSubject() {
    return subject;
  }

  /**
   * Set subject associated with model
   *
   * @param subject Subject string
   */
  public void setSubject(String subject) {
    this.subject = subject;
  }

  /**
   * Retrieve state object for user
   *
   * @param user Subject name to retrieve state for
   * @param s Initialized state cursor for request
   * @return User {@link AuthStateModel} or null if it does not exist
   */
  public static AuthStateModel get(String user, StateCursor s, PruningStrategy ps)
      throws StateException {
    AuthStateModel ret = s.get(user, AuthStateModel.class);
    if (ret == null) {
      return null;
    }
    ps.pruneState(ret);
    return ret;
  }

  /**
   * Persist state using state interface
   *
   * <p>Calling set will also commit and close the cursor.
   *
   * @param s Initialized state cursor for request
   */
  public void set(StateCursor s, PruningStrategy ps) throws StateException {
    ps.pruneState(this);
    s.set(subject, this);
    s.commit();
  }

  /**
   * Perform geo-velocity analysis using the latest entries in the model
   *
   * <p>The latest entry in the model (e.g., last known authentication event) is compared against
   * the entry that precedes it. If long/lat information is available, this information is used to
   * calculate the distance between the events and the amount of time that passed between the
   * events.
   *
   * <p>If geo-velocity analysis was possible, a GeoVelocityResponse is returned, null if not.
   *
   * @param maxKmPerSecond The maximum KM per second to use for the analysis
   * @return GeoVelocityResponse or null
   */
  public GeoVelocityResponse geoVelocityAnalyzeLatest(Double maxKmPerSecond) {
    ArrayList<AbstractMap.SimpleEntry<String, ModelEntry>> ent = timeSortedEntries();

    int s = ent.size();
    if (s <= 1) {
      return null;
    }

    AbstractMap.SimpleEntry<String, ModelEntry> prev = ent.get(s - 2);
    AbstractMap.SimpleEntry<String, ModelEntry> cur = ent.get(s - 1);

    // Make sure we have long/lat for both entries
    if ((prev.getValue().getLatitude() == null)
        || (prev.getValue().getLongitude() == null)
        || (cur.getValue().getLatitude() == null)
        || (cur.getValue().getLongitude() == null)) {
      return null;
    }

    Double kmdist =
        GeoUtil.kmBetweenTwoPoints(
            prev.getValue().getLatitude(),
            prev.getValue().getLongitude(),
            cur.getValue().getLatitude(),
            cur.getValue().getLongitude());

    long td =
        (cur.getValue().getTimestamp().getMillis() / 1000)
            - (prev.getValue().getTimestamp().getMillis() / 1000);

    if ((kmdist / td) > maxKmPerSecond) {
      return new GeoVelocityResponse(td, kmdist, true)
          .withPreviousSource(prev.getKey())
          .withCurrentSource(cur.getKey());
    }
    return new GeoVelocityResponse(td, kmdist, false)
        .withPreviousSource(prev.getKey())
        .withCurrentSource(cur.getKey());
  }

  /**
   * Return all entries in AuthStateModel as an array list, sorted by timestamp
   *
   * @return ArrayList
   */
  public ArrayList<AbstractMap.SimpleEntry<String, ModelEntry>> timeSortedEntries() {
    ArrayList<AbstractMap.SimpleEntry<String, ModelEntry>> ret = new ArrayList<>();
    for (Map.Entry<String, ModelEntry> entry : entries.entrySet()) {
      ret.add(new AbstractMap.SimpleEntry<String, ModelEntry>(entry.getKey(), entry.getValue()));
    }
    Collections.sort(
        ret,
        new Comparator<AbstractMap.SimpleEntry<String, ModelEntry>>() {
          @Override
          public int compare(
              AbstractMap.SimpleEntry<String, ModelEntry> lhs,
              AbstractMap.SimpleEntry<String, ModelEntry> rhs) {
            DateTime lhsd = lhs.getValue().getTimestamp();
            DateTime rhsd = rhs.getValue().getTimestamp();
            if (lhsd.isAfter(rhsd)) {
              return 1;
            } else if (lhsd.isBefore(rhsd)) {
              return -1;
            }
            return 0;
          }
        });
    return ret;
  }

  /**
   * Create new state model for user
   *
   * @param subject Subject user name
   */
  @JsonCreator
  public AuthStateModel(@JsonProperty("subject") String subject) {
    this.subject = subject;
    entries = new HashMap<String, ModelEntry>();
  }
}
