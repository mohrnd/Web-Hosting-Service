package com.authapi.webhostingservice.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

@Service
public class UserDockerService {

    private final DockerClient dockerClient;
    private static final String BASE_HTML_DIR = "/tmp/user-websites";

    public UserDockerService() {
        // Initialize Docker client
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("unix:///var/run/docker.sock")
                .build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        this.dockerClient = DockerClientBuilder.getInstance(config)
                .withDockerHttpClient(httpClient)
                .build();

        // Create base directory for user websites
        File baseDir = new File(BASE_HTML_DIR);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
    }

    /**
     * Creates a new container for a user with their HTML content
     */
    public ContainerCreationResult createUserContainer(String userEmail, String htmlContent) throws IOException {
        // Generate unique container name based on email
        String sanitizedEmail = userEmail.split("@")[0].replaceAll("[^a-zA-Z0-9]", "");
        String containerName = "user-" + sanitizedEmail + "-" + System.currentTimeMillis();
        
        // Create directory for user's website
        String userDir = BASE_HTML_DIR + "/" + containerName;
        File userDirFile = new File(userDir);
        userDirFile.mkdirs();
        
        // Write HTML file
        Path htmlPath = Paths.get(userDir, "index.html");
        Files.writeString(htmlPath, htmlContent);

        // Create nginx config for better performance (optional)
        String nginxConfig = "server {\n" +
                "    listen 80;\n" +
                "    root /usr/share/nginx/html;\n" +
                "    index index.html;\n" +
                "    location / {\n" +
                "        try_files $uri $uri/ =404;\n" +
                "    }\n" +
                "}";
        Path nginxConfigPath = Paths.get(userDir, "default.conf");
        Files.writeString(nginxConfigPath, nginxConfig);

        // Create Dockerfile
        String dockerfile = "FROM nginx:alpine\n" +
                           "COPY index.html /usr/share/nginx/html/index.html\n" +
                           "COPY default.conf /etc/nginx/conf.d/default.conf\n" +
                           "EXPOSE 80";
        Path dockerfilePath = Paths.get(userDir, "Dockerfile");
        Files.writeString(dockerfilePath, dockerfile);

        // Find available port
        int port = findAvailablePort();

        // Build Docker image
        String imageName = containerName + ":latest";
        try {
            dockerClient.buildImageCmd()
                    .withDockerfile(dockerfilePath.toFile())
                    .withBaseDirectory(userDirFile)
                    .withTags(new HashSet<>(Arrays.asList(imageName)))
                    .start()
                    .awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Image build was interrupted", e);
        }

        // Create container with port binding
        ExposedPort tcp80 = ExposedPort.tcp(80);
        Ports portBindings = new Ports();
        portBindings.bind(tcp80, Ports.Binding.bindPort(port));

        CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
                .withName(containerName)
                .withExposedPorts(tcp80)
                .withHostConfig(HostConfig.newHostConfig()
                        .withPortBindings(portBindings)
                        .withRestartPolicy(RestartPolicy.unlessStoppedRestart()))
                .exec();

        // Start the container
        dockerClient.startContainerCmd(container.getId()).exec();

        return new ContainerCreationResult(
                container.getId(),
                containerName,
                port,
                "running",
                imageName
        );
    }

    /**
     * Stops and removes a container completely
     */
    public void stopAndRemoveContainer(String containerId, String containerName) {
        try {
            // Stop container (with 10 second timeout)
            dockerClient.stopContainerCmd(containerId)
                    .withTimeout(10)
                    .exec();
            
            System.out.println("Container stopped: " + containerId);
        } catch (Exception e) {
            System.err.println("Error stopping container " + containerId + ": " + e.getMessage());
        }

        try {
            // Remove container
            dockerClient.removeContainerCmd(containerId)
                    .withForce(true)
                    .withRemoveVolumes(true)
                    .exec();
            
            System.out.println("Container removed: " + containerId);
        } catch (Exception e) {
            System.err.println("Error removing container " + containerId + ": " + e.getMessage());
        }

        // Remove the image
        try {
            String imageName = containerName + ":latest";
            dockerClient.removeImageCmd(imageName)
                    .withForce(true)
                    .exec();
            
            System.out.println("Image removed: " + imageName);
        } catch (Exception e) {
            System.err.println("Error removing image: " + e.getMessage());
        }

        // Clean up directory
        try {
            String userDir = BASE_HTML_DIR + "/" + containerName;
            File dir = new File(userDir);
            if (dir.exists()) {
                deleteDirectory(dir);
                System.out.println("Directory cleaned: " + userDir);
            }
        } catch (Exception e) {
            System.err.println("Error cleaning directory: " + e.getMessage());
        }
    }

    /**
     * Gets the current status of a container
     */
    public String getContainerStatus(String containerId) {
        try {
            var inspection = dockerClient.inspectContainerCmd(containerId).exec();
            if (inspection.getState() != null) {
                return inspection.getState().getStatus();
            }
            return "unknown";
        } catch (Exception e) {
            return "not_found";
        }
    }

    /**
     * Restarts a container
     */
    public void restartContainer(String containerId) {
        try {
            dockerClient.restartContainerCmd(containerId)
                    .withTimeout(10)
                    .exec();
        } catch (Exception e) {
            throw new RuntimeException("Failed to restart container: " + e.getMessage());
        }
    }

    /**
     * Finds an available port in the range 8081-9000
     */
    private int findAvailablePort() {
        Random random = new Random();
        int maxAttempts = 100;
        
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int port = 8081 + random.nextInt(920); // 8081-9000
            
            if (!isPortInUse(port)) {
                return port;
            }
        }
        
        throw new RuntimeException("No available ports found in range 8081-9000");
    }

    /**
     * Checks if a port is already in use by any container
     */
    private boolean isPortInUse(int port) {
        try {
            var containers = dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .exec();
            
            for (var container : containers) {
                if (container.getPorts() != null) {
                    for (var containerPort : container.getPorts()) {
                        if (containerPort.getPublicPort() != null && 
                            containerPort.getPublicPort() == port) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (Exception e) {
            System.err.println("Error checking port " + port + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Recursively deletes a directory and its contents
     */
    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }

    /**
     * Result object for container creation
     */
    public static class ContainerCreationResult {
        private final String containerId;
        private final String containerName;
        private final int port;
        private final String status;
        private final String imageName;

        public ContainerCreationResult(String containerId, String containerName, int port, String status, String imageName) {
            this.containerId = containerId;
            this.containerName = containerName;
            this.port = port;
            this.status = status;
            this.imageName = imageName;
        }

        public String getContainerId() { return containerId; }
        public String getContainerName() { return containerName; }
        public int getPort() { return port; }
        public String getStatus() { return status; }
        public String getImageName() { return imageName; }
    }
}