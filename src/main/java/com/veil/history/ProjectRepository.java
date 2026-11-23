package com.veil.history;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Project entities.
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Page<Project> findByUserId(UUID userId, Pageable pageable);

    List<Project> findByUserIdAndArchivedFalse(UUID userId);

    Optional<Project> findByUserIdAndDefaultProjectTrue(UUID userId);

    @Query("SELECT p FROM Project p WHERE p.userId = :userId AND LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Project> searchByName(@Param("userId") UUID userId, @Param("search") String search, Pageable pageable);

    Optional<Project> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    @Query("SELECT p FROM Project p WHERE p.userId = :userId AND p.archived = false ORDER BY p.defaultProject DESC, p.updatedAt DESC")
    List<Project> findByUserIdOrderByDefaultAndUpdatedDesc(@Param("userId") UUID userId);
}