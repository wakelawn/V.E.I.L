package com.veil.explain;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/explain")
public class ExplainController {

	private final ExplainService explainService;

	public ExplainController(ExplainService explainService) {

		this.explainService = explainService;
	}

	@PostMapping
	public Map<String, Object> explain(
			@RequestBody Map<String, String> request) {

		String sql = request.get("sql");

		PlanSummary plan = explainService.explain(sql);

		return Map.of("sql", sql, "plan", plan);
	}
}