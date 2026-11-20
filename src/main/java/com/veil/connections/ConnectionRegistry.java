package com.veil.connections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Holds the saved connections, assigns ids on save, and persists the safe part (everything except the password) through ConnectionStore. The no-argument constructor is kept for unit tests: it behaves like the original in-memory registry with no disk access.
@Component
public class ConnectionRegistry {

	private final Map<Long, SavedConnection> connections =
			new ConcurrentHashMap<>();

	private final ConnectionStore store;

	private long nextId = 1;

	public ConnectionRegistry() {
		this.store = null;
	}

	@Autowired
	public ConnectionRegistry(ConnectionStore store) {

		this.store = store;

		restore();
	}

	private void restore() {

		for (SavedConnection saved : store.load()) {

			connections.put(saved.getId(), saved);

			if (saved.getId() >= nextId) {
				nextId = saved.getId() + 1;
			}
		}
	}

	public synchronized SavedConnection save(SavedConnection connection) {

		connection.setId(nextId);

		connections.put(nextId, connection);
		nextId++;

		persist();

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

		boolean removed = connections.remove(id) != null;

		if (removed) {
			persist();
		}

		return removed;
	}

	private void persist() {

		if (store != null) {
			store.save(findAll());
		}
	}
}
