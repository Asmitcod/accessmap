package in.accessmap.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "moderation_audit")
public class ModerationAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false, unique = true)
    private Report report;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "moderator_user_id", nullable = false)
    private AppUser moderator;

    @Column(nullable = false, length = 16)
    private String decision;

    @Column(nullable = false)
    private Instant reviewedAt;

    protected ModerationAudit() {
    }

    public ModerationAudit(Report report, AppUser moderator, String decision, Instant reviewedAt) {
        this.report = report;
        this.moderator = moderator;
        this.decision = decision;
        this.reviewedAt = reviewedAt;
    }

    public Long getId() {
        return id;
    }

    public Report getReport() {
        return report;
    }

    public AppUser getModerator() {
        return moderator;
    }

    public String getDecision() {
        return decision;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
