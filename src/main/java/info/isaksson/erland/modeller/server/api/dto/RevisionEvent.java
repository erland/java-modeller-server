package info.isaksson.erland.modeller.server.api.dto;

import java.util.UUID;

/**
 * Lightweight stream event used for signalling that the dataset head revision advanced.
 *
 * Phase 3 (SSE).
 */
public record RevisionEvent(UUID datasetId, long revision) {
}
