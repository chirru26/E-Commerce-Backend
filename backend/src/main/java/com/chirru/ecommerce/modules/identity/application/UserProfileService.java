package com.chirru.ecommerce.modules.identity.application;

import com.chirru.ecommerce.modules.identity.domain.IdentityUser;
import com.chirru.ecommerce.modules.identity.infrastructure.IdentityUserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserProfileService {
    private final IdentityUserRepository users;

    public UserProfileService(IdentityUserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserProfile getProfile(UUID userId) {
        IdentityUser user = users.findById(userId)
                .filter(IdentityUser::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return new UserProfile(user.getId(), user.getEmail(), user.getRole().name(), user.getCreatedAt());
    }

    public record UserProfile(UUID id, String email, String role, Instant createdAt) {}
}
