package com.codescope.backend.security.util;

import com.codescope.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component("projectSecurity")
@RequiredArgsConstructor
public class ProjectSecurity {

    private final ProjectRepository projectRepository;

    public Mono<Boolean> isOwner(Authentication authentication, String projectId) {
        String ownerId = authentication.getName(); // Assuming username is the ownerId (email)
        return safeLookup(projectRepository.findByProjectIdAndOwnerId(projectId, ownerId))
                .switchIfEmpty(safeLookup(projectRepository.findByIdAndOwnerId(projectId, ownerId)))
                .hasElement();
    }

    private Mono<com.codescope.backend.project.model.Project> safeLookup(Mono<com.codescope.backend.project.model.Project> lookup) {
        return lookup != null ? lookup : Mono.empty();
    }
}
