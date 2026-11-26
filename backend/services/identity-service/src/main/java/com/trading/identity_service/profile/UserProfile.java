package com.trading.identity_service.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A user's preferences. Rows are created by {@link UserProfileRepository#insertDefaultsIfMissing}. */
@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 10)
    private String theme;

    @Column(name = "default_exchange", nullable = false, length = 10)
    private String defaultExchange;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;

    /** Required by JPA. */
    protected UserProfile() {
    }

    public void update(String theme, String defaultExchange, String timezone) {
        this.theme = theme;
        this.defaultExchange = defaultExchange;
        this.timezone = timezone;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public String getTheme() { return theme; }
    public String getDefaultExchange() { return defaultExchange; }
    public String getTimezone() { return timezone; }
}
