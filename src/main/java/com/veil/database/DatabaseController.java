package com.veil.database;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/database")
public class DatabaseController {

	private final DatabaseService databaseService;

	public DatabaseController(DatabaseService databaseService) {
		this.databaseService = databaseService;
	}

	@GetMapping
	public Map<String, Object> getDatabaseInfo() {

		Map<String, Object> response = new HashMap<>();

		response.put("database", databaseService.getDatabaseName());

		response.put("tables", databaseService.getTables());

		response.put("indexes", databaseService.getIndexes());

		return response;
	}
}