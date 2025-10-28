package com.authapi.webhostingservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminSecureController {

    @GetMapping("/dashboard")
    public ResponseEntity<String> dashboard(Authentication authentication) {
        // No need to check adminRepository - Spring Security already verified ROLE_ADMIN
        String email = authentication.getName();
        return ResponseEntity.ok("Welcome to the Admin Dashboard, " + email + "!");
    }
}

