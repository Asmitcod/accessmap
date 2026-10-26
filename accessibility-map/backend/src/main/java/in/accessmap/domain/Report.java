package in.accessmap.domain;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "reports")
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feature_id", nullable = false)
    private Feature feature;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Enumerated(EnumType.STRING)
    private ReportStatus status;

    private String note;
    private Instant reportedAt;
    private int ttlDays;

    protected Report() {
    }

    public Report(Feature feature, ReportStatus status, String note, Instant reportedAt, int ttlDays) {
        this.feature = feature;
        this.status = status;
        this.note = note;
        this.reportedAt = reportedAt;
        this.ttlDays = ttlDays;
    }

    public Report(
            Feature feature,
            AppUser user,
            ReportStatus status,
            String note,
            Instant reportedAt,
            int ttlDays) {
        this.feature = feature;
        this.user = user;
        this.status = status;
        this.note = note;
        this.reportedAt = reportedAt;
        this.ttlDays = ttlDays;
    }

    public Long getId() {
        return id;
    }

    public Feature getFeature() {
        return feature;
    }

    public AppUser getUser() {
        return user;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    public int getTtlDays() {
        return ttlDays;
    }
}
