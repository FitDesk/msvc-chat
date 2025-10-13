package com.msvcchat.mappers;

import com.msvcchat.config.ReactiveMapperConfig;
import com.msvcchat.dtos.ConversationDto;
import com.msvcchat.entity.ConversationDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = ReactiveMapperConfig.class)
public interface ConversationMapper {

    @Mapping(target = "participant", ignore = true)
    @Mapping(target = "lastMessage", ignore = true)
    @Mapping(target = "lastMessageDate", ignore = true)
    @Mapping(target = "unreadCount", constant = "0")
    @Mapping(target = "isFavorite", constant = "false")
    @Mapping(target = "isConnected", constant = "false")
    ConversationDto toDto(ConversationDocument entity);

}
