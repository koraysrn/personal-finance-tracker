package com.personal.finance.security;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes the CSRF token so the TypeScript frontend can send it back in the
 * {@code X-XSRF-TOKEN} header. Calling {@link CsrfToken#getToken()} also forces
 * the token cookie to be written to the client.
 */
@RestController
public class CsrfController {

    @GetMapping("/api/auth/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of(
                "headerName", token.getHeaderName(),
                "parameterName", token.getParameterName(),
                "token", token.getToken());
    }
}
