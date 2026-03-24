package com.lifeenrichment.repository;

import com.lifeenrichment.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link User} entities.
 *
 * <p>Extends {@link JpaRepository} to provide standard CRUD operations,
 * with additional queries for authentication and role-based lookups.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /** Looks up a user by email address — used as the authentication principal. */
    Optional<User> findByEmail(String email);

    /** Returns all users assigned to the given role — useful for admin listings. */
    List<User> findByRole(User.Role role);

    /**
     * Looks up a user by their currently-stored refresh token.
     * Returns empty if the token has been revoked (set to {@code null} on logout).
     */
    Optional<User> findByRefreshToken(String refreshToken);
}
