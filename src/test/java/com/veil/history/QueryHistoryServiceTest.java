package com.veil.history;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class QueryHistoryServiceTest {

    @Autowired
    private QueryHistoryService service;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private QueryHistoryRepository queryRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void createAndGetQuery() {
        // Create user
        User user = new User("testuser", "test@example.com", "hashedpass");
        user = userRepository.saveAndFlush(user);

        // Create query
        QueryHistory query = new QueryHistory("Test Query", "SELECT * FROM users;", user.getId());
        query.setDescription("Test description");
        query.addTag("test");
        query.addTag("sql");

        QueryHistory created = service.createQuery(query);

        assertNotNull(created.getId());
        assertEquals("Test Query", created.getName());
        assertEquals("SELECT * FROM users;", created.getSqlText());
        assertEquals(user.getId(), created.getUserId());
        assertEquals(1, created.getVersion());
        assertTrue(created.getTags().contains("test"));
        assertTrue(created.getTags().contains("sql"));

        // Retrieve
        Optional<QueryHistory> foundQuery = service.getQuery(created.getId(), user.getId());
        assertTrue(foundQuery.isPresent());
        assertEquals(created.getId(), foundQuery.get().getId());
    }

    @Test
    void createQueryWithProject() {
        User user = new User("testuser2", "test2@example.com", "hashedpass");
        entityManager.persist(user);
        entityManager.flush();

        Project project = service.createProject(new Project("Test Project", user.getId()));

        QueryHistory query = new QueryHistory("Project Query", "SELECT 1;", user.getId());
        query.setProjectId(project.getId());

        QueryHistory created = service.createQuery(query);

        assertNotNull(created.getProjectId());
        assertEquals(project.getId(), created.getProjectId());
    }

    @Test
    void updateQuery() {
        User user = new User("testuser3", "test3@example.com", "hashedpass");
        entityManager.persist(user);
        entityManager.flush();

        QueryHistory query = new QueryHistory("Original", "SELECT 1;", user.getId());
        query = service.createQuery(query);

        QueryHistory updates = new QueryHistory();
        updates.setName("Updated Name");
        updates.setSqlText("SELECT 2;");
        updates.setDescription("Updated desc");

        QueryHistory updated = service.updateQuery(query.getId(), user.getId(), updates);

        assertEquals("Updated Name", updated.getName());
        assertEquals("SELECT 2;", updated.getSqlText());
        assertEquals("Updated desc", updated.getDescription());
    }

    @Test
    void deleteQuery() {
        User user = new User("testuser4", "test4@example.com", "hashedpass");
        entityManager.persist(user);
        entityManager.flush();

        QueryHistory query = new QueryHistory("To Delete", "SELECT 1;", user.getId());
        query = service.createQuery(query);

        service.deleteQuery(query.getId(), user.getId());

        Optional<QueryHistory> found = service.getQuery(query.getId(), user.getId());
        assertFalse(found.isPresent());
    }

    @Test
    void toggleFavorite() {
        User user = new User("testuser5", "test5@example.com", "hashedpass");
        entityManager.persist(user);
        entityManager.flush();

        QueryHistory query = new QueryHistory("Favorite Test", "SELECT 1;", user.getId());
        query = service.createQuery(query);

        assertFalse(query.isFavorite());

        service.toggleFavorite(query.getId(), user.getId());
        Optional<QueryHistory> found = service.getQuery(query.getId(), user.getId());
        assertTrue(found.get().isFavorite());

        service.toggleFavorite(query.getId(), user.getId());
        found = service.getQuery(query.getId(), user.getId());
        assertFalse(found.get().isFavorite());
    }

    @Test
    void createVersion() {
        User user = new User("testuser6", "test6@example.com", "hashedpass");
        user = userRepository.saveAndFlush(user);
        
        // Verify user is persisted
        User foundUser = userRepository.findById(user.getId()).orElse(null);
        assertNotNull(foundUser, "User should be found after saveAndFlush");
        
        QueryHistory query = new QueryHistory("Version Test", "SELECT 1;", user.getId());
        query = service.createQuery(query);

        QueryHistory v2 = service.createNewVersion(query.getId(), user.getId(), "SELECT 2;");

        assertEquals(2, v2.getVersion());
        assertEquals("SELECT 2;", v2.getSqlText());
        assertEquals(query.getId(), v2.getParentId());

        List<QueryHistory> versions = service.getVersions(query.getId(), user.getId());
        assertEquals(1, versions.size());
        assertEquals(v2.getId(), versions.get(0).getId());
    }

    @Test
    void tags() {
        User user = new User("testuser7", "test7@example.com", "hashedpass");
        entityManager.persist(user);
        entityManager.flush();

        QueryHistory query = new QueryHistory("Tag Test", "SELECT 1;", user.getId());
        query.addTag("tag1");
        query.addTag("tag2");
        query = service.createQuery(query);

        assertEquals(2, query.getTags().size());

        List<String> tags = service.getAllTags(user.getId());
        assertTrue(tags.contains("tag1"));
        assertTrue(tags.contains("tag2"));
    }

    @Test
    void searchQueries() {
        User user = new User("testuser8", "test8@example.com", "hashedpass");
        entityManager.persist(user);
        entityManager.flush();

        service.createQuery(new QueryHistory("Find Me", "SELECT 1;", user.getId()));
        service.createQuery(new QueryHistory("Don't Find", "SELECT 2;", user.getId()));
        service.createQuery(new QueryHistory("Also Find Me", "SELECT 3;", user.getId()));

        var results = service.searchQueries(user.getId(), "Find", org.springframework.data.domain.Pageable.unpaged());
        assertEquals(3, results.getTotalElements());
    }

    @Test
    void projectOperations() {
        User user = new User("testuser9", "test9@example.com", "hashedpass");
        entityManager.persist(user);
        entityManager.flush();

        Project project = new Project("My Project", user.getId());
        project.setDescription("Test project");
        project = service.createProject(project);

        assertNotNull(project.getId());
        assertEquals("My Project", project.getName());
        assertEquals("Test project", project.getDescription());
        assertTrue(project.isDefaultProject()); // First project should be default

        // Update project
        Project updates = new Project();
        updates.setName("Updated Project");
        updates.setDescription("Updated desc");
        Project updated = service.updateProject(project.getId(), user.getId(), updates);

        assertEquals("Updated Project", updated.getName());
        assertEquals("Updated desc", updated.getDescription());
    }

    @Test
    void defaultProject() {
        User user = new User("testuser10", "test10@example.com", "hashedpass");
        entityManager.persist(user);
        entityManager.flush();

        Project p1 = service.createProject(new Project("Project 1", user.getId()));
        Project p2 = service.createProject(new Project("Project 2", user.getId()));

        assertTrue(p1.isDefaultProject());
        assertFalse(p2.isDefaultProject());

        service.setDefaultProject(p2.getId(), user.getId());

        Optional<Project> defaultProject = service.getDefaultProject(user.getId());
        assertTrue(defaultProject.isPresent());
        assertEquals(p2.getId(), defaultProject.get().getId());
        assertFalse(p1.isDefaultProject());
        assertTrue(p2.isDefaultProject());
    }

    @Test
    void queryStats() {
        User user = new User("testuser11", "test11@example.com", "hashedpass");
        entityManager.persist(user);
        entityManager.flush();

        service.createQuery(new QueryHistory("Q1", "SELECT 1;", user.getId()));
        service.createQuery(new QueryHistory("Q2", "SELECT 2;", user.getId()));
        QueryHistory fav = new QueryHistory("Fav", "SELECT 3;", user.getId());
        fav.setFavorite(true);
        service.createQuery(fav);

        QueryHistoryService.QueryStats stats = service.getStats(user.getId());

        assertEquals(3, stats.totalQueries);
        assertEquals(1, stats.favoriteQueries);
    }

    @Test
    void queryByProject() {
        User user = new User("testuser12", "test12@example.com", "hashedpass");
        user = userRepository.saveAndFlush(user);

        Project project = service.createProject(new Project("Test Project", user.getId()));
        entityManager.flush();

        service.createQuery(new QueryHistory("In Project", "SELECT 1;", user.getId()));
        QueryHistory inProject = new QueryHistory("In Project", "SELECT 2;", user.getId());
        inProject.setProjectId(project.getId());
        service.createQuery(inProject);

        var results = service.getQueriesByProject(user.getId(), project.getId(), org.springframework.data.domain.Pageable.unpaged());
        assertEquals(1, results.getTotalElements());
        assertEquals(inProject.getId(), results.getContent().get(0).getId());
    }

    @Test
    void resolveUserCreatesOnceAndReusesCaseInsensitively() {
        User first = service.resolveUser("Owner@Example.com");
        assertNotNull(first.getId());
        assertEquals("owner@example.com", first.getEmail());

        User second = service.resolveUser("owner@example.com");
        assertEquals(first.getId(), second.getId());
        assertEquals(second.getId(), service.resolveUserId("OWNER@EXAMPLE.COM"));

        assertEquals(1, userRepository.count());
    }

    @Test
    void resolveUserRejectsBlankEmail() {
        assertThrows(IllegalArgumentException.class, () -> service.resolveUser("  "));
    }

    @Test
    void deleteProjectUnassignsQueries() {
        User user = new User("testuser13", "test13@example.com", "hashedpass");
        user = userRepository.saveAndFlush(user);

        Project project = service.createProject(new Project("To Delete", user.getId()));
        entityManager.flush();

        QueryHistory query = new QueryHistory("In Deleted Project", "SELECT 1;", user.getId());
        query.setProjectId(project.getId());
        query = service.createQuery(query);

        service.deleteProject(project.getId(), user.getId());

        Optional<QueryHistory> found = service.getQuery(query.getId(), user.getId());
        assertTrue(found.isPresent());
        assertNull(found.get().getProjectId());
    }
}