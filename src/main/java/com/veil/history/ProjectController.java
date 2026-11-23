package com.veil.history;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for project/workspace operations.
 *
 * <p>The owner is always taken from the verified bearer token.</p>
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final QueryHistoryService service;

    public ProjectController(QueryHistoryService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Project> createProject(Authentication authentication,
                                                 @RequestBody Project project) {
        UUID userId = service.resolveUserId(authentication.getName());
        project.setUserId(userId);
        return ResponseEntity.ok(service.createProject(project));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Project> getProject(Authentication authentication,
                                              @PathVariable UUID id) {
        UUID userId = service.resolveUserId(authentication.getName());
        return service.getProject(id, userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<Page<Project>> getProjects(
            Authentication authentication,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID userId = service.resolveUserId(authentication.getName());
        return ResponseEntity.ok(service.getProjects(userId, pageable));
    }

    @GetMapping("/list")
    public ResponseEntity<List<Project>> getProjectsList(Authentication authentication) {
        UUID userId = service.resolveUserId(authentication.getName());
        return ResponseEntity.ok(service.getProjectsOrdered(userId));
    }

    @GetMapping("/default")
    public ResponseEntity<Project> getDefaultProject(Authentication authentication) {
        UUID userId = service.resolveUserId(authentication.getName());
        return service.getDefaultProject(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Project> updateProject(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestBody Project updates) {
        UUID userId = service.resolveUserId(authentication.getName());
        return ResponseEntity.ok(service.updateProject(id, userId, updates));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(Authentication authentication,
                                              @PathVariable UUID id) {
        UUID userId = service.resolveUserId(authentication.getName());
        service.deleteProject(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/default")
    public ResponseEntity<Project> setDefault(Authentication authentication,
                                              @PathVariable UUID id) {
        UUID userId = service.resolveUserId(authentication.getName());
        service.setDefaultProject(id, userId);
        return service.getProject(id, userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
