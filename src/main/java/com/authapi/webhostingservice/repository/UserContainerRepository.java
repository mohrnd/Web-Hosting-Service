package com.authapi.webhostingservice.repository;

import com.authapi.webhostingservice.model.UserContainer;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserContainerRepository extends MongoRepository<UserContainer, String> {
    Optional<UserContainer> findByUserEmail(String userEmail);
    Optional<UserContainer> findByContainerId(String containerId);
    boolean existsByUserEmail(String userEmail);
}