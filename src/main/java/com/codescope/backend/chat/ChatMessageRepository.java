package com.codescope.backend.chat;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ChatMessageRepository extends ReactiveMongoRepository<ChatMessage, String> {

    /**
     * Finds all chat messages for a given project, ordered by timestamp.
     *
     * @param projectId the ID of the project
     * @return a Flux of chat messages
     */
    Flux<ChatMessage> findByProjectIdOrderByTimestampAsc(String projectId);

    /**
     * Deletes all chat messages for a given project.
     *
     * @param projectId the ID of the project
     * @return a Mono<Void> indicating completion
     */
    Mono<Void> deleteByProjectId(String projectId);
}
