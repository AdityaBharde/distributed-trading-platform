package com.trading.identity_service.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    /**
     * Lazy creation that is safe under concurrency: two parallel first requests cannot both fail on the
     * primary key, because the database itself ignores the second insert. Column defaults fill the values.
     */
    @Modifying
    @Query(value = "insert into user_profiles (user_id) values (:userId) on conflict (user_id) do nothing", nativeQuery = true)
    void insertDefaultsIfMissing(@Param("userId") UUID userId);
}
