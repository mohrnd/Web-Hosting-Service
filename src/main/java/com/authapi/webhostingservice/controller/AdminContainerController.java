package com.authapi.webhostingservice.controller;

import com.authapi.webhostingservice.model.UserContainer;
import com.authapi.webhostingservice.service.UserContainerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminContainerController {

    private final UserContainerService userContainerService;

    public AdminContainerController(UserContainerService userContainerService) {
        this.userContainerService = userContainerService;
    }

    @GetMapping("/containers")
    public List<UserContainer> getActiveContainers() {
        return userContainerService.getAllContainers();
    }

    @DeleteMapping("/containers")
    public ResponseEntity<?> deleteUserContainer(@RequestParam String userEmail) {
        try {
            userContainerService.deleteContainer(userEmail);
            return ResponseEntity.ok(Map.of("message", "Container deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
