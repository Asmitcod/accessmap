package in.accessmap.dto;

import java.time.Instant;

import in.accessmap.domain.FeatureType;
import in.accessmap.domain.ReportStatus;

public record FeatureResponse(
        String id,
        FeatureType type,
        String label,
        ReportStatus status,
        Instant lastConfirmedAt,
        long confirmationCount) {
}
