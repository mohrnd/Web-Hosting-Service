package com.authapi.webhostingservice.controller;

import com.authapi.webhostingservice.model.User;
import com.authapi.webhostingservice.repository.UserRepository;
import com.authapi.webhostingservice.service.UserContainerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminUserController {

    private final UserRepository userRepository;
    private final UserContainerService userContainerService;

    public AdminUserController(UserRepository userRepository, UserContainerService userContainerService) {
        this.userRepository = userRepository;
        this.userContainerService = userContainerService;
    }

    /**
     * DELETE /api/admin/users?email={email}
     * Deletes a user and their containers.
     */
    @DeleteMapping("/users")
    public ResponseEntity<?> deleteUser(@RequestParam String email) {
        // 1. Find user
        User user = userRepository.findByEmail(email);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        // 2. Delete containers belonging to that user (if any)
        try {
            userContainerService.deleteContainer(email);
        } catch (Exception e) {
            System.err.println("Warning: failed to delete containers for " + email + ": " + e.getMessage());
        }

        // 3. Delete user from database
        userRepository.deleteByEmail(email);

        return ResponseEntity.ok(Map.of("message", "User and their containers deleted successfully", "email", email));
    }

    @GetMapping("/users")
public List<User> listAllUsers() {
    return userRepository.findAll();
}
}
