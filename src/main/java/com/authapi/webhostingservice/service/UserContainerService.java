package com.authapi.webhostingservice.service;

import com.authapi.webhostingservice.model.UserContainer;
import com.authapi.webhostingservice.repository.UserContainerRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserContainerService {

    private final UserContainerRepository userContainerRepository;
    private final UserDockerService userDockerService;  // CHANGED

    public UserContainerService(UserContainerRepository userContainerRepository, 
                               UserDockerService userDockerService) {  // CHANGED
        this.userContainerRepository = userContainerRepository;
        this.userDockerService = userDockerService;  // CHANGED
    }

    public boolean hasActiveContainer(String userEmail) {
        return userContainerRepository.existsByUserEmail(userEmail);
    }

    public Optional<UserContainer> getUserContainer(String userEmail) {
        return userContainerRepository.findByUserEmail(userEmail);
    }

    public UserContainer createContainer(String userEmail, String htmlContent) throws IOException {
        // Check if user already has a container
        if (hasActiveContainer(userEmail)) {
            throw new IllegalStateException("User already has an active container");
        }

        // Create Docker container using new service
        UserDockerService.ContainerCreationResult result = 
            userDockerService.createUserContainer(userEmail, htmlContent);

        // Save to MongoDB
        UserContainer userContainer = new UserContainer(
                userEmail,
                result.getContainerId(),
                result.getContainerName(),
                result.getPort(),
                result.getStatus()
        );

        return userContainerRepository.save(userContainer);
    }

    public void deleteContainer(String userEmail) {
        Optional<UserContainer> containerOpt = userContainerRepository.findByUserEmail(userEmail);
        
        if (containerOpt.isEmpty()) {
            throw new IllegalStateException("No container found for user");
        }

        UserContainer container = containerOpt.get();
        
        // Stop and remove Docker container with cleanup
        userDockerService.stopAndRemoveContainer(
            container.getContainerId(), 
            container.getContainerName()
        );
        
        // Remove from MongoDB
        userContainerRepository.delete(container);
    }

    public void updateContainerStatus(String userEmail) {
        Optional<UserContainer> containerOpt = userContainerRepository.findByUserEmail(userEmail);
        
        if (containerOpt.isPresent()) {
            UserContainer container = containerOpt.get();
            String status = userDockerService.getContainerStatus(container.getContainerId());
            container.setStatus(status);
            container.setUpdatedAt(LocalDateTime.now());
            userContainerRepository.save(container);
        }
    }

    public void restartContainer(String userEmail) {
        Optional<UserContainer> containerOpt = userContainerRepository.findByUserEmail(userEmail);
        
        if (containerOpt.isEmpty()) {
            throw new IllegalStateException("No container found for user");
        }

        UserContainer container = containerOpt.get();
        userDockerService.restartContainer(container.getContainerId());
        
        // Update status
        container.setStatus("running");
        container.setUpdatedAt(LocalDateTime.now());
        userContainerRepository.save(container);
    }
    public List<UserContainer> getAllContainers() {
    return userContainerRepository.findAll();
}

}