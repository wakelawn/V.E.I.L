package com.veil.history;

import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service for managing query history and workspace operations.
 */
@Service
@Transactional
public class QueryHistoryService {

    private final QueryHistoryRepository queryRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public QueryHistoryService(QueryHistoryRepository queryRepository,
                               ProjectRepository projectRepository,
                               UserRepository userRepository,
                               EntityManager entityManager) {
        this.queryRepository = queryRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    // ========== Query History Operations ==========

    public QueryHistory createQuery(QueryHistory query) {
        validateUser(query.getUserId());

        // Fetch the user and set it on the query
        User user = userRepository.findById(query.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        query.setUser(user);

        if (query.getProjectId() != null) {
            validateProjectAccess(query.getProjectId(), query.getUserId());
            // Fetch and set the project relationship
            Project project = projectRepository.findById(query.getProjectId())
                    .orElseThrow(() -> new IllegalArgumentException("Project not found"));
            query.setProject(project);
        }
        query.setVersion(1);
        entityManager.persist(query);
        entityManager.flush();
        return query;
    }

    public Optional<QueryHistory> getQuery(UUID queryId, UUID userId) {
        return queryRepository.findByIdAndUserId(queryId, userId);
    }

    public Page<QueryHistory> getQueries(UUID userId, Pageable pageable) {
        return queryRepository.findByUserIdAndParentIdIsNull(userId, pageable);
    }

    public Page<QueryHistory> getQueriesByProject(UUID userId, UUID projectId, Pageable pageable) {
        return queryRepository.findByUserIdAndProjectId(userId, projectId, pageable);
    }

    public Page<QueryHistory> searchQueries(UUID userId, String search, Pageable pageable) {
        return queryRepository.searchByName(userId, search, pageable);
    }

    public Page<QueryHistory> findByTag(UUID userId, String tag, Pageable pageable) {
        return queryRepository.findByTag(userId, tag, pageable);
    }

    public List<String> getAllTags(UUID userId) {
        return queryRepository.findAllTagsByUserId(userId);
    }

    public QueryHistory updateQuery(UUID queryId, UUID userId, QueryHistory updates) {
        QueryHistory existing = queryRepository.findByIdAndUserId(queryId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Query not found"));

        if (updates.getName() != null) existing.setName(updates.getName());
        if (updates.getDescription() != null) existing.setDescription(updates.getDescription());
        if (updates.getSqlText() != null) existing.setSqlText(updates.getSqlText());
        if (updates.getDatabaseType() != null) existing.setDatabaseType(updates.getDatabaseType());
        if (updates.getDatabaseName() != null) existing.setDatabaseName(updates.getDatabaseName());
        if (updates.getTags() != null) existing.setTags(new ArrayList<>(updates.getTags()));
        if (updates.isFavorite() != existing.isFavorite()) existing.setFavorite(updates.isFavorite());
        if (updates.getProjectId() != null) {
            validateProjectAccess(updates.getProjectId(), userId);
            // Fetch and set the project relationship
            Project project = projectRepository.findById(updates.getProjectId())
                    .orElseThrow(() -> new IllegalArgumentException("Project not found"));
            existing.setProject(project);
        }

        return queryRepository.save(existing);
    }

    public void deleteQuery(UUID queryId, UUID userId) {
        QueryHistory query = queryRepository.findByIdAndUserId(queryId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Query not found"));
        queryRepository.delete(query);
    }

    public void toggleFavorite(UUID queryId, UUID userId) {
        QueryHistory query = queryRepository.findByIdAndUserId(queryId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Query not found"));
        query.setFavorite(!query.isFavorite());
        queryRepository.save(query);
    }

    public QueryHistory createNewVersion(UUID queryId, UUID userId, String newSql) {
        QueryHistory original = queryRepository.findByIdAndUserId(queryId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Query not found"));

        QueryHistory newVersion = original.createNewVersion(newSql);
        
        // Fetch and set the user on the new version
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        newVersion.setUser(user);
        
        entityManager.persist(newVersion);
        entityManager.flush();
        return newVersion;
    }

    public List<QueryHistory> getVersions(UUID queryId, UUID userId) {
        QueryHistory root = queryRepository.findByIdAndUserId(queryId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Query not found"));
        return queryRepository.findVersionsByParentId(root.getId());
    }

    // ========== Project Operations ==========

    public Project createProject(Project project) {
        validateUser(project.getUserId());

        // Fetch the user and set it on the project
        User user = userRepository.findById(project.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        project.setUser(user);

        // If this is the first project, make it default
        long count = projectRepository.countByUserId(project.getUserId());
        if (count == 0) {
            project.setDefaultProject(true);
        }

        entityManager.persist(project);
        entityManager.flush();
        return project;
    }

    public Optional<Project> getProject(UUID projectId, UUID userId) {
        return projectRepository.findByIdAndUserId(projectId, userId);
    }

    public Page<Project> getProjects(UUID userId, Pageable pageable) {
        return projectRepository.findByUserId(userId, pageable);
    }

    public List<Project> getProjectsOrdered(UUID userId) {
        return projectRepository.findByUserIdOrderByDefaultAndUpdatedDesc(userId);
    }

    public Optional<Project> getDefaultProject(UUID userId) {
        return projectRepository.findByUserIdAndDefaultProjectTrue(userId);
    }

    public Project updateProject(UUID projectId, UUID userId, Project updates) {
        Project existing = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        if (updates.getName() != null) existing.setName(updates.getName());
        if (updates.getDescription() != null) existing.setDescription(updates.getDescription());
        if (updates.getColor() != null) existing.setColor(updates.getColor());
        if (updates.getIcon() != null) existing.setIcon(updates.getIcon());
        if (updates.isArchived() != existing.isArchived()) existing.setArchived(updates.isArchived());

        return projectRepository.save(existing);
    }

    public void deleteProject(UUID projectId, UUID userId) {
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        // Unassign queries from this project. The owning relationship is
        // `project`; `projectId` is a read-only mirror, so clear both.
        List<QueryHistory> queries = queryRepository.findByUserIdAndProjectId(userId, projectId, org.springframework.data.domain.Pageable.unpaged()).getContent();
        for (QueryHistory query : queries) {
            query.setProject(null);
            query.setProjectId(null);
        }
        queryRepository.saveAll(queries);
        entityManager.flush();

        projectRepository.delete(project);
    }

    public void setDefaultProject(UUID projectId, UUID userId) {
        Project newDefault = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        // Unset current default
        projectRepository.findByUserIdAndDefaultProjectTrue(userId)
                .ifPresent(p -> {
                    p.setDefaultProject(false);
                    projectRepository.save(p);
                });

        newDefault.setDefaultProject(true);
        projectRepository.save(newDefault);
    }

    // ========== User Operations ==========

    public Optional<User> getUser(UUID userId) {
        return userRepository.findById(userId);
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Maps the authenticated principal (the JWT subject, an e-mail address) to a
     * local user row. The row is created on first use so the single-account
     * deployment never needs a separate registration flow.
     */
    public User resolveUser(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Authenticated user is missing");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);

        return userRepository.findByEmail(normalized).orElseGet(() -> {
            String localPart = normalized.contains("@")
                    ? normalized.substring(0, normalized.indexOf('@'))
                    : normalized;
            String username = uniqueUsername(localPart);

            User user = new User(username, normalized, "EXTERNAL_AUTH_NO_LOCAL_PASSWORD");
            user.setFullName(localPart);
            return userRepository.save(user);
        });
    }

    public UUID resolveUserId(String email) {
        return resolveUser(email).getId();
    }

    private String uniqueUsername(String base) {
        String candidate = base.isBlank() ? "veil-user" : base;
        if (!userRepository.existsByUsername(candidate)) {
            return candidate;
        }
        return candidate + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public User createUser(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }
        return userRepository.save(user);
    }

    // ========== Statistics ==========

    public QueryStats getStats(UUID userId) {
        QueryStats stats = new QueryStats();
        stats.totalQueries = queryRepository.countByUserId(userId);
        stats.favoriteQueries = queryRepository.countByUserIdAndFavoriteTrue(userId);
        stats.totalProjects = projectRepository.countByUserId(userId);
        stats.totalTags = queryRepository.findAllTagsByUserId(userId).size();
        return stats;
    }

    // ========== Validation Helpers ==========

    private void validateUser(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
    }

    private void validateProjectAccess(UUID projectId, UUID userId) {
        if (!projectRepository.existsByIdAndUserId(projectId, userId)) {
            throw new IllegalArgumentException("Project not found or access denied");
        }
    }

    // ========== DTOs ==========

    public static class QueryStats {
        public long totalQueries;
        public long favoriteQueries;
        public long totalProjects;
        public long totalTags;
    }
}