package com.veil.connections;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionRegistryTest {

	private final ConnectionRegistry registry = new ConnectionRegistry();

	private SavedConnection connection(String name) {

		return new SavedConnection(name, "localhost", 5432,
				"v.e.i.l", "postgres", "secret");
	}

	@Test
	void saveAssignsIncreasingIds() {

		SavedConnection first = registry.save(connection("first"));
		SavedConnection second = registry.save(connection("second"));

		assertEquals(1, first.getId());
		assertEquals(2, second.getId());
	}

	@Test
	void findAllReturnsConnectionsSortedById() {

		registry.save(connection("first"));
		registry.save(connection("second"));

		List<SavedConnection> all = registry.findAll();

		assertEquals(2, all.size());
		assertEquals("first", all.get(0).getName());
		assertEquals("second", all.get(1).getName());
	}

	@Test
	void findByIdReturnsSavedConnectionOrNull() {

		SavedConnection saved = registry.save(connection("local"));

		assertEquals("local", registry.findById(saved.getId()).getName());

		assertNull(registry.findById(999));
	}

	@Test
	void deleteRemovesOnlyThatConnection() {

		SavedConnection saved = registry.save(connection("local"));

		assertTrue(registry.delete(saved.getId()));
		assertFalse(registry.delete(saved.getId()));
		assertNull(registry.findById(saved.getId()));
		assertEquals(0, registry.findAll().size());
	}

	@Test
	void viewNeverExposesThePassword() {

		SavedConnection saved = registry.save(connection("local"));

		ConnectionView view = ConnectionView.from(saved);

		assertEquals(saved.getId(), view.getId());
		assertEquals("local", view.getName());
		assertEquals("localhost", view.getHost());
		assertEquals(5432, view.getPort());
		assertEquals("v.e.i.l", view.getDatabaseName());
		assertEquals("postgres", view.getUsername());

		// the whole point of ConnectionView: no password field at all.
		// Reflection check keeps it true if someone refactors the class
		boolean hasPasswordField = false;

		for (java.lang.reflect.Field field : view.getClass().getDeclaredFields()) {
			if (field.getName().toLowerCase().contains("password")) {
				hasPasswordField = true;
			}
		}

		assertFalse(hasPasswordField,
				"ConnectionView must never carry a password");
	}
}
