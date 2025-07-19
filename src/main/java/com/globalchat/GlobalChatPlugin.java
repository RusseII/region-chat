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

import net.runelite.client.callback.ClientThread;

import com.google.inject.Provides;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Named;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.*;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.events.WorldChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.FriendsChatChanged;
import net.runelite.api.events.FriendsChatMemberJoined;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.MenuEntry;
import net.runelite.api.MenuAction;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@PluginDescriptor(name = "Global Chat", description = "Talk anywhere!", tags = {
		"chat" })
public class GlobalChatPlugin extends Plugin {
	@Inject
	private AblyManager ablyManager;

	@Inject
	private Client client;

	@Inject
	@Getter
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	private GlobalChatInfoPanel infoPanel;
	private NavigationButton navButton;

	@Getter
	@Setter
	private boolean shouldConnect = true;

	@Getter
	@Setter
	private String friendsChat;

	@Getter
	@Setter
	private String theClanName;

	public static final int CYCLES_PER_GAME_TICK = Constants.GAME_TICK_LENGTH / Constants.CLIENT_TICK_LENGTH;

	@Getter
	@Setter
	private String theGuesttheClanName;

	private long lastFailedSendMessageTime = 0;
	private static final long FAILED_SEND_MESSAGE_COOLDOWN = 1800000; // 30 minutes

	private long lastReconnectAttempt = 0;
	private int reconnectAttempts = 0;

	@Getter
	private final HashMap<String, ArrayList<String>> previousMessages = new HashMap<>();

	@Getter
	@Inject
	@Named("developerMode")
	private boolean developerMode;

	@Inject
	private GlobalChatConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private SupporterManager supporterManager;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	@Inject
	private MenuManager menuManager;

	private ScheduledExecutorService scheduler;

	// Tracking for chat command transformations
	private final Map<String, Long> pendingCommands = new HashMap<>();
	private final Map<String, String> commandTransformations = new HashMap<>();

	@Override
	protected void startUp() throws Exception {
		// Initialize scheduler for delayed operations
		scheduler = Executors.newSingleThreadScheduledExecutor();

		// Clear old pending commands periodically
		scheduler.scheduleAtFixedRate(() -> {
			long now = System.currentTimeMillis();
			pendingCommands.entrySet().removeIf(entry -> now - entry.getValue() > 5000); // 5 second cleanup
		}, 10, 10, TimeUnit.SECONDS);

		// Auto-reconnect mechanism - try to reconnect every 10 seconds if disconnected
		scheduler.scheduleAtFixedRate(() -> {
			try {
				// Move client data access to client thread to avoid thread safety issues
				clientThread.invokeLater(() -> {
					if (client.getGameState() == GameState.LOGGED_IN &&
							client.getLocalPlayer() != null &&
							!ablyManager.isConnected()) {

						long now = System.currentTimeMillis();

						// Implement exponential backoff to prevent spam
						long backoffTime = Math.min(60000,
								10000 * (long) Math.pow(2, Math.min(reconnectAttempts / 3, 4)));
						if (now - lastReconnectAttempt < backoffTime) {
							return true; // Skip this attempt due to backoff
						}

						String playerName = client.getLocalPlayer().getName();
						String world = String.valueOf(client.getWorld());
						if (playerName != null && !playerName.isEmpty()) {
							lastReconnectAttempt = now;
							reconnectAttempts++;

							// Only log every 3rd attempt to reduce spam
							if (reconnectAttempts % 3 == 1) {
								log.debug("Auto-reconnect attempt #{} for player: {}", reconnectAttempts, playerName);
							}

							// Execute reconnection off client thread
							scheduler.execute(() -> {
								ablyManager.startConnection(playerName);

								// Re-subscribe to channels using captured world info
								ablyManager.subscribeToCorrectChannel("p:" + playerName, world);
								ablyManager.subscribeToCorrectChannel("w:" + world, "pub");

								// Re-subscribe to friends chat if available
								clientThread.invokeLater(() -> {
									FriendsChatManager friendsChatManager = client.getFriendsChatManager();
									if (friendsChatManager != null && friendsChatManager.getOwner() != null) {
										String friendsChat = friendsChatManager.getOwner();
										ablyManager.subscribeToCorrectChannel("f:" + friendsChat, "pub");
									}
									return true;
								});
							});
						}
					} else if (ablyManager.isConnected()) {
						// Reset reconnect attempts on successful connection
						reconnectAttempts = 0;
					}
					return true;
				});
			} catch (Exception e) {
				log.debug("Error during auto-reconnect attempt", e);
			}
		}, 10, 10, TimeUnit.SECONDS);

		// ablyManager.startConnection();
		onLoggedInGameState(); // Call this to handle turning plugin on when already logged in, should do
								// nothing on initial call

		// Setup info panel
		infoPanel = new GlobalChatInfoPanel(developerMode, ablyManager, supporterManager, client, okHttpClient, gson,
				configManager);
		log.debug("Created GlobalChatInfoPanel");

		log.debug("Global Chat plugin started successfully");

		// Add player menu item for GC Status if lookup is enabled
		if (config.showPlayerLookup()) {
			menuManager.addPlayerMenuItem("GC Status");
		}

		// Create navigation button with simple icon
		navButton = NavigationButton.builder()
				.tooltip("Global Chat Info")
				.priority(0)
				.panel(infoPanel)
				.icon(createSimpleIcon())
				.build();

		clientToolbar.addNavigation(navButton);
		log.debug("Added Global Chat navigation button to toolbar");
	}

