package com.personal.finance.security;

import com.personal.finance.security.dto.ChangePasswordRequest;
import com.personal.finance.security.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Registration, current-user lookup and password change endpoints.
 * Login itself is handled by the Spring Security form login filter.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AppUserService appUserService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest request) {
        AppUser user = appUserService.register(request);
        return Map.of("registered", true, "username", user.getUsername());
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        AppUser user = appUserService.findByUsername(authentication.getName());
        return Map.of(
                "username", user.getUsername(),
                "displayName", user.getDisplayName() == null ? "" : user.getDisplayName());
    }

    @PostMapping("/password")
    public Map<String, Object> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                              Authentication authentication) {
        appUserService.changePassword(authentication.getName(), request);
        return Map.of("changed", true);
    }
}
