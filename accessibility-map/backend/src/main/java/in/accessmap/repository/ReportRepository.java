package in.accessmap.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import in.accessmap.domain.Report;
import in.accessmap.domain.ReviewStatus;
import jakarta.persistence.LockModeType;

public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findAllByFeatureIdInOrderByReportedAtDesc(Collection<Long> featureIds);

    boolean existsByFeature_IdAndUser_IdAndReportedAtGreaterThanEqual(
            Long featureId, Long userId, Instant reportedAt);

    long countByUser_IdAndReportedAtGreaterThanEqual(Long userId, Instant reportedAt);

    List<Report> findAllByReviewStatusOrderByReportedAtAsc(ReviewStatus reviewStatus);

    boolean existsByFeature_IdAndReviewStatus(Long featureId, ReviewStatus reviewStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select report from Report report where report.id = :reportId")
    Optional<Report> findByIdForUpdate(@Param("reportId") Long reportId);
}
