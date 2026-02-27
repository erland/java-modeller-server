package info.isaksson.erland.modeller.server.api.dto;

public class UpdateDatasetRequest {
    public String name;
    public String description;
    /** Optional (Phase 2): none | basic | strict. */
    public String validationPolicy;
}
