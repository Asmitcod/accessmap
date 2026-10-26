package in.accessmap.dto;

import in.accessmap.domain.ReviewDecision;
import jakarta.validation.constraints.NotNull;

public record ReviewReportRequest(@NotNull ReviewDecision decision) {
}