	@Override
	protected void shutDown() throws Exception {
		// Shutdown AblyManager properly
		ablyManager.shutdown();
		shouldConnect = true;

		// Clean up scheduler
		if (scheduler != null) {
			scheduler.shutdown();
		}

		// Clean up supporter manager
		if (supporterManager != null) {
			supporterManager.shutdown();
		}

		// Clean up UI panel
		if (navButton != null) {
			clientToolbar.removeNavigation(navButton);
		}

		// Clean up menu manager
		if (config.showPlayerLookup()) {
			menuManager.removePlayerMenuItem("GC Status");
		}

		// Clean up info panel
		if (infoPanel != null) {
			infoPanel.cleanup();
		}
	}

	@Subscribe
	public void onWorldChanged(WorldChanged worldChanged) {
		shouldConnect = true;

		// Force cleanup before reconnecting
		ablyManager.closeConnection();

		// Do all client data validation on client thread, then start connection
		clientThread.invokeLater(() -> {
			if (client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null) {
				String playerName = client.getLocalPlayer().getName();
				// All conditions satisfied, start connection after brief delay for cleanup
				scheduler.schedule(() -> {
					ablyManager.startConnection(playerName);
					// Refresh user counts after world change
					if (infoPanel != null) {
						infoPanel.refreshUserCounts();
					}
				}, 100, TimeUnit.MILLISECONDS);
			}
			return true;
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.LOGGED_IN) {
			onLoggedInGameState();
		}
		if (event.getGameState() == GameState.LOGIN_SCREEN) {
			onLoggedOut();
		}
	}

	@Subscribe
	public void onFriendsChatMemberJoined(FriendsChatMemberJoined event) {
		final FriendsChatMember member = event.getMember();
		if (member == null || member.getName() == null) {
			return;
		}
		String memberName = member.getName().replace('\u00A0', ' ').trim();
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null || localPlayer.getName() == null) {
			return;
		}
		String playerName = localPlayer.getName().replace('\u00A0', ' ').trim();

		Boolean isCurrentUser = memberName.equals(playerName);

		if (isCurrentUser) {
			FriendsChatManager friendsChatManager = client.getFriendsChatManager();
			if (friendsChatManager != null) {
				friendsChat = friendsChatManager.getOwner();
				ablyManager.subscribeToCorrectChannel("f:" + friendsChat, "pub");
			}
		}
	}

