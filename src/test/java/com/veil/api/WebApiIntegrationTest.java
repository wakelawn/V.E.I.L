package com.veil.api;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// integration tests that need NO live database. The analyze endpoint
// degrades gracefully (the IndexAdvisor catches the failed index check),
// and every error path is deterministic no matter what the database does
@SpringBootTest(properties = {
		"veil.connections.file=target/test-connections.json",
		"veil.ai.nvidia.api-key="
})
@AutoConfigureMockMvc(addFilters = false)
class WebApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void dashboardIsServed() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk());
	}

	@Test
	void analyzeReturnsFindingsAndCompatRecommendations() throws Exception {

		mockMvc.perform(post("/api/analyze")
						.contentType("application/json")
						.content("{\"sql\":\"SELECT id FROM projects WHERE id > 5;\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.table").value("projects"))
				.andExpect(jsonPath("$.findings").isArray())
				.andExpect(jsonPath("$.recommendations").isArray());
	}

	@Test
	void analyzeSyntaxErrorIsReadable400() throws Exception {

		mockMvc.perform(post("/api/analyze")
						.contentType("application/json")
						.content("{\"sql\":\"SELEKT x FRUM y\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").exists());
	}

	@Test
	void analyzeEmptyBodyIsReadable400() throws Exception {

		mockMvc.perform(post("/api/analyze")
						.contentType("application/json")
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").exists());
	}

	@Test
	void analyzeNonSelectIsReadable400() throws Exception {

		mockMvc.perform(post("/api/analyze")
						.contentType("application/json")
						.content("{\"sql\":\"DELETE FROM users;\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").exists());
	}

	@Test
	void connectionsListIsAnArray() throws Exception {

		mockMvc.perform(get("/api/connections"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray());
	}

	@Test
	void createConnectionWithMissingFieldIsReadable400() throws Exception {

		mockMvc.perform(post("/api/connections")
						.contentType("application/json")
						.content("{\"name\":\"incomplete\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").exists());
	}

	@Test
	void createConnectionWithBadCredentialsIsReadable400() throws Exception {

		// a database name that cannot exist, so this fails with or
		// without a live PostgreSQL - always a readable 400
		mockMvc.perform(post("/api/connections")
						.contentType("application/json")
						.content("{\"name\":\"bad\",\"host\":\"localhost\","
								+ "\"databaseName\":\"does_not_exist_veil_test\","
								+ "\"username\":\"postgres\",\"password\":\"wrong\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").exists());
	}

	@Test
	void unknownConnectionIsReadable404() throws Exception {

		mockMvc.perform(get("/api/connections/99999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").exists());
	}

	@Test
	void aiStatusReportsNvidiaNimWithoutExposingAKey() throws Exception {

		mockMvc.perform(get("/api/ai/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.provider").value("nvidia-nim"))
				.andExpect(jsonPath("$.configured").value(false))
				.andExpect(jsonPath("$.model").isNotEmpty())
				.andExpect(jsonPath("$.baseUrl").isNotEmpty())
				.andExpect(jsonPath("$.apiKey").doesNotExist());
	}

	@Test
	void aiRecommendWithoutConfiguredKeyIsReadable503() throws Exception {

		mockMvc.perform(post("/api/ai/recommend")
						.contentType("application/json")
						.content("{\"sql\":\"SELECT * FROM projects;\"}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error").exists());
	}

	@Test
	void aiRecommendWithoutSqlIsReadable400() throws Exception {

		mockMvc.perform(post("/api/ai/recommend")
						.contentType("application/json")
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").exists());
	}

	@Test
	void aiChatWithoutConfiguredKeyIsReadable503() throws Exception {

		mockMvc.perform(post("/api/ai/chat")
						.contentType("application/json")
						.content("{\"message\":\"Why is this query slow?\"}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error").exists());
	}
}
