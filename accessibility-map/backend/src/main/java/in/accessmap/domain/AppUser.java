package in.accessmap.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(name = "is_moderator", nullable = false, columnDefinition = "boolean default false")
    private boolean moderator;

    protected AppUser() {
    }

    public AppUser(String email, String passwordHash, Instant createdAt) {
        this(email, passwordHash, createdAt, false);
    }

    public AppUser(String email, String passwordHash, Instant createdAt, boolean moderator) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.moderator = moderator;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isModerator() {
        return moderator;
    }

    public void promoteToModerator() {
        moderator = true;
    }
}
