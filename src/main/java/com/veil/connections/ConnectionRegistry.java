package com.veil.connections;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// the connection registry (planned in the track doc, now real):
// holds the saved connections in memory, assigns ids on save.
// Connections live until the app restarts - persistence can be added
// later without changing this class's callers
@Component
public class ConnectionRegistry {

	private final Map<Long, SavedConnection> connections =
			new ConcurrentHashMap<>();

	private long nextId = 1;

	public synchronized SavedConnection save(SavedConnection connection) {

		connection.setId(nextId);

		connections.put(nextId, connection);
		nextId++;

		return connection;
	}

	public List<SavedConnection> findAll() {

		List<SavedConnection> all = new ArrayList<>(connections.values());

		all.sort(Comparator.comparingLong(SavedConnection::getId));

		return all;
	}

	public SavedConnection findById(long id) {
		return connections.get(id);
	}

	public boolean delete(long id) {
		return connections.remove(id) != null;
	}
}
