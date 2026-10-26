package in.accessmap.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import in.accessmap.domain.ReportStatus;

public record CreateReportRequest(
        @NotNull ReportStatus status,
        @Size(max = 280) String note) {
}
