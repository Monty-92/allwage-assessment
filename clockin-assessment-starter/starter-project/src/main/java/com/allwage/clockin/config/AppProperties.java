package com.allwage.clockin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Strongly-typed binding for application properties.
 * All properties can be overridden via environment variables using Spring Boot
 * relaxed binding: APP_X_Y_Z maps to app.x.y.z
 */
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Geofence geofence = new Geofence();
    private Sse sse = new Sse();
    private Notifications notifications = new Notifications();
    private SeedData seedData = new SeedData();

    public Geofence getGeofence() { return geofence; }
    public void setGeofence(Geofence geofence) { this.geofence = geofence; }

    public Sse getSse() { return sse; }
    public void setSse(Sse sse) { this.sse = sse; }

    public Notifications getNotifications() { return notifications; }
    public void setNotifications(Notifications notifications) { this.notifications = notifications; }

    public SeedData getSeedData() { return seedData; }
    public void setSeedData(SeedData seedData) { this.seedData = seedData; }

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

        public long getEmitterTimeoutMs() { return emitterTimeoutMs; }
        public void setEmitterTimeoutMs(long v) { this.emitterTimeoutMs = v; }
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
}
