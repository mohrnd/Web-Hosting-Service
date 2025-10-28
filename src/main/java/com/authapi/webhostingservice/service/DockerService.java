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
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Random;

@Service
public class DockerService {

    private final DockerClient dockerClient;
    private static final String BASE_HTML_DIR = "/tmp/user-websites";

    public DockerService() {
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
        new File(BASE_HTML_DIR).mkdirs();
    }

    public ContainerCreationResult createUserContainer(String userEmail, String htmlContent) throws IOException {
        // Generate unique container name
        String containerName = "user-" + userEmail.split("@")[0].replaceAll("[^a-zA-Z0-9]", "") + "-" + System.currentTimeMillis();
        
        // Create directory for user's HTML
        String userDir = BASE_HTML_DIR + "/" + containerName;
        new File(userDir).mkdirs();
        
        // Write HTML file
        Path htmlPath = Paths.get(userDir, "index.html");
        Files.writeString(htmlPath, htmlContent);

        // Find available port (8081-9000 range)
        int port = findAvailablePort();

        // Create Dockerfile
        String dockerfile = "FROM nginx:alpine\n" +
                           "COPY index.html /usr/share/nginx/html/index.html\n" +
                           "EXPOSE 80";
        Path dockerfilePath = Paths.get(userDir, "Dockerfile");
        Files.writeString(dockerfilePath, dockerfile);

        // Build Docker image
        String imageName = containerName + ":latest";
        try {
            dockerClient.buildImageCmd()
                    .withDockerfile(new File(dockerfilePath.toString()))
                    .withBaseDirectory(new File(userDir))
                    .withTags(new java.util.HashSet<>(Arrays.asList(imageName)))
                    .start()
                    .awaitCompletion();
        } catch (InterruptedException e) {
            throw new IOException("Image build interrupted", e);
        }

        // Create and start container
        ExposedPort tcp80 = ExposedPort.tcp(80);
        Ports portBindings = new Ports();
        portBindings.bind(tcp80, Ports.Binding.bindPort(port));

        CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
                .withName(containerName)
                .withExposedPorts(tcp80)
                .withHostConfig(HostConfig.newHostConfig().withPortBindings(portBindings))
                .exec();

        dockerClient.startContainerCmd(container.getId()).exec();

        return new ContainerCreationResult(container.getId(), containerName, port, "running");
    }

    public void stopAndRemoveContainer(String containerId) {
        try {
            // Stop container
            dockerClient.stopContainerCmd(containerId)
                    .withTimeout(10)
                    .exec();
        } catch (Exception e) {
            System.err.println("Error stopping container: " + e.getMessage());
        }

        try {
            // Remove container
            dockerClient.removeContainerCmd(containerId)
                    .withForce(true)
                    .exec();
        } catch (Exception e) {
            System.err.println("Error removing container: " + e.getMessage());
        }
    }

    public String getContainerStatus(String containerId) {
        try {
            var inspection = dockerClient.inspectContainerCmd(containerId).exec();
            return inspection.getState().getStatus();
        } catch (Exception e) {
            return "not_found";
        }
    }

    private int findAvailablePort() {
        Random random = new Random();
        int port;
        int attempts = 0;
        
        do {
            port = 8081 + random.nextInt(919); // Random port between 8081-9000
            attempts++;
        } while (isPortInUse(port) && attempts < 100);
        
        if (attempts >= 100) {
            throw new RuntimeException("No available ports found");
        }
        
        return port;
    }

    private boolean isPortInUse(int port) {
        try {
            var containers = dockerClient.listContainersCmd().withShowAll(true).exec();
            for (var container : containers) {
                if (container.getPorts() != null) {
                    for (var containerPort : container.getPorts()) {
                        if (containerPort.getPublicPort() != null && containerPort.getPublicPort() == port) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static class ContainerCreationResult {
        private final String containerId;
        private final String containerName;
        private final int port;
        private final String status;

        public ContainerCreationResult(String containerId, String containerName, int port, String status) {
            this.containerId = containerId;
            this.containerName = containerName;
            this.port = port;
            this.status = status;
        }

        public String getContainerId() { return containerId; }
        public String getContainerName() { return containerName; }
        public int getPort() { return port; }
        public String getStatus() { return status; }
    }
}