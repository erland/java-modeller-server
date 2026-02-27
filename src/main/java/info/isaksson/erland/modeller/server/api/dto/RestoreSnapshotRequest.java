package info.isaksson.erland.modeller.server.api.dto;

/**
 * Optional request body for snapshot restore.
 * All fields are optional.
 */
public class RestoreSnapshotRequest {
    /** Optional human-readable reason/message for the restore operation. */
    public String message;
}
