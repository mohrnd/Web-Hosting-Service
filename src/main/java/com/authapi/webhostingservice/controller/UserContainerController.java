package com.authapi.webhostingservice.controller;

import com.authapi.webhostingservice.model.UserContainer;
import com.authapi.webhostingservice.service.UserContainerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/container")
public class UserContainerController {

    private final UserContainerService userContainerService;

    public UserContainerController(UserContainerService userContainerService) {
        this.userContainerService = userContainerService;
    }

    @GetMapping("/status")
    public ResponseEntity<?> getContainerStatus(Authentication authentication) {
        String userEmail = authentication.getName();
        
        Optional<UserContainer> containerOpt = userContainerService.getUserContainer(userEmail);
        
        if (containerOpt.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("hasContainer", false);
            response.put("message", "No active container");
            return ResponseEntity.ok(response);
        }

        UserContainer container = containerOpt.get();
        userContainerService.updateContainerStatus(userEmail);
        
        Map<String, Object> response = new HashMap<>();
        response.put("hasContainer", true);
        response.put("containerId", container.getContainerId());
        response.put("containerName", container.getContainerName());
        response.put("port", container.getPort());
        response.put("status", container.getStatus());
        response.put("url", "http://192.168.1.81:" + container.getPort());
        response.put("createdAt", container.getCreatedAt());
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/create")
    public ResponseEntity<?> createContainer(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        
        String userEmail = authentication.getName();

        // Check if user already has a container
        if (userContainerService.hasActiveContainer(userEmail)) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "You already have an active container. Delete it first.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }

        // Validate file
        if (file.isEmpty()) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "File is empty");
            return ResponseEntity.badRequest().body(error);
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".html")) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Only HTML files are allowed");
            return ResponseEntity.badRequest().body(error);
        }

        try {
            // Read HTML content
            String htmlContent = new String(file.getBytes(), StandardCharsets.UTF_8);

            // Create container
            UserContainer container = userContainerService.createContainer(userEmail, htmlContent);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Container created successfully");
            response.put("containerId", container.getContainerId());
            response.put("containerName", container.getContainerName());
            response.put("port", container.getPort());
            response.put("url", "http://192.168.1.81:" + container.getPort());
            response.put("status", container.getStatus());

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IOException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to create container: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteContainer(Authentication authentication) {
        String userEmail = authentication.getName();

        try {
            userContainerService.deleteContainer(userEmail);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Container deleted successfully");
            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to delete container: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}