package com.msvcchat.mappers;

import com.msvcchat.config.ReactiveMapperConfig;
import com.msvcchat.dtos.ChatMessageDto;
import com.msvcchat.dtos.CreateChatMessageDto;
import com.msvcchat.entity.ChatMessage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = ReactiveMapperConfig.class)
public interface ChatMessageMapper {
    ChatMessageDto toDto(ChatMessage entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "roomId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    ChatMessage toEntity(CreateChatMessageDto dto);


    @Mapping(target = "id", ignore = true)
    @Mapping(target = "roomId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntityFromDto(CreateChatMessageDto dto, @MappingTarget ChatMessage entity);


}
