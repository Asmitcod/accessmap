package in.accessmap.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import in.accessmap.domain.Report;
import in.accessmap.domain.ReviewStatus;

public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findAllByFeatureIdInOrderByReportedAtDesc(Collection<Long> featureIds);

    boolean existsByFeature_IdAndUser_IdAndReportedAtGreaterThanEqual(
            Long featureId, Long userId, Instant reportedAt);

    long countByUser_IdAndReportedAtGreaterThanEqual(Long userId, Instant reportedAt);

    List<Report> findAllByReviewStatusOrderByReportedAtAsc(ReviewStatus reviewStatus);

    boolean existsByFeature_IdAndReviewStatus(Long featureId, ReviewStatus reviewStatus);
}
