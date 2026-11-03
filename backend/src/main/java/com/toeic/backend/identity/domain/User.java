
package com.toeic.backend.identity.domain;
import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "users")
@DynamicUpdate
public class User {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "email_verified_at")
    private OffsetDateTime emailVerifiedAt;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    protected User() {
    }

    public User(String email, String passwordHash, String fullName) {
        this.userId = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.status = "PENDING";
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void verifyEmail() {
        this.emailVerifiedAt = OffsetDateTime.now();
        this.status = "ACTIVE";
        this.updatedAt = OffsetDateTime.now();
    }

    public void recordLogin(OffsetDateTime now) {
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException("Only active accounts can log in");
        }
        this.lastLoginAt = now;
        this.updatedAt = now;
    }

    public void updateFullName(String fullName, OffsetDateTime now) {
        if (fullName == null || fullName.isBlank() || fullName.length() > 150) {
            throw new IllegalArgumentException("Invalid full name");
        }
        this.fullName = fullName.strip();
        this.updatedAt = now;
    }

    public void changePasswordHash(String hash, OffsetDateTime now) {
        if (hash == null || !hash.matches("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}") || now == null) {
            throw new IllegalArgumentException("Invalid password hash");
        }
        this.passwordHash = hash;
        this.updatedAt = now;
    }
    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }

    public void addRole(Role role) {
        if (role == null) {
            throw new IllegalArgumentException("Role must not be null");
        }

        this.roles.add(role);
    }
}