	private void onLoggedInGameState() {
		clientThread.invokeLater(() -> {
			// Get all client data first, then execute off client thread
			if (client.getGameState() != GameState.LOGGED_IN) {
				return true;
			}

			final Player player = client.getLocalPlayer();
			if (player == null) {
				return false;
			}
			final String name = player.getName();
			if (name == null) {
				return false;
			}
			if (name.equals("") || !shouldConnect) {
				return false;
			}

			// Get all needed client data
			String world = String.valueOf(client.getWorld());

			// All conditions satisfied, execute connection logic off client thread
			scheduler.execute(() -> {
				ablyManager.startConnection(name);
				ablyManager.subscribeToCorrectChannel("p:" + name, world);
				ablyManager.subscribeToCorrectChannel("w:" + world, "pub");
				shouldConnect = false;

				// Show update notification if needed
				showUpdateNotificationIfNeeded();
			});

			return true;
		});
	}

	private void onLoggedOut() {
		clientThread.invokeLater(() -> {
			// we return true in this case as something went wrong and somehow the state
			// isn't logged in, so we don't
			// want to keep scheduling this task.
			if (client.getGameState() != GameState.LOGIN_SCREEN) {
				return true;
			}
			shouldConnect = true;
			ablyManager.closeConnection();
			// ablyManager.startConnection();

			return true;
		});
	}

	@Subscribe
	public void onClanChannelChanged(ClanChannelChanged event) {
		ClanChannel clanChannel = event.getClanChannel();
		boolean inClanNow = clanChannel != null;
		String channelName = (inClanNow && clanChannel != null) ? clanChannel.getName() : null;
		boolean isGuest = event.isGuest();

		if (!inClanNow) {
			String channelPrefix = isGuest ? "c:" + theGuesttheClanName
					: "c:" +
							theClanName;
			if (ablyManager != null) {
				ablyManager.closeSpecificChannel(channelPrefix);
			}

			if (isGuest) {
				theGuesttheClanName = null;
			} else {
				theClanName = null;
			}
		} else {
			String targetChannelName = "c:" + channelName;
			ablyManager.subscribeToCorrectChannel(targetChannelName, "pub");

			if (isGuest) {
				theGuesttheClanName = channelName;
			} else {
				theClanName = channelName;
			}
		}
	}

	@Subscribe
	public void onFriendsChatChanged(FriendsChatChanged event) {
		if (!event.isJoined()) {
			if (friendsChat != null) {
				ablyManager.closeSpecificChannel("f:" + friendsChat);
				friendsChat = null;
			}
		}
	}

	// Single method approach using scheduler to handle transformation detection
	@Subscribe
	public void onChatMessage(ChatMessage event) {
		String cleanedMessage = Text.removeTags(event.getMessage());
		String cleanedName = Text.sanitize(event.getName());
		boolean isPublic = event.getType().equals(ChatMessageType.PUBLICCHAT);

		boolean isLocalPlayerSendingMessage = cleanedName.equals(client.getLocalPlayer().getName());

		log.debug("Chat event - Type: {}, IsLocal: {}", event.getType(), isLocalPlayerSendingMessage);

		if (isPublic && isLocalPlayerSendingMessage) {
			log.debug("Processing local public message from: '{}'", cleanedName);

			// Check if this is a command
			if (cleanedMessage.matches("^![a-zA-Z]+.*")) {
				log.debug("Chat command detected: '{}'", cleanedMessage);

				// Store original message and schedule transformation check
				String originalMessage = cleanedMessage;
				pendingCommands.put(originalMessage, System.currentTimeMillis());

				// Capture MessageNode reference on client thread (safe)
				final MessageNode messageNode = event.getMessageNode();

				// Check for transformation immediately and retry on failure
				checkForTransformationWithRetry(originalMessage, cleanedName, messageNode, 1);

			} else {
				publishMessageToGlobalChat("w", cleanedMessage, cleanedName, "REGULAR_MESSAGE");
			}
		}

		handleAllGlobalMessages(event, cleanedMessage, cleanedName, isLocalPlayerSendingMessage);
	}

