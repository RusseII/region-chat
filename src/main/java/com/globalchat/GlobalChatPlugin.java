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

import java.util.ArrayList;
import java.util.HashMap;
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
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.net.URL;
import javax.imageio.ImageIO;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.ScriptCallbackEvent;
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

	@Override
	protected void startUp() throws Exception {
		// ablyManager.startConnection();
		onLoggedInGameState(); // Call this to handle turning plugin on when already logged in, should do
								// nothing on initial call

		// Setup info panel
		infoPanel = new GlobalChatInfoPanel(developerMode, ablyManager, supporterManager);
		log.info("Created GlobalChatInfoPanel");

		// Create navigation button with simple icon
		navButton = NavigationButton.builder()
				.tooltip("Global Chat Info")
				.priority(0)
				.panel(infoPanel)
				.icon(createSimpleIcon())
				.build();

		clientToolbar.addNavigation(navButton);
		log.info("Added Global Chat navigation button to toolbar");
	}

	@Override
	protected void shutDown() throws Exception {
		ablyManager.closeConnection();
		shouldConnect = true;

		// Clean up supporter manager
		if (supporterManager != null) {
			supporterManager.shutdown();
		}

		// Clean up UI panel
		if (navButton != null) {
			clientToolbar.removeNavigation(navButton);
		}
	}

	@Subscribe
	public void onWorldChanged(WorldChanged worldChanged) {
		shouldConnect = true;

		// Force cleanup before reconnecting
		ablyManager.closeConnection();

		// Add delay to ensure cleanup completes before reconnection
		// Reconnect on next game tick to avoid race conditions
		clientThread.invokeLater(() -> {
			ablyManager.startConnection();
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
			// we return true in this case as something went wrong and somehow the state
			// isn't logged in, so we don't
			// want to keep scheduling this task.
			if (client.getGameState() != GameState.LOGGED_IN) {
				return true;
			}

			final Player player = client.getLocalPlayer();

			// player is null, so we can't get the display name so, return false, which will
			// schedule
			// the task on the client thread again.
			if (player == null) {
				return false;
			}
			final String name = player.getName();
			if (name == null) {
				return false;
			}

			if (name.equals("")) {
				return false;
			}
			if (!shouldConnect) {
				return true;
			}
			String sanitizedName = Text.sanitize(name);

			String world = String.valueOf(client.getWorld());
			ablyManager.startConnection();
			ablyManager.subscribeToCorrectChannel("p:" + name, world);
			ablyManager.subscribeToCorrectChannel("w:" + world, "pub");
			shouldConnect = false;

			// Show update notification if not shown before
			showUpdateNotificationIfNeeded();

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
			ablyManager.closeSpecificChannel(channelPrefix);

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

	@Subscribe
	public void onChatMessage(ChatMessage event) {
		String cleanedMessage = Text.removeTags(event.getMessage());

		String cleanedName = Text.sanitize(event.getName());
		boolean isPublic = event.getType().equals(ChatMessageType.PUBLICCHAT);

		boolean isLocalPlayerSendingMessage = cleanedName.equals(client.getLocalPlayer().getName());
		if (isPublic && isLocalPlayerSendingMessage) {
			// Check for spam BEFORE publishing to save costs
			if (!ablyManager.shouldPublishMessage(cleanedMessage, cleanedName)) {
				return; // Don't publish spam messages
			}

			ablyManager.shouldShowMessge(cleanedName, cleanedMessage, true);

			ablyManager.publishMessage("w", cleanedMessage, "w:" + String.valueOf(client.getWorld()), "");
			// Disable functality for publishing direct messages
			// } else if (event.getType().equals(ChatMessageType.PRIVATECHATOUT)) {
			// ablyManager.shouldShowMessge(client.getLocalPlayer().getName(),
			// cleanedMessage, true);
			// ablyManager.publishMessage("p", cleanedMessage, "p:" + cleanedName,
			// cleanedName);
		} else if (event.getType().equals(ChatMessageType.PRIVATECHAT)
				&& !ablyManager.shouldShowMessge(cleanedName, cleanedMessage, true)) {
			final ChatLineBuffer lineBuffer = client.getChatLineMap()
					.get(ChatMessageType.PRIVATECHAT.getType());
			lineBuffer.removeMessageNode(event.getMessageNode());
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
			// Check for spam BEFORE publishing to save costs
			if (!ablyManager.shouldPublishMessage(cleanedMessage, cleanedName)) {
				return; // Don't publish spam messages
			}

			ablyManager.shouldShowMessge(cleanedName, cleanedMessage, true);
			FriendsChatManager friendsChatManager = client.getFriendsChatManager();
			if (friendsChatManager != null) {
				ablyManager.publishMessage("f", cleanedMessage, "f:" + friendsChat,
						friendsChatManager.getName());
			}
		} else if (event.getType().equals(ChatMessageType.CLAN_CHAT)
				&& isLocalPlayerSendingMessage) {
			// Check for spam BEFORE publishing to save costs
			if (!ablyManager.shouldPublishMessage(cleanedMessage, cleanedName)) {
				return; // Don't publish spam messages
			}

			ablyManager.shouldShowMessge(client.getLocalPlayer().getName(), cleanedMessage, true);
			ClanChannel clanChannel = client.getClanChannel();
			if (clanChannel != null) {
				ablyManager.publishMessage("c", cleanedMessage, "c:" + clanChannel.getName(),
						clanChannel.getName());
			}
		} else if (event.getType().equals(ChatMessageType.CLAN_GUEST_CHAT)
				&& isLocalPlayerSendingMessage) {
			// Check for spam BEFORE publishing to save costs
			if (!ablyManager.shouldPublishMessage(cleanedMessage, cleanedName)) {
				return; // Don't publish spam messages
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
		String currentVersion = "v2.0.0";

		// Check if notification for this version has been shown
		String lastNotificationVersion = config.updateNotificationShown();

		if (!currentVersion.equals(lastNotificationVersion)) {
			// Show the update notification on next tick
			clientThread.invokeLater(() -> {
				// Show in-game chat message
				ablyManager.showUpdateNotification(
						"<col=00ff00>Global Chat v2.0 is here!</col> " +
								"New: Better error handling, redesigned info panel, spam prevention, and cost optimizations. "
								+
								"<col=ff9040>Support on Patreon to increase service limits!</col>");

				// Mark this version as notified
				configManager.setConfiguration("globalchat", "updateNotificationShown", currentVersion);
				return true;
			});
		}
	}

	@Provides
	GlobalChatConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(GlobalChatConfig.class);
	}

	private BufferedImage createSimpleIcon() {
		try {
			// Load the icon from project root
			BufferedImage image = ImageIO.read(new java.io.File("icon.png"));

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
