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
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import javax.swing.Box;
import javax.swing.BoxLayout;
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
import net.runelite.client.ui.components.PluginErrorPanel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GlobalChatInfoPanel extends PluginPanel {

    private static final String PATREON_URL = "https://patreon.com/global_chat_plugin";
    private static final String GITHUB_URL = "https://github.com/RusseII/region-chat";
    private static final String DISCORD_URL = "https://discord.gg/runelite";

    private final ConfigManager configManager;
    private final boolean developerMode;
    private final AblyManager ablyManager;

    public GlobalChatInfoPanel() {
        super(false);
        this.configManager = null;
        this.developerMode = false;
        this.ablyManager = null;
        init();
    }

    public GlobalChatInfoPanel(boolean developerMode, AblyManager ablyManager) {
        super(false);
        this.configManager = null;
        this.developerMode = developerMode;
        this.ablyManager = ablyManager;
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

        // Spacing
        gbc.gridy++;
        gbc.insets = new Insets(15, 0, 0, 0);

        // Settings button
        panel.add(createSettingsButton(), gbc);

        gbc.gridy++;
        panel.add(createSupportSection(), gbc);

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

        JLabel title = new JLabel("World Global Chat", SwingConstants.CENTER);
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
        JPanel panel = createSectionContainer("Keep Global Chat Running!");

        JLabel warningText = new JLabel(
                "<html><b style='color: #ff6b6b;'>WARNING: Service will go offline when limits are reached!</b></html>");
        warningText.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD, 12f));
        warningText.setBorder(new EmptyBorder(0, 0, 8, 0));
        panel.add(warningText);

        JLabel currentStatus = new JLabel("<html>" +
                "<b>Current Status and Limits:</b><br>" +
                "- <b>Connection limit:</b> 200 concurrent users<br>" +
                "- <b>Message limit:</b> 6 million per month<br>" +
                "- <b>Channel limit:</b> 200 active channels" +
                "</html>");
        currentStatus.setFont(FontManager.getRunescapeFont().deriveFont(11f));
        currentStatus.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        currentStatus.setBorder(new EmptyBorder(0, 0, 12, 0));
        panel.add(currentStatus);

        JLabel benefitsText = new JLabel("<html>" +
                "<b style='color: #4CAF50;'>Your support will:</b><br>" +
                "- Increase connection limits (more players can chat)<br>" +
                "- Increase message limits (no more outages)<br>" +
                "- Keep the service running 24/7<br>" +
                "- Enable new features and improvements" +
                "</html>");
        benefitsText.setFont(FontManager.getRunescapeFont().deriveFont(11f));
        benefitsText.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        benefitsText.setBorder(new EmptyBorder(0, 0, 12, 0));
        panel.add(benefitsText);

        JButton patreonBtn = createFullWidthButton("Support on Patreon", ColorScheme.BRAND_ORANGE);
        patreonBtn.addActionListener(e -> openURL(PATREON_URL));
        panel.add(patreonBtn);

        return panel;
    }

    private JPanel createLinks() {
        JPanel panel = createSectionContainer("Community & Help");

        JButton githubBtn = createFullWidthButton("View on GitHub", ColorScheme.MEDIUM_GRAY_COLOR);
        githubBtn.addActionListener(e -> openURL(GITHUB_URL));
        panel.add(githubBtn);

        panel.add(Box.createRigidArea(new Dimension(0, 8)));

        JButton issuesBtn = createFullWidthButton("Report Issues", ColorScheme.MEDIUM_GRAY_COLOR);
        issuesBtn.addActionListener(e -> openURL(GITHUB_URL + "/issues"));
        panel.add(issuesBtn);

        return panel;
    }

    private JPanel createDebugSection() {
        JPanel panel = createSectionContainer("Debug Tools");

        JLabel debugInfo = new JLabel("<html>Test error message display (developer mode only)</html>");
        debugInfo.setFont(FontManager.getRunescapeFont().deriveFont(11f));
        debugInfo.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        debugInfo.setBorder(new EmptyBorder(0, 0, 12, 0));
        panel.add(debugInfo);

        JButton testCapacityBtn = createFullWidthButton("Test Capacity Error", ColorScheme.MEDIUM_GRAY_COLOR);
        testCapacityBtn.addActionListener(e -> ablyManager.testCapacityError());
        panel.add(testCapacityBtn);

        panel.add(Box.createRigidArea(new Dimension(0, 8)));

        JButton testConnectionBtn = createFullWidthButton("Test Connection Error", ColorScheme.MEDIUM_GRAY_COLOR);
        testConnectionBtn.addActionListener(e -> ablyManager.testConnectionError());
        panel.add(testConnectionBtn);
        
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
        
        JButton testUpdateBtn = createFullWidthButton("Test Update Notification", ColorScheme.MEDIUM_GRAY_COLOR);
        testUpdateBtn.addActionListener(e -> ablyManager.testUpdateNotification());
        panel.add(testUpdateBtn);

        return panel;
    }

    private JPanel createSectionContainer(String title) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1),
                new EmptyBorder(15, 15, 15, 15)));

        if (title != null) {
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD, 14f));
            titleLabel.setForeground(ColorScheme.BRAND_ORANGE);
            titleLabel.setBorder(new EmptyBorder(0, 0, 12, 0));
            panel.add(titleLabel);
        }

        return panel;
    }

    private JButton createFullWidthButton(String text, Color backgroundColor) {
        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

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
}