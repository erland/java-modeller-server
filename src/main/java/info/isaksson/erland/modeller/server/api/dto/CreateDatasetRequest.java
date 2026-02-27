package info.isaksson.erland.modeller.server.api.dto;

public class CreateDatasetRequest {
    public String name;
    public String description;
    /** Optional (Phase 2): none | basic | strict. Defaults to "none". */
    public String validationPolicy;
}
