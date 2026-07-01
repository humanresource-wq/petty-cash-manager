package com.freestone.pettycash;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the Spring application context loads successfully
 * with H2 database and Liquibase migrations.
 */
@SpringBootTest
@ActiveProfiles("test")
class PettyCashApplicationTests {

    @Test
    @DisplayName("Spring context loads with H2 + Liquibase")
    void contextLoads() {
        // Context load success implies:
        // - All beans are wired correctly
        // - Liquibase migrations ran without errors
        // - H2 database is connected
    }
}
