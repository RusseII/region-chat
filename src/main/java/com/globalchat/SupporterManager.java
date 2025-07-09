package com.globalchat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
public class SupporterManager {
    private static final String SUPPORTERS_URL = "https://global-chat-plugin.vercel.app/api/supporters";
    private static final long REFRESH_INTERVAL_MINUTES = 10;

    private final Gson gson;
    private final ScheduledExecutorService scheduler;
    private List<Supporter> supporters = new ArrayList<>();
    private int totalSupport = 0;
    private String lastUpdated = "";

    @Inject
    public SupporterManager(Gson gson) {
        this.gson = gson;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        // Initial fetch
        fetchSupporters();

        // Schedule periodic refresh
        scheduler.scheduleAtFixedRate(this::fetchSupporters,
                REFRESH_INTERVAL_MINUTES, REFRESH_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    public List<Supporter> getSupporters() {
        return new ArrayList<>(supporters);
    }

    public int getTotalSupport() {
        return totalSupport;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public boolean isSupporter(String username) {
        if (username == null)
            return false;
        return supporters.stream()
                .anyMatch(supporter -> supporter.name != null &&
                        supporter.name.equalsIgnoreCase(username.trim()));
    }

    public String getSupporterIcon(String username) {
        if (username == null)
            return "";

        return supporters.stream()
                .filter(supporter -> supporter.name != null &&
                        supporter.name.equalsIgnoreCase(username.trim()))
                .findFirst()
                .map(this::getTierIcon)
                .orElse("");
    }

    private String getTierIcon(Supporter supporter) {
        // Use RuneScape item icons for different tiers
        if (supporter.amount >= 50) {
            return "<img=314> "; // Gold
        } else if (supporter.amount >= 20) {
            return "<img=312> "; // Silver
        } else if (supporter.amount >= 5) {
            return "<img=313> "; // Bronze
        } else {
            return ""; // No icon
        }
    }

    private void fetchSupporters() {
        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL(SUPPORTERS_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(10000);

                if (connection.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    parseSupportersResponse(response.toString());
                    log.info("Successfully fetched {} supporters", supporters.size());
                } else {
                    log.warn("Failed to fetch supporters: HTTP {}", connection.getResponseCode());
                }

                connection.disconnect();
            } catch (Exception e) {
                log.error("Error fetching supporters", e);
            }
        });
    }

    private void parseSupportersResponse(String json) {
        try {
            Type responseType = new TypeToken<SupportersResponse>() {
            }.getType();
            SupportersResponse response = gson.fromJson(json, responseType);

            if (response != null && response.supporters != null) {
                this.supporters = response.supporters;
                this.totalSupport = response.totalSupport;
                this.lastUpdated = response.lastUpdated;
            }
        } catch (Exception e) {
            log.error("Error parsing supporters response", e);
        }
    }

    private static class SupportersResponse {
        public List<Supporter> supporters;
        public int totalSupport;
        public String lastUpdated;
    }
}