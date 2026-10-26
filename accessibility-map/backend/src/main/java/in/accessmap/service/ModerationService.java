package in.accessmap.service;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import in.accessmap.domain.Report;
import in.accessmap.domain.ModerationAudit;
import in.accessmap.domain.ReviewDecision;
import in.accessmap.domain.ReviewStatus;
import in.accessmap.dto.ModerationAuditResponse;
import in.accessmap.dto.ModerationReportResponse;
import in.accessmap.dto.ReviewReportRequest;
import in.accessmap.repository.AppUserRepository;
import in.accessmap.repository.ModerationAuditRepository;
import in.accessmap.repository.ReportRepository;

@Service
public class ModerationService {
    private final ReportRepository reportRepository;
    private final ModerationAuditRepository auditRepository;
    private final AppUserRepository userRepository;

    public ModerationService(
            ReportRepository reportRepository,
            ModerationAuditRepository auditRepository,
            AppUserRepository userRepository) {
        this.reportRepository = reportRepository;
        this.auditRepository = auditRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ModerationReportResponse> getPendingReports() {
        return reportRepository.findAllByReviewStatusOrderByReportedAtAsc(ReviewStatus.PENDING).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<ModerationAuditResponse> getAuditHistory() {
        return auditRepository.findTop100ByOrderByReviewedAtDesc().stream()
                .map(this::toAuditResponse)
                .toList();
    }

    @Transactional
    public void reviewReport(Long reportId, ReviewReportRequest request, String moderatorEmail) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found."));
        if (report.getReviewStatus() != ReviewStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This report has already been reviewed.");
        }
        var moderator = userRepository.findByEmail(moderatorEmail)
                .filter(user -> user.isModerator())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Moderator account is no longer authorized."));
        ReviewStatus decision = request.decision() == ReviewDecision.APPROVE
                ? ReviewStatus.APPROVED
                : ReviewStatus.REJECTED;
        report.review(decision);
        auditRepository.save(new ModerationAudit(report, moderator, decision.name(), Instant.now()));
    }

    private ModerationReportResponse toResponse(Report report) {
        return new ModerationReportResponse(
                report.getId(),
                report.getFeature().getPlace().getName(),
                report.getFeature().getLabel(),
                report.getFeature().getType(),
                report.getStatus(),
                report.getNote(),
                report.getUser() == null ? null : report.getUser().getEmail(),
                report.getReportedAt());
    }

    private ModerationAuditResponse toAuditResponse(ModerationAudit audit) {
        Report report = audit.getReport();
        return new ModerationAuditResponse(
                report.getId(),
                report.getFeature().getPlace().getName(),
                report.getFeature().getLabel(),
                report.getFeature().getType(),
                report.getStatus(),
                audit.getDecision(),
                audit.getModerator().getEmail(),
                audit.getReviewedAt());
    }
}
