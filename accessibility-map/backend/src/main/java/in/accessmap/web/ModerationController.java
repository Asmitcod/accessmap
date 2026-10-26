package in.accessmap.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import in.accessmap.dto.ModerationReportResponse;
import in.accessmap.dto.ModerationAuditResponse;
import in.accessmap.dto.ReviewReportRequest;
import in.accessmap.service.ModerationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/moderation/reports")
public class ModerationController {
    private final ModerationService moderationService;

    public ModerationController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @GetMapping
    public List<ModerationReportResponse> getPendingReports() {
        return moderationService.getPendingReports();
    }

    @GetMapping("/audit")
    public List<ModerationAuditResponse> getAuditHistory() {
        return moderationService.getAuditHistory();
    }

    @PostMapping("/{reportId}/decision")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reviewReport(
            @PathVariable Long reportId,
            @AuthenticationPrincipal String moderatorEmail,
            @Valid @RequestBody ReviewReportRequest request) {
        moderationService.reviewReport(reportId, request, moderatorEmail);
    }
}
