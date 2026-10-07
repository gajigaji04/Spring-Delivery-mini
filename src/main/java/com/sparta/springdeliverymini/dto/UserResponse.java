package com.sparta.springdeliverymini.dto;

import com.sparta.springdeliverymini.entity.Role;
import com.sparta.springdeliverymini.entity.User;

public record UserResponse(Long id, String username, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole());
    }
}
