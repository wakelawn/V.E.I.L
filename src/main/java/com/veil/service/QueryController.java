package com.veil.service;

import com.veil.security.SqlValidator;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class QueryController {

	private final QueryService queryService;
	private final SqlValidator sqlValidator;

	public QueryController(QueryService queryService,
                           SqlValidator sqlValidator) {
		this.queryService = queryService;
		this.sqlValidator = sqlValidator;
	}

	@PostMapping("/analyze")
	public AnalyzeResponse analyze(@RequestBody Map<String, String> request) {

		String sql = request == null ? null : request.get("sql");

		// Validate before parsing: SELECT/WITH only, one statement,
		// bounded length.
		sqlValidator.validateSelectOnly(sql);

		return queryService.analyze(sql);
	}
}
