package com.veil.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Two datasources on purpose:
 *
 * <ul>
 *   <li><b>metadata</b> (primary) holds VEIL's own tables: users, projects and
 *       query history. It is a small embedded H2 database so deploying VEIL
 *       never creates or alters tables inside the database it is analysing.</li>
 *   <li><b>target</b> is the database the user asks VEIL to analyse. It is used
 *       by the JDBC template, EXPLAIN, benchmarks and differential tests.</li>
 * </ul>
 *
 * <p>Hibernate/JPA binds to the primary (metadata) datasource automatically.</p>
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource metadataDataSource(
            @Value("${veil.metadata.url:jdbc:h2:mem:veil-metadata;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}") String url,
            @Value("${veil.metadata.username:sa}") String username,
            @Value("${veil.metadata.password:}") String password) {

        createParentDirectory(url);

        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .build();
    }

    // H2 will not create missing directories for a file database.
    private void createParentDirectory(String url) {
        String prefix = "jdbc:h2:file:";
        if (url == null || !url.startsWith(prefix)) {
            return;
        }
        String path = url.substring(prefix.length());
        int options = path.indexOf(';');
        if (options >= 0) {
            path = path.substring(0, options);
        }
        java.nio.file.Path parent = java.nio.file.Paths.get(path).toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }
        try {
            java.nio.file.Files.createDirectories(parent);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot create metadata directory " + parent, e);
        }
    }

    @Bean("targetDataSource")
    public DataSource targetDataSource(Environment environment) {
        // Same keys the application already uses for the analysed database.
        return DataSourceBuilder.create()
                .url(environment.getProperty("spring.datasource.url"))
                .username(environment.getProperty("spring.datasource.username"))
                .password(environment.getProperty("spring.datasource.password"))
                .build();
    }

    /**
     * Queries, EXPLAIN and benchmarks must run against the analysed database,
     * never against VEIL's own metadata store.
     */
    @Bean
    public JdbcTemplate jdbcTemplate(@Qualifier("targetDataSource") DataSource targetDataSource) {
        return new JdbcTemplate(targetDataSource);
    }
}
