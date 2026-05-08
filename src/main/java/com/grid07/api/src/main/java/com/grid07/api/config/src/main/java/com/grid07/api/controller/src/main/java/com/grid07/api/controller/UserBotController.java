package com.grid07.api.controller;

import com.grid07.api.dto.Dtos;
import com.grid07.api.entity.Bot;
import com.grid07.api.entity.User;
import com.grid07.api.repository.BotRepository;
import com.grid07.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserBotController {

    private final UserRepository userRepository;
    private final BotRepository botRepository;

    @PostMapping("/users")
    public ResponseEntity<Dtos.ApiResponse<User>> createUser(@RequestBody User user) {
        User saved = userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Dtos.ApiResponse.ok("User created.", saved));
    }

    @GetMapping("/users")
    public ResponseEntity<Dtos.ApiResponse<List<User>>> listUsers() {
        return ResponseEntity.ok(Dtos.ApiResponse.ok(userRepository.findAll()));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<Dtos.ApiResponse<User>> getUser(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(u -> ResponseEntity.ok(Dtos.ApiResponse.ok(u)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Dtos.ApiResponse.error("User not found: " + id)));
    }

    @PostMapping("/bots")
    public ResponseEntity<Dtos.ApiResponse<Bot>> createBot(@RequestBody Bot bot) {
        Bot saved = botRepository.save(bot);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Dtos.ApiResponse.ok("Bot created.", saved));
    }

    @GetMapping("/bots")
    public ResponseEntity<Dtos.ApiResponse<List<Bot>>> listBots() {
        return ResponseEntity.ok(Dtos.ApiResponse.ok(botRepository.findAll()));
    }

    @GetMapping("/bots/{id}")
    public ResponseEntity<Dtos.ApiResponse<Bot>> getBot(@PathVariable Long id) {
        return botRepository.findById(id)
                .map(b -> ResponseEntity.ok(Dtos.ApiResponse.ok(b)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Dtos.ApiResponse.error("Bot not found: " + id)));
    }
}
