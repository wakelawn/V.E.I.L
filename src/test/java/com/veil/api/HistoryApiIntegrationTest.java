package com.veil.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.veil.config.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that query-history ownership comes from the bearer token, never
 * from a userId in the request, and that one account cannot see another's
 * saved queries.
 */
@SpringBootTest(properties = {
        "veil.connections.file=target/test-connections-history.json",
        "veil.ai.nvidia.api-key=",
        "veil.auth.email=history@veil.local",
        "veil.auth.password=history-password",
        "veil.auth.jwt-secret=history-test-secret-that-is-long-enough-123",
        "veil.metadata.url=jdbc:h2:mem:history-api-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class HistoryApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    private String login() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"history@veil.local\",\"password\":\"history-password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        return json.get("token").asText();
    }

    private long historyCount(String token) throws Exception {
        String body = mockMvc.perform(get("/api/history")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("totalElements").asLong();
    }

    @Test
    void historyRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerComesFromTokenNotRequestBody() throws Exception {
        String token = login();
        long before = historyCount(token);

        // A client-supplied userId must be ignored.
        mockMvc.perform(post("/api/history")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mine\",\"sqlText\":\"SELECT 1;\","
                                + "\"userId\":\"00000000-0000-0000-0000-000000000001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mine"))
                .andExpect(jsonPath("$.userId").isNotEmpty());

        mockMvc.perform(get("/api/history")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(before + 1))
                .andExpect(jsonPath("$.content[*].name", hasItem("Mine")));
    }

    @Test
    void otherAccountsCannotReadThisAccountsHistory() throws Exception {
        String ownerToken = login();

        mockMvc.perform(post("/api/history")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Private\",\"sqlText\":\"SELECT 2;\"}"))
                .andExpect(status().isOk());

        String otherToken = jwtService.issue("intruder@veil.local");

        mockMvc.perform(get("/api/history")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
