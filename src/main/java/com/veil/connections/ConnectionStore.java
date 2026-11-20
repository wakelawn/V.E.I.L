package com.veil.connections;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Stores saved connection settings on disk so they survive a restart. Only the safe fields are written: id, name, host, port, databaseName and username. The password is deliberately never serialized - the UI asks for it every time a connection is activated.
@Component
public class ConnectionStore {

	private record StoredConnection(long id, String name, String host,
			int port, String databaseName, String username) {
	}

	private final Path file;
	private final ObjectMapper mapper = new ObjectMapper();

	public ConnectionStore(
			@Value("${veil.connections.file:}") String configuredPath) {

		String path = configuredPath == null || configuredPath.isBlank()
				? System.getProperty("user.home") + "/.veil/connections.json"
				: configuredPath;

		this.file = Path.of(path);
	}

	public synchronized List<SavedConnection> load() {

		if (!Files.isRegularFile(file)) {
			return new ArrayList<>();
		}

		try {
			List<StoredConnection> stored = mapper.readValue(file.toFile(),
					new TypeReference<List<StoredConnection>>() {
					});

			List<SavedConnection> connections = new ArrayList<>();

			for (StoredConnection row : stored) {

				SavedConnection saved = new SavedConnection(
						row.name(), row.host(), row.port(),
						row.databaseName(), row.username(), null);

				saved.setId(row.id());
				connections.add(saved);
			}

			return connections;

		} catch (IOException | RuntimeException e) {
// a corrupt or unreadable file must never stop the application
			return new ArrayList<>();
		}
	}

	public synchronized void save(List<SavedConnection> connections) {

		try {
			Path parent = file.toAbsolutePath().getParent();

			if (parent != null) {
				Files.createDirectories(parent);
			}

			List<StoredConnection> stored = new ArrayList<>();

			for (SavedConnection connection : connections) {
				stored.add(new StoredConnection(connection.getId(),
						connection.getName(), connection.getHost(),
						connection.getPort(), connection.getDatabaseName(),
						connection.getUsername()));
			}

			Path temp = file.resolveSibling(file.getFileName() + ".tmp");

			mapper.writerWithDefaultPrettyPrinter()
					.writeValue(temp.toFile(), stored);

			try {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}

			restrictPermissions();

		} catch (IOException e) {
// persistence is best-effort: an unwritable file must not break the connection API
		}
	}

	private void restrictPermissions() {

		try {
			Files.setPosixFilePermissions(file, Set.of(
					PosixFilePermission.OWNER_READ,
					PosixFilePermission.OWNER_WRITE));
		} catch (UnsupportedOperationException | IOException ignored) {
// non-POSIX filesystems keep their default permissions
		}
	}

	public Path getFile() {
		return file;
	}
}
