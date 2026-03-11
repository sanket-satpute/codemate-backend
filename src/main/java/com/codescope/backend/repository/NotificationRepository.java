package com.codescope.backend.repository;

import com.codescope.backend.model.Notification;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface NotificationRepository extends ReactiveMongoRepository<Notification, String> {
    Flux<Notification> findByUserIdOrderByTimestampDesc(String userId);

    Flux<Notification> findByUserIdAndReadFalse(String userId);

    Mono<Void> deleteAllByUserId(String userId);
}
