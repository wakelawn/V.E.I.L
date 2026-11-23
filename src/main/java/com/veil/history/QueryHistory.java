package com.veil.history;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a saved query with version history.
 * Each query can have multiple versions, allowing users to track changes over time.
 */
@Entity
@Table(name = "query_history", indexes = {
        @Index(name = "idx_query_history_user", columnList = "user_id"),
        @Index(name = "idx_query_history_project", columnList = "project_id"),
        @Index(name = "idx_query_history_created", columnList = "created_at")
})
public class QueryHistory {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @NotBlank
    @Column(name = "sql_text", nullable = false, columnDefinition = "TEXT")
    private String sqlText;

    @Column(name = "database_type", length = 50)
    private String databaseType;

    @Column(name = "database_name", length = 255)
    private String databaseName;

    @ElementCollection
    @CollectionTable(name = "query_history_tags", joinColumns = @JoinColumn(name = "query_id"))
    @Column(name = "tag", length = 100)
    private List<String> tags = new ArrayList<>();

    @Column(name = "is_favorite")
    private boolean favorite = false;

    @Column(name = "version")
    private int version = 1;

    @Column(name = "parent_id", insertable = false, updatable = false)
    private UUID parentId; // For version history

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    @JsonIgnore
    private Project project;

    @Column(name = "project_id", insertable = false, updatable = false)
    private UUID projectId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @JsonIgnore
    private QueryHistory parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("version ASC")
    @JsonIgnore
    private List<QueryHistory> versions = new ArrayList<>();

    // Constructors
    public QueryHistory() {}

    public QueryHistory(String name, String sqlText, UUID userId) {
        this.name = name;
        this.sqlText = sqlText;
        this.userId = userId;
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSqlText() { return sqlText; }
    public void setSqlText(String sqlText) { this.sqlText = sqlText; }

    public String getDatabaseType() { return databaseType; }
    public void setDatabaseType(String databaseType) { this.databaseType = databaseType; }

    public String getDatabaseName() { return databaseName; }
    public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public void addTag(String tag) { if (!tags.contains(tag)) tags.add(tag); }
    public void removeTag(String tag) { tags.remove(tag); }

    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }

    public QueryHistory getParent() { return parent; }
    public void setParent(QueryHistory parent) { this.parent = parent; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<QueryHistory> getVersions() { return versions; }
    public void setVersions(List<QueryHistory> versions) { this.versions = versions; }
    public void addVersion(QueryHistory version) {
        version.setParentId(this.id);
        version.setParent(this);
        version.setVersion(this.versions.size() + 1);
        this.versions.add(version);
    }

    // Helper methods
    public QueryHistory createNewVersion(String newSql) {
        QueryHistory newVersion = new QueryHistory();
        newVersion.setName(this.name);
        newVersion.setSqlText(newSql);
        newVersion.setDescription(this.description);
        newVersion.setDatabaseType(this.databaseType);
        newVersion.setDatabaseName(this.databaseName);
        newVersion.setTags(new ArrayList<>(this.tags));
        newVersion.setUserId(this.userId);
        newVersion.setProjectId(this.projectId);
        newVersion.setParentId(this.id);
        newVersion.setParent(this);
        newVersion.setVersion(this.version + 1);
        return newVersion;
    }
}