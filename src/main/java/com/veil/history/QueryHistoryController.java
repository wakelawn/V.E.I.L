package com.veil.history;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for query history operations.
 *
 * <p>The owner is always taken from the verified bearer token; the request body
 * can never choose whose history is read or written.</p>
 */
@RestController
@RequestMapping("/api/history")
public class QueryHistoryController {

    private final QueryHistoryService service;

    public QueryHistoryController(QueryHistoryService service) {
        this.service = service;
    }

    // ========== Query Endpoints ==========

    @PostMapping
    public ResponseEntity<QueryHistory> createQuery(Authentication authentication,
                                                    @RequestBody QueryHistory query) {
        UUID userId = service.resolveUserId(authentication.getName());
        query.setUserId(userId);
        return ResponseEntity.ok(service.createQuery(query));
    }

    @GetMapping("/{id}")
    public ResponseEntity<QueryHistory> getQuery(Authentication authentication,
                                                 @PathVariable UUID id) {
        UUID userId = service.resolveUserId(authentication.getName());
        return service.getQuery(id, userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<Page<QueryHistory>> getQueries(
            Authentication authentication,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID userId = service.resolveUserId(authentication.getName());
        return ResponseEntity.ok(service.getQueries(userId, pageable));
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<Page<QueryHistory>> getQueriesByProject(
            Authentication authentication,
            @PathVariable UUID projectId,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID userId = service.resolveUserId(authentication.getName());
        return ResponseEntity.ok(service.getQueriesByProject(userId, projectId, pageable));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<QueryHistory>> searchQueries(
            Authentication authentication,
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID userId = service.resolveUserId(authentication.getName());
        return ResponseEntity.ok(service.searchQueries(userId, q, pageable));
    }

    @GetMapping("/tag/{tag}")
    public ResponseEntity<Page<QueryHistory>> getByTag(
            Authentication authentication,
            @PathVariable String tag,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID userId = service.resolveUserId(authentication.getName());
        return ResponseEntity.ok(service.findByTag(userId, tag, pageable));
    }

    @GetMapping("/tags")
    public ResponseEntity<List<String>> getAllTags(Authentication authentication) {
        UUID userId = service.resolveUserId(authentication.getName());
        return ResponseEntity.ok(service.getAllTags(userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<QueryHistory> updateQuery(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestBody QueryHistory updates) {
        UUID userId = service.resolveUserId(authentication.getName());
        return ResponseEntity.ok(service.updateQuery(id, userId, updates));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteQuery(Authentication authentication,
                                            @PathVariable UUID id) {
        UUID userId = service.resolveUserId(authentication.getName());
        service.deleteQuery(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/favorite")
    public ResponseEntity<QueryHistory> toggleFavorite(Authentication authentication,
                                                       @PathVariable UUID id) {
        UUID userId = service.resolveUserId(authentication.getName());
        service.toggleFavorite(id, userId);
        return getQuery(authentication, id);
    }

    @PostMapping("/{id}/version")
    public ResponseEntity<QueryHistory> createVersion(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        String newSql = body.get("sql");
        if (newSql == null || newSql.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        UUID userId = service.resolveUserId(authentication.getName());
        return ResponseEntity.ok(service.createNewVersion(id, userId, newSql));
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<List<QueryHistory>> getVersions(Authentication authentication,
                                                          @PathVariable UUID id) {
        UUID userId = service.resolveUserId(authentication.getName());
        return ResponseEntity.ok(service.getVersions(id, userId));
    }

    @GetMapping("/stats")
    public ResponseEntity<QueryHistoryService.QueryStats> getStats(Authentication authentication) {
        UUID userId = service.resolveUserId(authentication.getName());
        return ResponseEntity.ok(service.getStats(userId));
    }
}