	private void checkForTransformationWithRetry(String originalMessage, String playerName, MessageNode messageNode,
			int attempt) {
		// Immediately check for transformation
		checkForTransformation(originalMessage, playerName, messageNode, attempt, (success) -> {
			if (!success && attempt < 2) {
				// Retry once after 150ms delay if first attempt failed
				scheduler.schedule(() -> {
					if (pendingCommands.containsKey(originalMessage)) {
						checkForTransformationWithRetry(originalMessage, playerName, messageNode, attempt + 1);
					}
				}, 150, TimeUnit.MILLISECONDS);
			}
		});
	}

	private void checkForTransformation(String originalMessage, String playerName, MessageNode messageNode,
			int attempt) {
		checkForTransformation(originalMessage, playerName, messageNode, attempt, null);
	}

	private void checkForTransformation(String originalMessage, String playerName, MessageNode messageNode,
			int attempt, java.util.function.Consumer<Boolean> callback) {
		try {
			log.debug("Checking transformation attempt #{} for command: '{}'", attempt, originalMessage);

			if (messageNode != null) {
				// Access MessageNode from client thread to ensure thread safety
				clientThread.invokeLater(() -> {
					try {
						String runeLiteMessage = messageNode.getRuneLiteFormatMessage();
						String currentMessage = runeLiteMessage != null ? Text.removeTags(runeLiteMessage)
								: originalMessage; // Fallback to original if no RuneLite message

						boolean transformationFound = !originalMessage.equals(currentMessage);
						if (transformationFound) {
							log.debug("Command transformed: '{}' -> '{}'", originalMessage, currentMessage);
							commandTransformations.put(originalMessage, currentMessage);
							// Send the transformed message (without color formatting)
							publishMessageToGlobalChat("w", currentMessage, playerName, "TRANSFORMATION_DETECTED");
							pendingCommands.remove(originalMessage);
							if (callback != null)
								callback.accept(true);
						} else if (attempt >= 2) {
							// Final attempt - no transformation found, send original
							publishMessageToGlobalChat("w", originalMessage, playerName, "COMMAND_NO_TRANSFORMATION");
							pendingCommands.remove(originalMessage);
							if (callback != null)
								callback.accept(true);
						} else {
							// No transformation yet, signal failure for retry
							if (callback != null)
								callback.accept(false);
						}
					} catch (Exception e) {
						log.error("Error accessing MessageNode for '{}': ", originalMessage, e);
						if (attempt >= 2) {
							publishMessageToGlobalChat("w", originalMessage, playerName, "MESSAGENODE_ACCESS_ERROR");
							pendingCommands.remove(originalMessage);
							if (callback != null)
								callback.accept(true);
						} else {
							if (callback != null)
								callback.accept(false);
						}
					}
					return true;
				});
			} else {
				if (attempt >= 2) {
					publishMessageToGlobalChat("w", originalMessage, playerName, "MESSAGENODE_NULL");
					pendingCommands.remove(originalMessage);
					if (callback != null)
						callback.accept(true);
				} else {
					if (callback != null)
						callback.accept(false);
				}
			}

		} catch (Exception e) {
			log.error("Error checking transformation for '{}': ", originalMessage, e);
			if (attempt >= 2) {
				publishMessageToGlobalChat("w", originalMessage, playerName, "TRANSFORMATION_CHECK_ERROR");
				pendingCommands.remove(originalMessage);
				if (callback != null)
					callback.accept(true);
			} else {
				if (callback != null)
					callback.accept(false);
			}
		}
	}

