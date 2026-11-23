package com.veil.connections;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

// REST API for saved database connections (Phase 9, extended by the multi-connection work): GET    /api/connections              list saved connections POST   /api/connections              create (validates + tests first) POST   /api/connections/discover     list databases on a server GET    /api/connections/active       which connection is in use POST   /api/connections/{id}/activate switch the analysed database POST   /api/connections/deactivate   go back to the default datasource GET    /api/connections/{id}         get one connection POST   /api/connections/{id}/test    re-test a saved connection DELETE /api/connections/{id}         delete a saved connection every response goes through ConnectionView, so the password never leaves the registry
@RestController
@RequestMapping("/api/connections")
public class ConnectionsController {

	private final ConnectionRegistry registry;
	private final ConnectionTester tester;
	private final DatabaseDiscovery discovery;
	private final ActiveConnection active;
	private final ConnectionTarget target;
	private final ConnectionSettings settings;

	public ConnectionsController(ConnectionRegistry registry,
			ConnectionTester tester, DatabaseDiscovery discovery,
			ActiveConnection active, ConnectionTarget target,
			ConnectionSettings settings) {

		this.registry = registry;
		this.tester = tester;
		this.discovery = discovery;
		this.active = active;
		this.target = target;
		this.settings = settings;
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

		String password = text(request, "password");

		if (registry.findAll().size() >= settings.getMaxSavedConnections()) {
			throw new IllegalArgumentException(
					"Only " + settings.getMaxSavedConnections()
							+ " saved connections are allowed; delete one first");
		}

		SavedConnection saved = new SavedConnection(
				requiredText(request, "name"),
				requiredText(request, "host"),
				portFrom(request),
				requiredText(request, "databaseName"),
				requiredText(request, "username"),
				password == null ? "" : password);

		target.validate(saved.getName(), saved.getHost(), saved.getPort(),
				saved.getDatabaseName(), saved.getUsername());

// never save a connection that does not work
		tester.test(saved.getHost(), saved.getPort(),
				saved.getDatabaseName(), saved.getUsername(),
				saved.getPassword());

		SavedConnection stored = registry.save(saved);

// the connection the user just added is the one they want to use
		active.activate(stored);

// the pool owns the password from here on
		registry.clearPassword(stored.getId());

		return ConnectionView.from(stored);
	}

// Lists databases on a PostgreSQL server so the Add Connection modal can offer a picker instead of making the user type a name.
	@PostMapping("/discover")
	public Map<String, Object> discover(
			@RequestBody Map<String, Object> request) {

		String host = requiredText(request, "host");
		String username = requiredText(request, "username");
		int port = portFrom(request);

		target.validateHost(host);
		target.validatePort(port);
		target.validateUsername(username);

		String password = text(request, "password");

		List<String> databases = discovery.listDatabases(
				host, port, username, password == null ? "" : password);

		Map<String, Object> body = new LinkedHashMap<>();

		body.put("host", host);
		body.put("port", port);
		body.put("databases", databases);

		return body;
	}

	@GetMapping("/active")
	public Map<String, Object> active() {

		SavedConnection saved = active.get();

		Map<String, Object> body = new LinkedHashMap<>();

		body.put("active", saved != null);

		if (saved != null) {
			body.put("connection", ConnectionView.from(saved));
		}

		return body;
	}

// Switches the database used by /api/database, /api/explain and /api/analyze. The password is supplied per activation and kept only in memory; it is never persisted.
	@PostMapping("/{id}/activate")
	public Map<String, Object> activate(@PathVariable long id,
			@RequestBody(required = false) Map<String, Object> request) {

		SavedConnection saved = findOrFail(id);

		String password = text(request, "password");

		if (password == null && saved.getPassword() == null) {
			throw new IllegalArgumentException(
					"'password' is required to activate this connection");
		}

		if (password != null) {
			saved.setPassword(password);
		}

		target.validate(saved.getName(), saved.getHost(), saved.getPort(),
				saved.getDatabaseName(), saved.getUsername());

		tester.test(saved.getHost(), saved.getPort(),
				saved.getDatabaseName(), saved.getUsername(),
				saved.getPassword());

		active.activate(saved);

// the pool owns the password from here on
		registry.clearPassword(saved.getId());

		Map<String, Object> body = new LinkedHashMap<>();

		body.put("active", true);
		body.put("connection", ConnectionView.from(saved));

		return body;
	}

	@PostMapping("/deactivate")
	public Map<String, Object> deactivate() {

		active.deactivate();

		return Map.of("active", false);
	}

	@GetMapping("/{id}")
	public ConnectionView get(@PathVariable long id) {
		return ConnectionView.from(findOrFail(id));
	}

	@PostMapping("/{id}/test")
	public Map<String, Object> test(@PathVariable long id,
			@RequestBody(required = false) Map<String, Object> request) {

		SavedConnection saved = findOrFail(id);

		String password = text(request, "password");

		if (password != null) {
			saved.setPassword(password);
		}

		if (saved.getPassword() == null) {
			throw new IllegalArgumentException(
					"'password' is required to test this connection");
		}

		target.validate(saved.getName(), saved.getHost(), saved.getPort(),
				saved.getDatabaseName(), saved.getUsername());

		tester.test(saved.getHost(), saved.getPort(),
				saved.getDatabaseName(), saved.getUsername(),
				saved.getPassword());

// keep the password out of the long-lived registry object
		registry.clearPassword(saved.getId());

		return Map.of("tested", true, "name", saved.getName());
	}

	@DeleteMapping("/{id}")
	public Map<String, Object> delete(@PathVariable long id) {

		if (registry.findById(id) == null) {
			throw new NoSuchElementException(
					"Connection " + id + " not found");
		}

// deleting the connection that is currently in use falls back to the default datasource instead of leaving a dangling template
		SavedConnection current = active.get();

		if (current != null && current.getId() == id) {
			active.deactivate();
		}

		registry.delete(id);

		return Map.of("deleted", true, "id", id);
	}

	private SavedConnection findOrFail(long id) {

		SavedConnection saved = registry.findById(id);

		if (saved == null) {
			throw new NoSuchElementException(
					"Connection " + id + " not found");
		}

		return saved;
	}

	private String requiredText(Map<String, Object> request, String field) {

		Object value = request == null ? null : request.get(field);

		if (value == null || String.valueOf(value).isBlank()) {
			throw new IllegalArgumentException(
					"'" + field + "' is required");
		}

		return String.valueOf(value).trim();
	}

	private String text(Map<String, Object> request, String field) {

		Object value = request == null ? null : request.get(field);

		return value == null ? null : String.valueOf(value);
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
