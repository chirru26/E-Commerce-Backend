package com.chirru.ecommerce.modules.identity.api;

import com.chirru.ecommerce.modules.identity.application.AuthService;
import com.chirru.ecommerce.modules.identity.application.AuthService.AuthResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResult> register(@Valid @RequestBody Credentials request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request.email(), request.password()));
    }

    @PostMapping("/login")
    public AuthResult login(@Valid @RequestBody Credentials request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/refresh")
    public AuthResult refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }

    public record Credentials(@NotBlank @Email @Size(max = 254) String email,
                              @NotBlank @Size(min = 12, max = 72) String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
}
