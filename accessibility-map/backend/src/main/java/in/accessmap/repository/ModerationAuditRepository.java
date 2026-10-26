package in.accessmap.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import in.accessmap.domain.ModerationAudit;

public interface ModerationAuditRepository extends JpaRepository<ModerationAudit, Long> {
    List<ModerationAudit> findTop100ByOrderByReviewedAtDesc();
}
