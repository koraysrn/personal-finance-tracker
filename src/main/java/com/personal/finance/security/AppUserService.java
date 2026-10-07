package com.personal.finance.security;

import com.personal.finance.common.api.ApiException;
import com.personal.finance.common.api.ErrorCode;
import com.personal.finance.security.dto.ChangePasswordRequest;
import com.personal.finance.security.dto.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service around the single user account: registration, lookup and
 * password change. Controllers only depend on this service, never directly on
 * the repository.
 */
@Service
@RequiredArgsConstructor
public class AppUserService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;

    @Transactional
    public AppUser register(RegisterRequest request) {
        if (userRepository.count() > 0) {
            throw new ApiException(ErrorCode.CONFLICT, "The application already has a registered user");
        }
        passwordPolicyValidator.validate(request.password(), request.username());

        AppUser user = new AppUser();
        user.setUsername(request.username().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public AppUser findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        AppUser user = findByUsername(username);

        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Current password is incorrect");
        }
        passwordPolicyValidator.validate(request.newPassword(), user.getUsername());

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }
}
