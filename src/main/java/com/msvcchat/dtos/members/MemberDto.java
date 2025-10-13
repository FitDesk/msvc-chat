package com.msvcchat.dtos.members;

public record MemberDto(
        String userId,
        String firstName,
        String lastName,
        String dni,
        String phone,
        String profileImageUrl,
        String status
) {
}
