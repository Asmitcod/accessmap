package in.accessmap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import in.accessmap.domain.FeatureType;
import in.accessmap.domain.ReportStatus;

public record CreateFeatureRequest(
        @NotNull FeatureType type,
        @NotBlank @Size(max = 80) String label,
        @NotNull ReportStatus status,
        @Size(max = 280) String note) {
}
