package com.jobhunter.auth.api;

import com.jobhunter.auth.application.AuthService;
import com.jobhunter.auth.application.LoginRateLimiter;
import com.jobhunter.auth.application.SecurityProperties;
import com.jobhunter.shared.api.ApiException;
import com.jobhunter.users.api.UserDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    static final String COOKIE = "jh_refresh";
    static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final LoginRateLimiter rateLimiter;
    private final SecurityProperties props;

    public AuthController(AuthService authService, LoginRateLimiter rateLimiter, SecurityProperties props) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.props = props;
    }

    public record RegisterRequest(@NotBlank @Email @Size(max = 254) String email,
                                  @NotBlank @Size(min = 10, max = 128) String password,
                                  @NotBlank @Size(max = 150) String fullName) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank @Size(max = 128) String password) {
    }

    public record AuthResponse(String accessToken, long expiresIn, UserDto user) {
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest body, HttpServletRequest req, HttpServletResponse res) {
        rateLimiter.check(req.getRemoteAddr());
        return respond(authService.register(body.email(), body.password(), body.fullName(), ua(req), req.getRemoteAddr()), res);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest req, HttpServletResponse res) {
        rateLimiter.check(req.getRemoteAddr());
        return respond(authService.login(body.email(), body.password(), ua(req), req.getRemoteAddr()), res);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@CookieValue(name = COOKIE, required = false) String token, HttpServletRequest req,
                                HttpServletResponse res) {
        requireAjaxHeader(req);
        rateLimiter.check("refresh:" + req.getRemoteAddr());
        try {
            return respond(authService.refresh(token, ua(req), req.getRemoteAddr()), res);
        } catch (ApiException e) {
            clearCookie(res);
            throw e;
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = COOKIE, required = false) String token, HttpServletRequest req,
                       HttpServletResponse res) {
        requireAjaxHeader(req);
        authService.logout(token);
        clearCookie(res);
    }

    private AuthResponse respond(AuthService.Session session, HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, cookie(session.refreshToken(), props.refreshTokenTtl().toSeconds()).toString());
        res.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return new AuthResponse(session.accessToken(), session.expiresIn(), UserDto.from(session.user()));
    }

    private void clearCookie(HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, cookie("", 0).toString());
    }

    private ResponseCookie cookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAgeSeconds)
                .build();
    }

    /** CSRF defence for the cookie-authenticated endpoints: cross-site forms cannot set custom headers. */
    private static void requireAjaxHeader(HttpServletRequest req) {
        if (!"XMLHttpRequest".equals(req.getHeader("X-Requested-With"))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MISSING_CSRF_HEADER", "Falta la cabecera X-Requested-With");
        }
    }

    private static String ua(HttpServletRequest req) {
        return req.getHeader("User-Agent");
    }
}
