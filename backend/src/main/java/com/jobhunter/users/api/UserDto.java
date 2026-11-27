package com.jobhunter.users.api;

import com.jobhunter.users.domain.User;

public record UserDto(Long id, String email, String fullName, String role, String locale, String timezone,
                      String theme) {

    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getRole().name(), u.getLocale(),
                u.getTimezone(), u.getTheme());
    }
}
