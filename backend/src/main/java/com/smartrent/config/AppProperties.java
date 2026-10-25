package com.smartrent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed binding for every "smartrent.*" property (see application.yml). */
@ConfigurationProperties(prefix = "smartrent")
@Getter
@Setter
public class AppProperties {

    private String currency = "INR";
    private Seed seed = new Seed();
    private Booking booking = new Booking();
    private Jms jms = new Jms();
    private Outbox outbox = new Outbox();
    private Reports reports = new Reports();
    private Recommendation recommendation = new Recommendation();

    @Getter
    @Setter
    public static class Seed {
        private boolean enabled = true;
        private String adminPassword = "Admin@123";
        private String employeePassword = "Employee@123";
        private String customerPassword = "Customer@123";
    }

    @Getter
    @Setter
    public static class Booking {
        private int maxRentalDays = 30;
        private int maxAdvanceDays = 365;
        private int pickupDaysBeforeAllowed = 0;
    }

    @Getter
    @Setter
    public static class Jms {
        private String eventsQueue = "smartrent.booking.events";
    }

    @Getter
    @Setter
    public static class Outbox {
        private long pollIntervalMs = 1000;
        private int batchSize = 50;
        private int maxAttempts = 10;
    }

    @Getter
    @Setter
    public static class Reports {
        private String outputDir = "./reports";
        private boolean schedulerEnabled = true;
        private String scheduleCron = "0 0 2 * * *";
        private int lookbackDays = 30;
        private int chunkSize = 50;
    }

    @Getter
    @Setter
    public static class Recommendation {
        private int maxResults = 5;
        private double weightPrice = 0.35;
        private double weightCapacity = 0.20;
        private double weightLocation = 0.20;
        private double weightPreference = 0.15;
        private double weightAvailability = 0.10;
        private double maxLocationDistanceKm = 600;
    }
}
