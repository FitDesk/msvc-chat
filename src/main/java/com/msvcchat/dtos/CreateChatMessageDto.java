package com.msvcchat.dtos;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateChatMessageDto {
    private String fromId;
    private String fromRole;
    private String text;

}
