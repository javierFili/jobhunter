package com.jobhunter.users.api;

import com.jobhunter.auth.application.AuthService;
import com.jobhunter.shared.security.CurrentUser;
import com.jobhunter.users.application.UserService;
import com.jobhunter.users.domain.ConsentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final UserService userService;
    private final AuthService authService;

    public MeController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    public record UpdateMeRequest(@Size(min = 1, max = 150) String fullName, @Size(max = 10) String locale,
                                  @Size(max = 64) String timezone, String theme) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 10, max = 128) String newPassword) {
    }

    public record ConsentRequest(@NotNull ConsentType consentType, @NotBlank String provider, boolean granted) {
    }

    @GetMapping
    public UserDto me() {
        return UserDto.from(userService.get(CurrentUser.id()));
    }

    @PatchMapping
    public UserDto update(@Valid @RequestBody UpdateMeRequest body) {
        return UserDto.from(userService.updateSettings(CurrentUser.id(), body.fullName(), body.locale(),
                body.timezone(), body.theme()));
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest body) {
        long userId = CurrentUser.id();
        userService.changePassword(userId, body.currentPassword(), body.newPassword());
        authService.revokeAllSessions(userId);
    }

    @GetMapping("/consents")
    public List<UserService.ConsentView> consents() {
        return userService.consents(CurrentUser.id());
    }

    @PutMapping("/consents")
    public List<UserService.ConsentView> updateConsents(@RequestBody List<@Valid ConsentRequest> body) {
        return userService.updateConsents(CurrentUser.id(), body.stream()
                .map(c -> new UserService.ConsentChange(c.consentType(), c.provider(), c.granted())).toList());
    }
}
