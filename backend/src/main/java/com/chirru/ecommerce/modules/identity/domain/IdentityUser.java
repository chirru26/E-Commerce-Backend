package com.chirru.ecommerce.modules.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "identity_users", uniqueConstraints = {
    @UniqueConstraint(name = "uk_identity_users_email", columnNames = "email")
})
public class IdentityUser {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdentityUser() {}

    public IdentityUser(String email, String passwordHash) {
        this(email, passwordHash, Role.USER);
    }

    private IdentityUser(String email, String passwordHash, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = true;
    }

    /**
     * Creates an administrator only for trusted server-side provisioning paths.
     * Public registration must always use the regular constructor.
     */
    public static IdentityUser adminAccount(String email, String passwordHash) {
        return new IdentityUser(email, passwordHash, Role.ADMIN);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
}
