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
 * Repository for QueryHistory entities.
 */
@Repository
public interface QueryHistoryRepository extends JpaRepository<QueryHistory, UUID> {

    Page<QueryHistory> findByUserId(UUID userId, Pageable pageable);

    Page<QueryHistory> findByUserIdAndProjectId(UUID userId, UUID projectId, Pageable pageable);

    List<QueryHistory> findByUserIdAndFavoriteTrue(UUID userId);

    Page<QueryHistory> findByUserIdAndParentIdIsNull(UUID userId, Pageable pageable);

    Optional<QueryHistory> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT q FROM QueryHistory q WHERE q.userId = :userId AND LOWER(q.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<QueryHistory> searchByName(@Param("userId") UUID userId, @Param("search") String search, Pageable pageable);

    @Query("SELECT q FROM QueryHistory q WHERE q.userId = :userId AND :tag MEMBER OF q.tags")
    Page<QueryHistory> findByTag(@Param("userId") UUID userId, @Param("tag") String tag, Pageable pageable);

    @Query("SELECT DISTINCT q.tags FROM QueryHistory q WHERE q.userId = :userId")
    List<String> findAllTagsByUserId(@Param("userId") UUID userId);

    long countByUserIdAndFavoriteTrue(UUID userId);

    long countByUserId(UUID userId);

    @Query("SELECT q FROM QueryHistory q WHERE q.parentId = :parentId ORDER BY q.version ASC")
    List<QueryHistory> findVersionsByParentId(@Param("parentId") UUID parentId);

    @Query("SELECT q FROM QueryHistory q WHERE q.userId = :userId AND q.parentId IS NULL ORDER BY q.updatedAt DESC")
    List<QueryHistory> findRootQueriesByUserIdOrderByUpdatedDesc(@Param("userId") UUID userId);
}