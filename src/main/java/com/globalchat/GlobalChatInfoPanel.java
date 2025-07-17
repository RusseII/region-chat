/*
 * Copyright (c) 2021, Zoinkwiz <https://github.com/Zoinkwiz>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.globalchat;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.Timer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.util.Map;
import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.api.Client;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GlobalChatInfoPanel extends PluginPanel {

    private static final String PATREON_URL = "https://patreon.com/global_chat_plugin";
    private static final String GITHUB_URL = "https://github.com/RusseII/region-chat";
    private static final String DISCORD_URL = "https://discord.gg/runelite";

    private final ConfigManager configManager;
    private final boolean developerMode;
    private final AblyManager ablyManager;
    private final SupporterManager supporterManager;
    private final Client client;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private JLabel totalUsersLabel;
    private JLabel currentWorldUsersLabel;
    private Timer userCountUpdateTimer;

    public GlobalChatInfoPanel() {
        super(false);
        this.configManager = null;
        this.developerMode = false;
        this.ablyManager = null;
        this.supporterManager = null;
        this.client = null;
        this.httpClient = new OkHttpClient();
        this.gson = new Gson();
        init();
    }

    public GlobalChatInfoPanel(boolean developerMode, AblyManager ablyManager, SupporterManager supporterManager, Client client, OkHttpClient httpClient, Gson gson) {
        super(false);
        this.configManager = null;
        this.developerMode = developerMode;
        this.ablyManager = ablyManager;
        this.supporterManager = supporterManager;
        this.client = client;
        this.httpClient = httpClient;
        this.gson = gson;
        init();
    }

    private void init() {
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(15, 15, 15, 15));
        add(createContent(), BorderLayout.CENTER);
    }

    private JPanel createContent() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTH;

        // Title
        panel.add(createTitle(), gbc);

        // User count section
        gbc.gridy++;
        gbc.insets = new Insets(15, 0, 0, 0);
        panel.add(createUserCountSection(), gbc);

        // Spacing
        gbc.gridy++;
        gbc.insets = new Insets(15, 0, 0, 0);

        // Settings button
        panel.add(createSettingsButton(), gbc);

        gbc.gridy++;
        panel.add(createSupportSection(), gbc);

        // Add supporters section if we have the manager
        if (supporterManager != null) {
            gbc.gridy++;
            panel.add(createSupportersSection(), gbc);
        }

        gbc.gridy++;
        panel.add(createLinks(), gbc);

        // Debug section if enabled
        if (developerMode && ablyManager != null) {
            gbc.gridy++;
            panel.add(createDebugSection(), gbc);
        }

        // Push everything to top
        gbc.gridy++;
        gbc.weighty = 1.0;
        panel.add(Box.createVerticalGlue(), gbc);

        return panel;
    }

    private JPanel createTitle() {
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        titlePanel.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, ColorScheme.BRAND_ORANGE),
                new EmptyBorder(5, 0, 8, 0)));

        JLabel title = new JLabel("Global Chat", SwingConstants.CENTER);
        title.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD, 18f));
        title.setForeground(ColorScheme.BRAND_ORANGE);
        titlePanel.add(title, BorderLayout.CENTER);

        return titlePanel;
    }

    private JPanel createSettingsButton() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JButton settingsBtn = new JButton("Open Plugin Settings");
        settingsBtn.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD, 12f));
        settingsBtn.setForeground(Color.WHITE);
        settingsBtn.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
        settingsBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR.darker(), 1),
                new EmptyBorder(10, 20, 10, 20)));
        settingsBtn.setFocusPainted(false);
        settingsBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Add hover effect
        settingsBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                settingsBtn.setBackground(ColorScheme.MEDIUM_GRAY_COLOR.brighter());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                settingsBtn.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
            }
        });

        settingsBtn.addActionListener(e -> {
            // Show helpful instructions since we can't directly open settings
            javax.swing.JOptionPane.showMessageDialog(
                    this,
                    "To access plugin settings:\n\n" +
                            "1. Click the Settings button in RuneLite's sidebar\n" +
                            "2. Search for 'Global Chat' or scroll to find it\n" +
                            "3. Configure your preferences there\n\n" +
                            "Available settings:\n" +
                            "- Read-Only Mode\n" +
                            "- Combat Level Filter",
                    "Plugin Settings Location",
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
        });

        panel.add(settingsBtn, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createSupportSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1),
                new EmptyBorder(15, 15, 15, 15)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Title
        JLabel titleLabel = new JLabel("Keep Global Chat Running!");
        titleLabel.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD, 14f));
        titleLabel.setForeground(ColorScheme.BRAND_ORANGE);
        gbc.insets = new Insets(0, 0, 12, 0);
        panel.add(titleLabel, gbc);
        gbc.gridy++;

        // Warning text
        JLabel warningText = new JLabel(
                "<html><b style='color: #ff6b6b;'>WARNING: Service will go offline when limits are reached!</b></html>");
        warningText.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD, 12f));
        gbc.insets = new Insets(0, 0, 10, 0);
        panel.add(warningText, gbc);
        gbc.gridy++;

        // Current status
        JLabel currentStatus = new JLabel("<html>" +
                "<b>Current Status and Limits:</b><br>" +
                "- <b>Connection limit:</b> 200 concurrent users<br>" +
                "- <b>Message limit:</b> 6 million per month<br>" +
                "- <b>Channel limit:</b> 200 active channels" +
                "</html>");
        currentStatus.setFont(FontManager.getRunescapeFont().deriveFont(11f));
        currentStatus.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        gbc.insets = new Insets(0, 0, 15, 0);
        panel.add(currentStatus, gbc);
        gbc.gridy++;

        // Benefits text
        JLabel benefitsText = new JLabel("<html>" +
                "<b style='color: #4CAF50;'>Your support will:</b><br>" +
                "- Increase connection limits (more players can chat)<br>" +
                "- Increase message limits (no more outages)<br>" +
                "- Keep the service running 24/7<br>" +
                "- Enable new features and improvements" +
                "</html>");
        benefitsText.setFont(FontManager.getRunescapeFont().deriveFont(11f));
        benefitsText.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        gbc.insets = new Insets(0, 0, 15, 0);
        panel.add(benefitsText, gbc);
        gbc.gridy++;

        // Patreon button
        JButton patreonBtn = new JButton("Support on Patreon");
        patreonBtn.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD, 12f));
        patreonBtn.setForeground(Color.WHITE);
        patreonBtn.setBackground(ColorScheme.BRAND_ORANGE);
        patreonBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE.darker(), 1),
                new EmptyBorder(12, 20, 12, 20)));
        patreonBtn.setFocusPainted(false);
        patreonBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        patreonBtn.addActionListener(e -> openURL(PATREON_URL));
        
        // Add hover effect
        patreonBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                patreonBtn.setBackground(ColorScheme.BRAND_ORANGE.brighter());
            }
            @Override
            public void mouseExited(MouseEvent e) {
                patreonBtn.setBackground(ColorScheme.BRAND_ORANGE);
            }
        });
        
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(patreonBtn, gbc);

        return panel;
    }

    private JPanel createSupportersSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1),
                new EmptyBorder(15, 15, 15, 15)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Title
        JLabel titleLabel = new JLabel("Amazing Supporters");
        titleLabel.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD, 14f));
        titleLabel.setForeground(ColorScheme.BRAND_ORANGE);
        gbc.insets = new Insets(0, 0, 12, 0);
        panel.add(titleLabel, gbc);
        gbc.gridy++;

        List<Supporter> supporters = supporterManager.getSupporters();

        if (supporters.isEmpty()) {
            JLabel noSupportersLabel = new JLabel("<html><i>No supporters yet - be the first!</i></html>");
            noSupportersLabel.setFont(FontManager.getRunescapeFont().deriveFont(11f));
            noSupportersLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            gbc.insets = new Insets(0, 0, 0, 0);
            panel.add(noSupportersLabel, gbc);
        } else {
            // Display supporters by tier
            for (int i = 0; i < supporters.size(); i++) {
                Supporter supporter = supporters.get(i);
                JPanel supporterPanel = createSupporterPanel(supporter);
                gbc.insets = new Insets(0, 0, i == supporters.size() - 1 ? 0 : 4, 0);
                panel.add(supporterPanel, gbc);
                gbc.gridy++;
            }
        }

        return panel;
    }

    private JPanel createSupporterPanel(Supporter supporter) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(new CompoundBorder(
            BorderFactory.createLineBorder(getTierColor(supporter.tier), 1),
            new EmptyBorder(8, 12, 8, 12)));

        JLabel nameLabel = new JLabel(supporter.getDisplayName());
        nameLabel.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD, 11f));
        nameLabel.setForeground(Color.WHITE);

        JLabel tierLabel = new JLabel(supporter.getTierDisplay());
        tierLabel.setFont(FontManager.getRunescapeFont().deriveFont(10f));
        tierLabel.setForeground(getTierColor(supporter.tier));

        JPanel textPanel = new JPanel(new BorderLayout());
        textPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        textPanel.add(nameLabel, BorderLayout.NORTH);
        textPanel.add(tierLabel, BorderLayout.SOUTH);

        // Add tier badge
        JLabel badge = new JLabel(getTierBadge(supporter.tier));
        badge.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD, 10f));
        badge.setForeground(getTierColor(supporter.tier));

        panel.add(textPanel, BorderLayout.CENTER);
        panel.add(badge, BorderLayout.EAST);

        return panel;
    }

    private Color getTierColor(String tier) {
        switch (tier.toLowerCase()) {
            case "bronze":
                return new Color(205, 127, 50); // Bronze
            case "silver":
                return new Color(192, 192, 192); // Silver
            case "gold":
                return new Color(255, 215, 0); // Gold
            case "platinum":
                return new Color(229, 228, 226); // Platinum
            default:
                return ColorScheme.LIGHT_GRAY_COLOR;
        }
    }

    private String getTierBadge(String tier) {
        switch (tier.toLowerCase()) {
            case "bronze":
                return "★";
            case "silver":
                return "★★";
            case "gold":
                return "★★★";
            case "platinum":
                return "★★★★";
            default:
                return "★";
        }
    }

    private JPanel createLinks() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1),
                new EmptyBorder(15, 15, 15, 15)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Title
        JLabel titleLabel = new JLabel("Community & Help");
        titleLabel.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD, 14f));
        titleLabel.setForeground(ColorScheme.BRAND_ORANGE);
        gbc.insets = new Insets(0, 0, 12, 0);
        panel.add(titleLabel, gbc);
        gbc.gridy++;

        // GitHub button
        JButton githubBtn = createStyledButton("View on GitHub", ColorScheme.MEDIUM_GRAY_COLOR);
        githubBtn.addActionListener(e -> openURL(GITHUB_URL));
        gbc.insets = new Insets(0, 0, 8, 0);
        panel.add(githubBtn, gbc);
        gbc.gridy++;

        // Issues button
        JButton issuesBtn = createStyledButton("Report Issues", ColorScheme.MEDIUM_GRAY_COLOR);
        issuesBtn.addActionListener(e -> openURL(GITHUB_URL + "/issues"));
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(issuesBtn, gbc);

        return panel;
    }

    private JPanel createDebugSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1),
                new EmptyBorder(15, 15, 15, 15)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Title
        JLabel titleLabel = new JLabel("Debug Tools");
        titleLabel.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD, 14f));
        titleLabel.setForeground(ColorScheme.BRAND_ORANGE);
        gbc.insets = new Insets(0, 0, 12, 0);
        panel.add(titleLabel, gbc);
        gbc.gridy++;

        // Debug info
        JLabel debugInfo = new JLabel("<html>Test error message display (developer mode only)</html>");
        debugInfo.setFont(FontManager.getRunescapeFont().deriveFont(11f));
        debugInfo.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        gbc.insets = new Insets(0, 0, 12, 0);
        panel.add(debugInfo, gbc);
        gbc.gridy++;

        // Test capacity button
        JButton testCapacityBtn = createStyledButton("Test Capacity Error", ColorScheme.MEDIUM_GRAY_COLOR);
        testCapacityBtn.addActionListener(e -> ablyManager.testCapacityError());
        gbc.insets = new Insets(0, 0, 8, 0);
        panel.add(testCapacityBtn, gbc);
        gbc.gridy++;

        // Test connection button
        JButton testConnectionBtn = createStyledButton("Test Connection Error", ColorScheme.MEDIUM_GRAY_COLOR);
        testConnectionBtn.addActionListener(e -> ablyManager.testConnectionError());
        gbc.insets = new Insets(0, 0, 8, 0);
        panel.add(testConnectionBtn, gbc);
        gbc.gridy++;
        
        // Test update button
        JButton testUpdateBtn = createStyledButton("Test Update Notification", ColorScheme.MEDIUM_GRAY_COLOR);
        testUpdateBtn.addActionListener(e -> ablyManager.testUpdateNotification());
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(testUpdateBtn, gbc);

        return panel;
    }

    private JPanel createSectionContainer(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1),
                new EmptyBorder(15, 15, 15, 15)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        if (title != null) {
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD, 14f));
            titleLabel.setForeground(ColorScheme.BRAND_ORANGE);
            gbc.insets = new Insets(0, 0, 12, 0);
            panel.add(titleLabel, gbc);
            gbc.gridy++;
        }

        return panel;
    }

    private JButton createStyledButton(String text, Color backgroundColor) {
        JButton button = new JButton(text);
        button.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD, 12f));
        button.setForeground(Color.WHITE);
        button.setBackground(backgroundColor);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(backgroundColor.darker(), 1),
                new EmptyBorder(12, 20, 12, 20)));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Add hover effect
        Color originalColor = backgroundColor;
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(originalColor.brighter());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(originalColor);
            }
        });

        return button;
    }

    private void openURL(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception e) {
            log.error("Failed to open URL: " + url, e);
        }
    }

    private JPanel createUserCountSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1),
                new EmptyBorder(15, 15, 15, 15)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Title
        JLabel titleLabel = new JLabel("Online Users");
        titleLabel.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD, 14f));
        titleLabel.setForeground(ColorScheme.BRAND_ORANGE);
        gbc.insets = new Insets(0, 0, 12, 0);
        panel.add(titleLabel, gbc);
        gbc.gridy++;

        // Total users
        totalUsersLabel = new JLabel("Total Online: Loading...");
        totalUsersLabel.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD, 12f));
        totalUsersLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        gbc.insets = new Insets(0, 0, 8, 0);
        panel.add(totalUsersLabel, gbc);
        gbc.gridy++;

        // Current world users
        currentWorldUsersLabel = new JLabel("Current World: Loading...");
        currentWorldUsersLabel.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD, 12f));
        currentWorldUsersLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(currentWorldUsersLabel, gbc);

        // Start periodic updates
        startUserCountUpdates();

        return panel;
    }

    private void startUserCountUpdates() {
        // Initial update
        updateUserCounts();
        
        // Update every 30 seconds
        userCountUpdateTimer = new Timer(30000, e -> updateUserCounts());
        userCountUpdateTimer.start();
    }

    private void updateUserCounts() {
        CompletableFuture.runAsync(() -> {
            try {
                Request request = new Request.Builder()
                    .url("https://global-chat-frontend.vercel.app/api/user-counts")
                    .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        UserCountResponse userCountResponse = gson.fromJson(responseBody, UserCountResponse.class);
                        
                        if (userCountResponse != null) {
                            // Get current world count if available
                            String currentWorldId = getCurrentWorldId();
                            int currentWorldCount = 0;
                            if (currentWorldId != null && userCountResponse.worldCounts != null) {
                                Integer worldCount = userCountResponse.worldCounts.get(currentWorldId);
                                if (worldCount != null) {
                                    currentWorldCount = worldCount;
                                }
                            }
                            
                            UserCountData data = new UserCountData(userCountResponse.totalOnline, currentWorldCount, currentWorldId);
                            
                            // Update UI on EDT
                            javax.swing.SwingUtilities.invokeLater(() -> {
                                if (data.totalOnline > 0) {
                                    totalUsersLabel.setText("Total Online: " + data.totalOnline + " players");
                                    totalUsersLabel.setForeground(new Color(76, 175, 80)); // Green
                                } else {
                                    totalUsersLabel.setText("Total Online: Unavailable");
                                    totalUsersLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                                }
                                
                                if (data.currentWorldId != null) {
                                    if (data.currentWorldCount > 0) {
                                        currentWorldUsersLabel.setText("World " + data.currentWorldId + ": " + data.currentWorldCount + " players");
                                        currentWorldUsersLabel.setForeground(new Color(76, 175, 80)); // Green
                                    } else {
                                        currentWorldUsersLabel.setText("World " + data.currentWorldId + ": 0 players");
                                        currentWorldUsersLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                                    }
                                } else {
                                    currentWorldUsersLabel.setText("Current World: Not connected");
                                    currentWorldUsersLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                                }
                            });
                        }
                    } else {
                        log.debug("Failed to fetch user counts: HTTP {}", response.code());
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to fetch user counts: {}", e.getMessage());
            }
        });
    }
    
    private String getCurrentWorldId() {
        if (client != null) {
            int worldId = client.getWorld();
            if (worldId > 0) {
                return String.valueOf(worldId);
            }
        }
        return null;
    }
    
    private static class UserCountResponse {
        @SerializedName("totalOnline")
        public int totalOnline;
        
        @SerializedName("worldCounts")
        public Map<String, Integer> worldCounts;
        
        @SerializedName("error")
        public String error;
    }
    
    private static class UserCountData {
        final int totalOnline;
        final int currentWorldCount;
        final String currentWorldId;
        
        UserCountData(int totalOnline, int currentWorldCount, String currentWorldId) {
            this.totalOnline = totalOnline;
            this.currentWorldCount = currentWorldCount;
            this.currentWorldId = currentWorldId;
        }
    }
    
    public void cleanup() {
        if (userCountUpdateTimer != null) {
            userCountUpdateTimer.stop();
        }
    }
}