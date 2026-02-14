package com.codescope.backend.repository;

import com.codescope.backend.model.AIResponse;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AIResponseRepository extends ReactiveMongoRepository<AIResponse, String> {
}
