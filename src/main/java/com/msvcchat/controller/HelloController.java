package com.msvcchat.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {
    @GetMapping
    public String saludo() {
        return "Hola Microservicio Chat";
    }
}
