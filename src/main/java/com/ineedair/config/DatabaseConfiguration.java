package com.ineedair.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class DatabaseConfiguration {
    private static final String DATA_DIRECTORY_ENV = "INEEDAIR_DATA_DIR";

    @Bean
    DataSource dataSource() throws Exception {
        Path dataDirectory = dataDirectory();
        Files.createDirectories(dataDirectory);
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:" + dataDirectory.resolve("i-need-air.db").toAbsolutePath());
        return dataSource;
    }

    /**
     * Installer directories are commonly read-only. Keep user data outside the
     * app image so upgrades and uninstalls do not remove favourites or caches.
     * INEEDAIR_DATA_DIR is useful for portable deployments and automated tests.
     */
    private Path dataDirectory() {
        String configuredDirectory = System.getenv(DATA_DIRECTORY_ENV);
        if (configuredDirectory != null && !configuredDirectory.isBlank()) {
            return Path.of(configuredDirectory);
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, "I Need Air", "data");
        }
        return Path.of(System.getProperty("user.home"), ".i-need-air", "data");
    }
}
