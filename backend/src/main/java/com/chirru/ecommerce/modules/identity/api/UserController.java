package com.chirru.ecommerce.modules.identity.api;

import com.chirru.ecommerce.modules.identity.application.UserProfileService;
import com.chirru.ecommerce.modules.identity.application.UserProfileService.UserProfile;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserProfileService userProfileService;

    public UserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    public UserProfile getCurrentUser(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return userProfileService.getProfile(userId);
    }
}
