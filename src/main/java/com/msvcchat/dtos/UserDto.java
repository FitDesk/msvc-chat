package com.msvcchat.dtos;

public record UserDto(
        String id,
        String name,
        String role,
        String avatar
) {
}
