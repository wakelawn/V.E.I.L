package com.veil.auth;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<User> rowMapper = (rs, rowNum) -> new User(
            rs.getLong("id"),
            rs.getString("full_name"),
            rs.getString("username"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("role"),
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null
    );

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initTable() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id BIGSERIAL PRIMARY KEY,
                    full_name VARCHAR(100) NOT NULL,
                    username VARCHAR(50) UNIQUE NOT NULL,
                    email VARCHAR(100) UNIQUE NOT NULL,
                    password_hash VARCHAR(255) NOT NULL,
                    role VARCHAR(30) NOT NULL DEFAULT 'ROLE_USER',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """);
        } catch (Exception e) {
            // Logged or handled gracefully if db connection is pending
        }
    }

    public Optional<User> findByUsernameOrEmail(String identifier) {
        String sql = "SELECT id, full_name, username, email, password_hash, role, created_at FROM users WHERE username = ? OR email = ? LIMIT 1";
        return jdbcTemplate.query(sql, rowMapper, identifier, identifier).stream().findFirst();
    }

    public Optional<User> findByUsername(String username) {
        String sql = "SELECT id, full_name, username, email, password_hash, role, created_at FROM users WHERE username = ? LIMIT 1";
        return jdbcTemplate.query(sql, rowMapper, username).stream().findFirst();
    }

    public boolean existsByUsername(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, username);
        return count != null && count > 0;
    }

    public boolean existsByEmail(String email) {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, email);
        return count != null && count > 0;
    }

    public void save(String fullName, String username, String email, String passwordHash, String role) {
        String sql = "INSERT INTO users (full_name, username, email, password_hash, role) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, fullName, username, email, passwordHash, role);
    }
}
