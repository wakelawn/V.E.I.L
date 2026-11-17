package com.veil.connections;

// one saved database connection. The password is kept only in memory
// inside the registry and is NEVER sent back to the frontend -
// ConnectionView is the safe, masked shape for responses
public class SavedConnection {

	private long id;
	private String name;
	private String host;
	private int port;
	private String databaseName;
	private String username;
	private String password;

	public SavedConnection() {
	}

	public SavedConnection(String name, String host, int port,
			String databaseName, String username, String password) {

		this.name = name;
		this.host = host;
		this.port = port;
		this.databaseName = databaseName;
		this.username = username;
		this.password = password;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getDatabaseName() {
		return databaseName;
	}

	public void setDatabaseName(String databaseName) {
		this.databaseName = databaseName;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
}
