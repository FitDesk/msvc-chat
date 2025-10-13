package com.msvcchat.dtos.security;

import java.util.Set;

public record SimpleUserDto(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        Boolean enabled,
        Set<SimpleRoleDto> roles
) {
}
