package in.accessmap.dto;

import java.time.Instant;

import in.accessmap.domain.FeatureType;
import in.accessmap.domain.ReportStatus;

public record ModerationReportResponse(
        Long id,
        String placeName,
        String featureLabel,
        FeatureType featureType,
        ReportStatus status,
        String note,
        String reporterEmail,
        Instant reportedAt) {
}
