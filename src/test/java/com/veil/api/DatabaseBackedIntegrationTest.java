package com.veil.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// integration tests that need the REAL local database, because they
// verify things only a database can answer: real EXPLAIN plans and real
// index checks. They only run when DB_PASSWORD is provided:
//
//   DB_PASSWORD=... sh mvnw test
//
// without it they are skipped (not failed) - plain `sh mvnw test`
// still runs everything else green
@SpringBootTest(properties = "veil.connections.file=target/test-connections.json")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class DatabaseBackedIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void explainReturnsPlanWithCostBreakdown() throws Exception {

		mockMvc.perform(post("/api/explain")
						.contentType("application/json")
						.content("{\"sql\":\"SELECT q.id FROM queries q "
								+ "JOIN projects p ON q.project_id = p.id;\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.plan.nodeType").exists())
				.andExpect(jsonPath("$.plan.planNodeCount").isNumber())
				.andExpect(jsonPath("$.plan.costBreakdown").isArray())
				.andExpect(jsonPath("$.plan.planSummary").value(
						org.hamcrest.Matchers.containsString("Cost breakdown")));
	}

	@Test
	void indexAdvisorConfirmsPrimaryKeyIsCovered() throws Exception {

		// projects.id is the primary key: the FILTER_INDEX hint must be
		// verified down to a low "already indexed" note, no suggestion
		mockMvc.perform(post("/api/analyze")
						.contentType("application/json")
						.content("{\"sql\":\"SELECT id FROM projects "
								+ "WHERE id > 5;\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.findings[?(@.ruleId == 'FILTER_INDEX')].severity")
						.value("low"))
				.andExpect(jsonPath("$.findings[?(@.ruleId == 'FILTER_INDEX')].suggestionSql")
						.value((Object) null));
	}

	@Test
	void indexAdvisorSuggestsIndexForUncoveredColumn() throws Exception {

		// queries has no index leading with status: expect a real,
		// verified CREATE INDEX suggestion
		mockMvc.perform(post("/api/analyze")
						.contentType("application/json")
						.content("{\"sql\":\"SELECT id FROM queries "
								+ "WHERE status = 'done';\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.findings[?(@.ruleId == 'FILTER_INDEX')].suggestionSql")
						.value("CREATE INDEX idx_queries_status ON queries (status);"));
	}

	@Test
	void connectionLifecycleCreateTestDelete() throws Exception {

		String password = System.getenv("DB_PASSWORD");

		// 1. create (the tester proves the credentials work)
		MvcResult created = mockMvc.perform(post("/api/connections")
						.contentType("application/json")
						.content("{\"name\":\"integration test\",\"host\":\"localhost\","
								+ "\"databaseName\":\"v.e.i.l\",\"username\":\"postgres\","
								+ "\"password\":\"" + password + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").isNumber())
				// the response must never contain the password
				.andExpect(jsonPath("$.password").doesNotExist())
				.andReturn();

		int id = Integer.parseInt(created.getResponse().getContentAsString()
				.replaceAll(".*\"id\":(\\d+).*", "$1"));

		// 2. re-test the saved connection with its stored password
		mockMvc.perform(post("/api/connections/" + id + "/test"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tested").value(true));

		// 3. the list now contains it
		mockMvc.perform(get("/api/connections"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == 'integration test')]").exists());

		// 4. delete it
		mockMvc.perform(delete("/api/connections/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deleted").value(true));

		// 5. it is gone (and the registry is clean for the next run)
		mockMvc.perform(get("/api/connections/" + id))
				.andExpect(status().isNotFound());
	}
}
