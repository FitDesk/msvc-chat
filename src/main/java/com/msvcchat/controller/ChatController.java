package com.msvcchat.controller;

import com.msvcchat.dtos.UserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chat/users")
public class ChatController {
    private final ReactiveMongoTemplate mongoTemplate;

    @GetMapping
    public Flux<UserDto> getAllUsers() {
        return mongoTemplate.findAll(UserDto.class, "users");
    }

}
