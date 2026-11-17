package com.veil.connections;

// the response shape of a saved connection: everything EXCEPT the
// password. Building responses from this class makes it impossible
// to leak the password by accident
public class ConnectionView {

	private long id;
	private String name;
	private String host;
	private int port;
	private String databaseName;
	private String username;

	public ConnectionView() {
	}

	public ConnectionView(long id, String name, String host, int port,
			String databaseName, String username) {

		this.id = id;
		this.name = name;
		this.host = host;
		this.port = port;
		this.databaseName = databaseName;
		this.username = username;
	}

	public static ConnectionView from(SavedConnection saved) {

		return new ConnectionView(saved.getId(), saved.getName(),
				saved.getHost(), saved.getPort(), saved.getDatabaseName(),
				saved.getUsername());
	}

	public long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public String getDatabaseName() {
		return databaseName;
	}

	public String getUsername() {
		return username;
	}
}
