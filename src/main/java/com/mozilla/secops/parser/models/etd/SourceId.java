package com.mozilla.secops.parser.models.etd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SourceId implements Serializable {
  private static final long serialVersionUID = 1L;

  private String customerOrganizationNumber;
  private String projectId;

  /**
   * Get GCP org number
   *
   * @return String
   */
  @JsonProperty("customerOrganizationNumber")
  public String getCustomerOrganizationNumber() {
    return customerOrganizationNumber;
  }

  /**
   * Get GCP project id for source of Finding
   *
   * @return String
   */
  @JsonProperty("projectId")
  public String getProjectId() {
    return projectId;
  }

  @Override
  public boolean equals(Object o) {
    SourceId sid = (SourceId) o;
    return sid.getCustomerOrganizationNumber().equals(customerOrganizationNumber)
        && sid.getProjectId().equals(projectId);
  }

  @Override
  public int hashCode() {
    return customerOrganizationNumber.hashCode() * projectId.hashCode();
  }

  public SourceId() {}
}