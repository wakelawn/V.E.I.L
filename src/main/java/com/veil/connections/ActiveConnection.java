package com.veil.connections;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The connection VEIL is analysing against.
 *
 * <p>A small Hikari pool is created per activation and closed when the
 * connection is switched or removed. The password lives only inside that pool,
 * never in the saved-connection registry.</p>
 */
@Component
public class ActiveConnection {

    private static final Logger log = LoggerFactory.getLogger(ActiveConnection.class);

    private final ConnectionTarget target;
    private final ConnectionSettings settings;

    private volatile SavedConnection connection;
    private volatile HikariDataSource pool;
    private volatile JdbcTemplate jdbcTemplate;

    public ActiveConnection(ConnectionTarget target, ConnectionSettings settings) {
        this.target = target;
        this.settings = settings;
    }

    public synchronized void activate(SavedConnection saved) {

        target.validate(saved.getName(), saved.getHost(), saved.getPort(),
                saved.getDatabaseName(), saved.getUsername());

        closePool();

        HikariConfig config = new HikariConfig();
        config.setPoolName("veil-target-" + saved.getId());
        config.setJdbcUrl(target.jdbcUrl(saved.getHost(), saved.getPort(),
                saved.getDatabaseName()));
        config.setUsername(saved.getUsername());
        config.setPassword(saved.getPassword() == null ? "" : saved.getPassword());

        config.setMaximumPoolSize(settings.getPoolSize());
        config.setMinimumIdle(0);
        config.setConnectionTimeout(settings.getConnectTimeoutSeconds() * 1000L);
        config.setValidationTimeout(Math.min(settings.getConnectTimeoutSeconds() * 1000L, 5000L));
        config.setIdleTimeout(60_000L);
        config.setMaxLifetime(30 * 60_000L);

        // The caller has just tested the connection; do not block activation,
        // and do not fail it when the database is briefly unavailable.
        config.setInitializationFailTimeout(-1);

        // VEIL only reads: EXPLAIN, benchmarks and differential tests are all
        // SELECTs. A read-only session means a mistake in generated SQL cannot
        // modify the user's data.
        config.setReadOnly(settings.isReadOnly());

        config.addDataSourceProperty("ApplicationName", "VEIL");
        config.addDataSourceProperty("reWriteBatchedInserts", "false");

        HikariDataSource created = new HikariDataSource(config);

        this.pool = created;
        this.jdbcTemplate = new JdbcTemplate(created);
        this.connection = saved;

        log.info("Activated connection '{}' to {}:{}/{} (pool {}, read-only {})",
                saved.getName(), saved.getHost(), saved.getPort(),
                saved.getDatabaseName(), settings.getPoolSize(), settings.isReadOnly());
    }

    public synchronized void deactivate() {
        closePool();
        this.connection = null;
        this.jdbcTemplate = null;
    }

    /** Active connection, or null when the default datasource is in use. */
    public SavedConnection get() {
        return connection;
    }

    /** JdbcTemplate for the active connection, or null. */
    public JdbcTemplate jdbcTemplate() {
        return jdbcTemplate;
    }

    public boolean isActive() {
        return jdbcTemplate != null;
    }

    @PreDestroy
    public synchronized void shutdown() {
        deactivate();
    }

    private void closePool() {
        HikariDataSource current = this.pool;
        this.pool = null;
        if (current != null) {
            current.close();
        }
    }
}
