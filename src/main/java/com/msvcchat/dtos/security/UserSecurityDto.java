package com.msvcchat.dtos.security;


import lombok.*;

import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSecurityDto {
    private UUID id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private Boolean enabled;
    private Set<RoleDto> roles;
}
