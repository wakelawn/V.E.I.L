package com.veil.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Security-focused integration tests. These use MockMvc without servlet
// filters (@AutoConfigureMockMvc(addFilters = false)) so they test the
// controllers' own validation and error handling, not auth.
@SpringBootTest(properties = {
        "veil.connections.file=target/test-connections-security.json",
        "veil.ai.nvidia.api-key="
})
@AutoConfigureMockMvc(addFilters = false)
class SecurityHardeningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void analyzeRejectsNonSelectSql() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("sql", "DELETE FROM users"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void analyzeRejectsMultipleStatements() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("sql", "SELECT 1; SELECT 2"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void analyzeRejectsOversizedSql() throws Exception {
        String tooLong = "SELECT " + "a".repeat(70_000) + " FROM projects";
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("sql", tooLong))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void analyzeAcceptsValidSelect() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("sql", "SELECT id FROM projects WHERE id > 5;"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.table").value("projects"));
    }

    @Test
    void explainRejectsNonSelectSql() throws Exception {
        mockMvc.perform(post("/api/explain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("sql", "UPDATE projects SET name = 'x'"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void explainAnalyzeRejectsNonSelectSql() throws Exception {
        mockMvc.perform(post("/api/explain/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("sql", "DROP TABLE users"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void benchmarkRejectsNonSelectOriginalSql() throws Exception {
        mockMvc.perform(post("/api/benchmark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("originalSql", "DELETE FROM users",
                                        "optimizedSql", "SELECT 1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void benchmarkRejectsNonSelectOptimizedSql() throws Exception {
        mockMvc.perform(post("/api/benchmark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("originalSql", "SELECT 1",
                                        "optimizedSql", "UPDATE users SET x = 1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void benchmarkRejectsMissingSql() throws Exception {
        mockMvc.perform(post("/api/benchmark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("optimizedSql", "SELECT 1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void benchmarkValidatesSqlBeforeRunning() throws Exception {
        // Non-SELECT SQL must be rejected with 400 before any database work.
        mockMvc.perform(post("/api/benchmark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("originalSql", "DROP TABLE users",
                                        "optimizedSql", "SELECT 1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
