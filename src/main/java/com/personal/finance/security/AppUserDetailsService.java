package com.personal.finance.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Loads the single application user into the Spring Security authentication
 * machinery. Locked accounts are reported through {@code accountLocked} so the
 * authentication provider rejects even a correct password while the lock lasts.
 */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));

        boolean locked = user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(Instant.now());

        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles("USER")
                .accountLocked(locked)
                .build();
    }
}
