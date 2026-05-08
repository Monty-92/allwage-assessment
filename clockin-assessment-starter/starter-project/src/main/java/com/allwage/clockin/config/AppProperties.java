package com.allwage.clockin.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly-typed binding for application properties.
 * All properties can be overridden via environment variables using Spring Boot
 * relaxed binding: APP_X_Y_Z maps to app.x.y.z
 */
@Component
@ConfigurationProperties(prefix = "app")
@Validated
public class AppProperties {

    private Geofence geofence = new Geofence();
    private Sse sse = new Sse();
    private Notifications notifications = new Notifications();
    private SeedData seedData = new SeedData();
    private Summary summary = new Summary();

    public Geofence getGeofence() { return geofence; }
    public void setGeofence(Geofence geofence) { this.geofence = geofence; }

    public Sse getSse() { return sse; }
    public void setSse(Sse sse) { this.sse = sse; }

    public Notifications getNotifications() { return notifications; }
    public void setNotifications(Notifications notifications) { this.notifications = notifications; }

    public SeedData getSeedData() { return seedData; }
    public void setSeedData(SeedData seedData) { this.seedData = seedData; }

    public Summary getSummary() { return summary; }
    public void setSummary(Summary summary) { this.summary = summary; }

    public static class Geofence {
        private int defaultToleranceMeters = 20;
        private int strictModeToleranceMeters = 5;

        public int getDefaultToleranceMeters() { return defaultToleranceMeters; }
        public void setDefaultToleranceMeters(int v) { this.defaultToleranceMeters = v; }

        public int getStrictModeToleranceMeters() { return strictModeToleranceMeters; }
        public void setStrictModeToleranceMeters(int v) { this.strictModeToleranceMeters = v; }
    }

    public static class Sse {
        private long emitterTimeoutMs = 0L;
        private boolean redisEnabled = false;
        @NotBlank
        private String redisChannel = "clock-events";

        public long getEmitterTimeoutMs() { return emitterTimeoutMs; }
        public void setEmitterTimeoutMs(long v) { this.emitterTimeoutMs = v; }

        public boolean isRedisEnabled() { return redisEnabled; }
        public void setRedisEnabled(boolean redisEnabled) { this.redisEnabled = redisEnabled; }

        public String getRedisChannel() { return redisChannel; }
        public void setRedisChannel(String redisChannel) { this.redisChannel = redisChannel; }
    }

    public static class Notifications {
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class SeedData {
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class Summary {
        private boolean enabled = false;
        private String morningCron = "0 0 6 * * MON-FRI";
        private String eveningCron = "0 0 18 * * MON-FRI";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getMorningCron() { return morningCron; }
        public void setMorningCron(String morningCron) { this.morningCron = morningCron; }

        public String getEveningCron() { return eveningCron; }
        public void setEveningCron(String eveningCron) { this.eveningCron = eveningCron; }
    }
}
