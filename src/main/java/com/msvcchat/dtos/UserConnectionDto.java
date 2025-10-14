package com.msvcchat.dtos;

public record UserConnectionDto(
        String id,
        String username,
        boolean enabled,
        String avatar,
        String initials
) {
}