	private void publishMessageToGlobalChat(String type, String message, String playerName, String approach) {
		// Check for spam BEFORE publishing to save costs
		if (!ablyManager.shouldPublishMessage(message, playerName)) {
			return;
		}

		// Only use client thread to build the message with world data
		clientThread.invokeLater(() -> {
			try {
				String channel = type + ":" + String.valueOf(client.getWorld());
				ablyManager.shouldShowMessge(playerName, message, true);

				// Move actual publishing off client thread to background executor
				ablyManager.publishMessageAsync(type, message, channel, "", (success) -> {
					if (!success) {
						// Handle failure on client thread since UI operations are needed
						clientThread.invokeLater(() -> {
							removeGlobalChatIconFromRecentMessage(message);
							return true;
						});
					}
				});
			} catch (Exception e) {
				log.error("Error preparing message for publish: '{}'", message, e);
				// Remove global chat icon from the message since it failed to publish
				removeGlobalChatIconFromRecentMessage(message);
			}
			return true;
		});
	}

	private void handleAllGlobalMessages(ChatMessage event, String cleanedMessage, String cleanedName,
			boolean isLocalPlayerSendingMessage) {
		if (event.getType().equals(ChatMessageType.PUBLICCHAT) && isLocalPlayerSendingMessage) {
			// Handle icons for regular messages (non-commands) only
			if (!cleanedMessage.matches("^![a-zA-Z]+.*")) {
				// Modify message to include icons if not in read-only mode and connected
				if (!config.readOnlyMode() && ablyManager.isConnected()) {
					// Remove the original message
					final ChatLineBuffer lineBuffer = client.getChatLineMap().get(ChatMessageType.PUBLICCHAT.getType());
					lineBuffer.removeMessageNode(event.getMessageNode());

					// Get icons (match the format used for received messages)
					String accountIcon = getAccountIcon();
					String supporterIcon = supporterManager.getSupporterIcon(cleanedName);
					String symbol = accountIcon; // Start with account icon

					// Add supporter icon if user is a supporter
					if (!supporterIcon.isEmpty()) {
						if (symbol.isEmpty()) {
							symbol = supporterIcon;
						} else {
							symbol = supporterIcon + " " + symbol;
						}
					}

					// Add global chat icon
					symbol = "<img=19> " + symbol;

					// Re-add the message with icons
					client.addChatMessage(ChatMessageType.PUBLICCHAT, symbol + cleanedName, cleanedMessage, null);
				}
			}
		} else if (event.getType().equals(ChatMessageType.PRIVATECHAT)
				&& !ablyManager.shouldShowMessge(cleanedName, cleanedMessage, true)) {
			final ChatLineBuffer lineBuffer = client.getChatLineMap()
					.get(ChatMessageType.PRIVATECHAT.getType());
			lineBuffer.removeMessageNode(event.getMessageNode());
			// } else if (event.getType().equals(ChatMessageType.PRIVATECHATOUT)) {
			// if (cleanedName != null && !cleanedName.isEmpty()) {
			// if (!ablyManager.shouldPublishMessage(cleanedMessage,
			// client.getLocalPlayer().getName())) {
			// return;
			// }
			// ablyManager.shouldShowMessge(client.getLocalPlayer().getName(),
			// cleanedMessage, true);
			// ablyManager.publishMessage("p", cleanedMessage, "p:" + cleanedName,
			// cleanedName);
			// }
		} else if (event.getType().equals(ChatMessageType.FRIENDSCHAT) && !isLocalPlayerSendingMessage
				&& !ablyManager.shouldShowMessge(cleanedName, cleanedMessage, true)) {
			final ChatLineBuffer lineBuffer = client.getChatLineMap()
					.get(ChatMessageType.FRIENDSCHAT.getType());
			lineBuffer.removeMessageNode(event.getMessageNode());
		} else if (event.getType().equals(ChatMessageType.CLAN_CHAT) && !isLocalPlayerSendingMessage
				&& !ablyManager.shouldShowMessge(cleanedName, cleanedMessage, true)) {
			final ChatLineBuffer lineBuffer = client.getChatLineMap()
					.get(ChatMessageType.CLAN_CHAT.getType());
			lineBuffer.removeMessageNode(event.getMessageNode());
		} else if (event.getType().equals(ChatMessageType.CLAN_GUEST_CHAT) && !isLocalPlayerSendingMessage
				&& !ablyManager.shouldShowMessge(cleanedName, cleanedMessage, true)) {
			final ChatLineBuffer lineBuffer = client.getChatLineMap()
					.get(ChatMessageType.CLAN_GUEST_CHAT.getType());
			lineBuffer.removeMessageNode(event.getMessageNode());
		} else if (event.getType().equals(ChatMessageType.FRIENDSCHAT) && isLocalPlayerSendingMessage) {
			if (!ablyManager.shouldPublishMessage(cleanedMessage, cleanedName)) {
				return;
			}
			ablyManager.shouldShowMessge(cleanedName, cleanedMessage, true);
			FriendsChatManager friendsChatManager = client.getFriendsChatManager();
			if (friendsChatManager != null) {
				ablyManager.publishMessage("f", cleanedMessage, "f:" + friendsChat,
						friendsChatManager.getName());
			}
		} else if (event.getType().equals(ChatMessageType.CLAN_CHAT) && isLocalPlayerSendingMessage) {
			if (!ablyManager.shouldPublishMessage(cleanedMessage, cleanedName)) {
				return;
			}
			ablyManager.shouldShowMessge(client.getLocalPlayer().getName(), cleanedMessage, true);
			ClanChannel clanChannel = client.getClanChannel();
			if (clanChannel != null) {
				ablyManager.publishMessage("c", cleanedMessage, "c:" + clanChannel.getName(),
						clanChannel.getName());
			}
		} else if (event.getType().equals(ChatMessageType.CLAN_GUEST_CHAT) && isLocalPlayerSendingMessage) {
			if (!ablyManager.shouldPublishMessage(cleanedMessage, cleanedName)) {
				return;
			}
			ablyManager.shouldShowMessge(client.getLocalPlayer().getName(), cleanedMessage, true);
			ClanChannel guestClanChannel = client.getGuestClanChannel();
			if (guestClanChannel != null) {
				ablyManager.publishMessage("c", cleanedMessage, "c:" + guestClanChannel.getName(),
						guestClanChannel.getName());
			}
		} else {
			ablyManager.shouldShowMessge(cleanedName, cleanedMessage, true);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event) {
		// Handle player menu clicks from MenuManager
		if (event.getMenuAction() == MenuAction.RUNELITE_PLAYER && event.getMenuOption().equals("GC Status")) {
			String target = Text.removeTags(event.getMenuTarget());
			checkPlayerGlobalChatStatus(target);
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event) {
		// Check if player lookup feature is enabled
		if (!config.showPlayerLookup()) {
			return;
		}

		String target = Text.removeTags(event.getTarget());

		// Add option for chat messages (when right-clicking a name in any chat)
		if (event.getOption().equals("Add friend") || event.getOption().equals("Message")) {
			client.createMenuEntry(-2)
					.setOption("GC Status")
					.setTarget(event.getTarget())
					.setType(MenuAction.RUNELITE)
					.onClick(e -> checkPlayerGlobalChatStatus(target));
		}
	}

	private void checkPlayerGlobalChatStatus(String playerName) {
		// Extract just the player name from "PlayerName (level-123)" format
		String extractedName = playerName;
		if (extractedName.contains("(level-")) {
			extractedName = extractedName.substring(0, extractedName.indexOf("(level-")).trim();
		}
		final String cleanName = Text.sanitize(extractedName);

		// Show checking message on client thread
		clientThread.invokeLater(() -> {
			String checkingMessage = "<col=ff9040>Checking Global Chat status for " + cleanName + "...</col>";
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", checkingMessage, null);
			return true;
		});

		// Make async request to check status
		String apiUrl = "https://global-chat-frontend.vercel.app/api/check-player-status?playerName=" + cleanName;
		Request request = new Request.Builder()
				.url(apiUrl)
				.get()
				.build();

		okHttpClient.newCall(request).enqueue(new okhttp3.Callback() {
			@Override
			public void onFailure(okhttp3.Call call, java.io.IOException e) {
				log.error("Error checking player status for " + cleanName, e);
				clientThread.invokeLater(() -> {
					String errorMessage = "<col=ff0000>Failed to check Global Chat status for " + cleanName + "</col>";
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", errorMessage, null);
					return true;
				});
			}

			@Override
			public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
				try {
					if (response.isSuccessful() && response.body() != null) {
						String responseBody = response.body().string();
						JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

						boolean isConnected = jsonResponse.get("connectedToGlobalChat").getAsBoolean();

						// Show result in chat
						clientThread.invokeLater(() -> {
							String statusColor = isConnected ? "00ff00" : "ff0000";
							String statusText = isConnected ? "CONNECTED" : "NOT CONNECTED";
							String resultMessage = "<col=" + statusColor + ">Global Chat: " + cleanName + " is "
									+ statusText + "</col>";
							client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", resultMessage, null);
							return true;
						});
					} else {
						// Handle error response
						clientThread.invokeLater(() -> {
							String errorMessage = "<col=ff0000>Failed to check Global Chat status for " + cleanName
									+ " (HTTP " + response.code() + ")</col>";
							client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", errorMessage, null);
							return true;
						});
					}
				} catch (Exception e) {
					log.error("Error parsing response for " + cleanName, e);
					clientThread.invokeLater(() -> {
						String errorMessage = "<col=ff0000>Failed to check Global Chat status for " + cleanName
								+ "</col>";
						client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", errorMessage, null);
						return true;
					});
				} finally {
					response.close();
				}
			}
		});
	}

	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent event) {

		if ("chatFilterCheck".equals(event.getEventName())) {

			int[] intStack = client.getIntStack();
			int intStackSize = client.getIntStackSize();
			// Extract the message type and message content from the event.
			final int messageType = intStack[intStackSize - 2];
			final int messageId = intStack[intStackSize - 1];

			final MessageNode messageNode = client.getMessages().get(messageId);
			final String name = messageNode.getName();
			if (name == null) {
				return;
			}
			String cleanedName = Text.sanitize(name);
			Player localPlayer = client.getLocalPlayer();
			if (localPlayer == null || localPlayer.getName() == null) {
				return;
			}
			boolean isLocalPlayerSendingMessage = cleanedName.equals(localPlayer.getName());

			boolean shouldConsiderHiding = !isLocalPlayerSendingMessage
					&& ChatMessageType.of(messageType) == ChatMessageType.PUBLICCHAT;

			if (shouldConsiderHiding && ablyManager.isUnderCbLevel(cleanedName)) {
				intStack[intStackSize - 3] = 0;
			}

		}

	}

