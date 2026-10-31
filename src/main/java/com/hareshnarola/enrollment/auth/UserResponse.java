package com.hareshnarola.enrollment.auth;

public record UserResponse(Long id, String email, String fullName, Role role) {

    static UserResponse from(UserAccount user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
    }
}
