package com.globalchat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
@Singleton
public class SupporterManager {
    private static final String SUPPORTERS_URL = "https://global-chat-frontend.vercel.app/api/supporters";
    private static final long REFRESH_INTERVAL_MINUTES = 60; // Reduced from 10 to 60 minutes
    private static final long RETRY_DELAY_SECONDS = 30;
    private static final int MAX_RETRIES = 3;

    private final Gson gson;
    private final OkHttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private volatile List<Supporter> supporters = new ArrayList<>();
    private volatile int totalSupport = 0;
    private volatile String lastUpdated = "";
    private volatile int consecutiveFailures = 0;
    private volatile long lastFetchAttempt = 0;

    @Inject
    public SupporterManager(Gson gson, OkHttpClient httpClient) {
        this.gson = gson;
        this.httpClient = httpClient;
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
        // Rate limit: Don't fetch more than once per minute
        long now = System.currentTimeMillis();
        if (now - lastFetchAttempt < 60000) {
            log.debug("[SUPPORTERS] Skipping fetch - too soon since last attempt");
            return;
        }
        lastFetchAttempt = now;
        
        // Exponential backoff if we've had failures
        if (consecutiveFailures > 0) {
            long backoffTime = Math.min(300000, RETRY_DELAY_SECONDS * 1000 * (long)Math.pow(2, consecutiveFailures - 1));
            if (now - lastFetchAttempt < backoffTime) {
                log.debug("[SUPPORTERS] In backoff period after {} failures", consecutiveFailures);
                return;
            }
        }
        
        log.debug("[SUPPORTERS] Fetching supporter list");
        
        Request request = new Request.Builder()
                .url(SUPPORTERS_URL)
                .build();

        httpClient.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                consecutiveFailures++;
                log.debug("[SUPPORTERS] Error fetching supporters (failure #{})", consecutiveFailures, e);
                
                // After MAX_RETRIES failures, back off for longer
                if (consecutiveFailures >= MAX_RETRIES) {
                    log.debug("[SUPPORTERS] Max retries reached, will retry in next scheduled interval");
                }
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws java.io.IOException {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        parseSupportersResponse(responseBody);
                        consecutiveFailures = 0; // Reset on success
                        log.debug("[SUPPORTERS] Successfully fetched {} supporters", supporters.size());
                    } else {
                        consecutiveFailures++;
                        log.debug("[SUPPORTERS] Failed to fetch supporters: HTTP {} (failure #{})", response.code(), consecutiveFailures);
                    }
                } finally {
                    response.close();
                }
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