	@Subscribe(priority = -2) // conflicts with chat filter plugin without this priority
	public void onOverheadTextChanged(OverheadTextChanged event) {
		if (!(event.getActor() instanceof Player) || event.getActor().getName() == null)
			return;
		String cleanedName = Text.sanitize(event.getActor().getName());
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null || localPlayer.getName() == null) {
			return;
		}
		boolean isLocalPlayerSendingMessage = cleanedName.equals(localPlayer.getName());

		if (!isLocalPlayerSendingMessage && ablyManager.isUnderCbLevel(cleanedName)) {
			event.getActor().setOverheadText("");
		}

	}

	private void showUpdateNotificationIfNeeded() {
		// Define the current version - update this when you want to show a new
		// notification
		String currentVersion = "v2.3.0";

		// Check if notification for this version has been shown
		String lastNotificationVersion = config.updateNotificationShown();

		if (!currentVersion.equals(lastNotificationVersion)) {
			// Show in-game chat message directly
			ablyManager.showUpdateNotification(
					"<col=00ff00>Global Chat v2.3 is here!</col> " +
							"<col=ff9040>New: Right-click any player to check if they're connected to Global Chat! Plus chat commands work seamlessly (!task, !kc), live connection status, see who's online in the info panel, and read-only mode option. "
							+
							"Support on Patreon to unlock higher message limits!</col>");

			// Mark this version as notified
			configManager.setConfiguration("globalchat", "updateNotificationShown", currentVersion);
		}
	}

	@Provides
	GlobalChatConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(GlobalChatConfig.class);
	}

	private void removeGlobalChatIconFromRecentMessage(String message) {
		try {
			// Don't show error messages if read-only mode is enabled
			if (config.readOnlyMode()) {
				return;
			}

			// Rate limiting: only show error message every 30 minutes to prevent spam
			long now = System.currentTimeMillis();
			if (now - lastFailedSendMessageTime < FAILED_SEND_MESSAGE_COOLDOWN) {
				return; // Skip showing message if within cooldown period
			}

			if (client.getGameState() == GameState.LOGGED_IN) {
				lastFailedSendMessageTime = now;
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
						"<col=ff0000>Message failed to send to Global Chat</col>", null);
			}
		} catch (Exception e) {
			log.error("Error notifying about failed message publish", e);
		}
	}

	private String getAccountIcon() {
		if (client.getWorldType().contains(WorldType.TOURNAMENT_WORLD)) {
			return "<img=33>";
		}
		switch (client.getAccountType()) {
			case IRONMAN:
				return "<img=2>";
			case HARDCORE_IRONMAN:
				return "<img=10>";
			case ULTIMATE_IRONMAN:
				return "<img=3>";
			default:
				return "";
		}
	}

	private BufferedImage createSimpleIcon() {
		try {
			// Load the icon from project root
			BufferedImage image = ImageIO.read(getClass().getResourceAsStream("/icon.png"));

			// Resize to 16x16 if needed
			if (image.getWidth() != 16 || image.getHeight() != 16) {
				BufferedImage resized = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g2 = resized.createGraphics();
				g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g2.drawImage(image, 0, 0, 16, 16, null);
				g2.dispose();
				return resized;
			}

			return image;
		} catch (Exception e) {
			log.error("Failed to load icon from project root", e);
			return null;
		}
	}

	// @Subscribe(priority = -2)
	// public void onClientTick(ClientTick clientTick) {
	// if (client.isMenuOpen()) {
	// return;
	// }

	// MenuEntry[] menuEntries = client.getMenuEntries();

	// for (MenuEntry entry : menuEntries) {
	// MenuAction type = entry.getType();

	// if (type == WALK
	// || type == WIDGET_TARGET_ON_PLAYER
	// || type == ITEM_USE_ON_PLAYER
	// || type == PLAYER_FIRST_OPTION
	// || type == PLAYER_SECOND_OPTION
	// || type == PLAYER_THIRD_OPTION
	// || type == PLAYER_FOURTH_OPTION
	// || type == PLAYER_FIFTH_OPTION
	// || type == PLAYER_SIXTH_OPTION
	// || type == PLAYER_SEVENTH_OPTION
	// || type == PLAYER_EIGHTH_OPTION
	// || type == RUNELITE_PLAYER) {
	// Player[] players = client.getCachedPlayers();
	// Player player = null;

	// int identifier = entry.getIdentifier();

	// // 'Walk here' identifiers are offset by 1 because the default
	// // identifier for this option is 0, which is also a player index.
	// if (type == WALK) {
	// identifier--;
	// }

	// if (identifier >= 0 && identifier < players.length) {
	// player = players[identifier];

	// }

	// if (player == null) {
	// return;
	// }

	// String oldTarget = entry.getTarget();
	// String newTarget = decorateTarget(oldTarget, player.getName());

	// entry.setTarget(newTarget);
	// }

	// }
	// }

}
