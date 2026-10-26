package in.accessmap.dto;

import java.time.Instant;

import in.accessmap.domain.FeatureType;
import in.accessmap.domain.ReportStatus;

public record ModerationAuditResponse(
        Long reportId,
        String placeName,
        String featureLabel,
        FeatureType featureType,
        ReportStatus reportStatus,
        String decision,
        String moderatorEmail,
        Instant reviewedAt) {
}
