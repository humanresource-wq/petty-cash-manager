package com.freestone.pettycash.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Global application properties configured under the "app" prefix in configuration properties.
 */
@Configuration
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    /**
     * List of registered companies for petty cash records.
     */
    private List<String> companies = List.of("Freestone Infotech LLP", "Freestone Infotech PVT LTD");

    /**
     * Nested configuration properties under "app.transaction".
     */
    private TransactionConfig transaction = new TransactionConfig();

    @Getter
    @Setter
    public static class TransactionConfig {
        /**
         * Threshold limit window after which transactions are locked and cannot be edited.
         */
        private EditLimit editLimit = new EditLimit();
    }

    @Getter
    @Setter
    public static class EditLimit {
        /**
         * Number of months for edit limit. Default is 1 month.
         */
        private int months = 1;

        /**
         * Number of days for edit limit. Default is 3 days.
         */
        private int days = 3;
    }
}
