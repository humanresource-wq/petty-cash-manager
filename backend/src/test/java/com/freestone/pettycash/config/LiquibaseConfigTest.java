package com.freestone.pettycash.config;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD tests for Liquibase configuration.
 * Validates that Liquibase is wired correctly and migrations run on startup.
 */
@SpringBootTest
@ActiveProfiles("test")
class LiquibaseConfigTest {

    @Autowired
    private SpringLiquibase springLiquibase;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("SpringLiquibase bean should be present in context")
    void liquibaseBeanExists() {
        assertThat(springLiquibase).isNotNull();
    }

    @Test
    @DisplayName("Liquibase changelog should reference the correct master file")
    void changelogPathIsCorrect() {
        assertThat(springLiquibase.getChangeLog())
                .isEqualTo("classpath:db/changelog/db.changelog-master.xml");
    }

    @Test
    @DisplayName("Liquibase should have created DATABASECHANGELOG tracking table")
    void databaseChangeLogTableExists() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            ResultSet rs = conn.getMetaData().getTables(null, null, "DATABASECHANGELOG", null);
            assertThat(rs.next())
                    .as("DATABASECHANGELOG table should exist after Liquibase runs")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Baseline changeset should be recorded in DATABASECHANGELOG")
    void baselineChangesetRecorded() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            ResultSet rs = conn.createStatement()
                    .executeQuery("SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID = '001-baseline'");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1))
                    .as("Baseline changeset should be recorded")
                    .isEqualTo(1);
        }
    }
}
