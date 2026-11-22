package com.veil.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// End-to-end checks for the real security chain: public pages, login,
// Bearer enforcement and rejection of forged tokens.
@SpringBootTest(properties = {
		"veil.connections.file=target/test-connections.json",
		"veil.ai.nvidia.api-key=",
		"veil.auth.email=security@veil.local",
		"veil.auth.password=correct-horse-battery",
		"veil.auth.jwt-secret=security-test-secret-that-is-long-enough-123"
})
@AutoConfigureMockMvc
class SecurityIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private String login() throws Exception {
		String body = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"security@veil.local\",\"password\":\"correct-horse-battery\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		JsonNode json = objectMapper.readTree(body);
		return json.get("token").asText();
	}

	@Test
	void pagesAndAssetsLoadWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/index.html")).andExpect(status().isOk());
		mockMvc.perform(get("/login.html")).andExpect(status().isOk());
		mockMvc.perform(get("/JS/auth.js")).andExpect(status().isOk());
		mockMvc.perform(get("/api/health")).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ok"));
	}

	@Test
	void protectedEndpointRejectsMissingToken() throws Exception {
		mockMvc.perform(post("/api/analyze")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"sql\":\"SELECT id FROM projects\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
	}

	@Test
	void forgedTokenIsRejected() throws Exception {
		String fabricated = "eyJhbGciOiJIUzI1NiJ9."
				+ "eyJpc3MiOiJ2ZWlsIiwic3ViIjoiaGFja2VyQHZlaWwuY29tIiwiZXhwIjo5OTk5OTk5OTk5fQ."
				+ "not-a-valid-signature";

		mockMvc.perform(post("/api/analyze")
						.header("Authorization", "Bearer " + fabricated)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"sql\":\"SELECT id FROM projects\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void loginIssuesTokenThatUnlocksTheApi() throws Exception {
		String token = login();

		mockMvc.perform(get("/api/auth/me")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.authenticated").value(true));
	}

	@Test
	void wrongPasswordIsRejected() throws Exception {
		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"security@veil.local\",\"password\":\"wrong\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void connectionDiscoveryRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/connections/discover")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"host\":\"localhost\",\"port\":5432,\"username\":\"postgres\",\"password\":\"x\"}"))
				.andExpect(status().isUnauthorized());
	}
}
