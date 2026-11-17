package com.veil.connections;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

// REST API for saved database connections (Phase 9, contract from
// veil-frntend-track.md):
//   GET    /api/connections           list saved connections
//   POST   /api/connections           create (validates + tests first)
//   GET    /api/connections/{id}      get one connection
//   POST   /api/connections/{id}/test re-test a saved connection
//   DELETE /api/connections/{id}      delete a saved connection
// every response goes through ConnectionView, so the password never
// leaves the registry
@RestController
@RequestMapping("/api/connections")
public class ConnectionsController {

	private final ConnectionRegistry registry;
	private final ConnectionTester tester;

	public ConnectionsController(ConnectionRegistry registry,
			ConnectionTester tester) {

		this.registry = registry;
		this.tester = tester;
	}

	@GetMapping
	public List<ConnectionView> list() {

		List<ConnectionView> views = new ArrayList<>();

		for (SavedConnection saved : registry.findAll()) {
			views.add(ConnectionView.from(saved));
		}

		return views;
	}

	@PostMapping
	public ConnectionView create(
			@RequestBody Map<String, Object> request) {

		SavedConnection saved = new SavedConnection(
				requiredText(request, "name"),
				requiredText(request, "host"),
				portFrom(request),
				requiredText(request, "databaseName"),
				requiredText(request, "username"),
				request.get("password") == null
						? "" : String.valueOf(request.get("password")));

		// never save a connection that does not work
		tester.test(saved.getHost(), saved.getPort(),
				saved.getDatabaseName(), saved.getUsername(),
				saved.getPassword());

		return ConnectionView.from(registry.save(saved));
	}

	@GetMapping("/{id}")
	public ConnectionView get(@PathVariable long id) {

		SavedConnection saved = registry.findById(id);

		if (saved == null) {
			throw new NoSuchElementException(
					"Connection " + id + " not found");
		}

		return ConnectionView.from(saved);
	}

	@PostMapping("/{id}/test")
	public Map<String, Object> test(@PathVariable long id) {

		SavedConnection saved = registry.findById(id);

		if (saved == null) {
			throw new NoSuchElementException(
					"Connection " + id + " not found");
		}

		tester.test(saved.getHost(), saved.getPort(),
				saved.getDatabaseName(), saved.getUsername(),
				saved.getPassword());

		return Map.of("tested", true, "name", saved.getName());
	}

	@DeleteMapping("/{id}")
	public Map<String, Object> delete(@PathVariable long id) {

		if (!registry.delete(id)) {
			throw new NoSuchElementException(
					"Connection " + id + " not found");
		}

		return Map.of("deleted", true, "id", id);
	}

	private String requiredText(Map<String, Object> request, String field) {

		Object value = request.get(field);

		if (value == null || String.valueOf(value).isBlank()) {
			throw new IllegalArgumentException(
					"'" + field + "' is required to create a connection");
		}

		return String.valueOf(value).trim();
	}

	// port 0 / missing in the request means "use the PostgreSQL default"
	private int portFrom(Map<String, Object> request) {

		Object value = request.get("port");

		if (value == null) {
			return 5432;
		}

		try {
			int port = Integer.parseInt(String.valueOf(value));

			if (port <= 0 || port > 65535) {
				throw new IllegalArgumentException(
						"port must be between 1 and 65535");
			}

			return port;

		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(
					"port must be a number");
		}
	}
}
