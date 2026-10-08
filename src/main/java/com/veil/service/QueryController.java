package com.veil.service;

import com.veil.service.AnalyzeResponse;
import com.veil.service.QueryService;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class QueryController {

	private final QueryService queryService;

	public QueryController(QueryService queryService) {
		this.queryService = queryService;
	}

	@PostMapping("/analyze")
	public AnalyzeResponse analyze(@RequestBody Map<String, String> request) {

		String sql = request.get("sql");

		return queryService.analyze(sql);
	}
}