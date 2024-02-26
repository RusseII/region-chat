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

import io.ably.lib.types.PresenceMessage;
import net.runelite.client.callback.ClientThread;

import com.google.inject.Provides;

import io.ably.lib.types.Callback.Map;

import java.util.ArrayList;
import java.util.HashMap;
import javax.inject.Inject;
import javax.inject.Named;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.*;
import net.runelite.api.events.WorldChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.FriendsChatChanged;
import net.runelite.api.events.FriendsChatMemberJoined;
import net.runelite.client.config.ConfigManager;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;
import lombok.extern.slf4j.Slf4j;
import static net.runelite.api.MenuAction.ITEM_USE_ON_PLAYER;
import static net.runelite.api.MenuAction.PLAYER_EIGHTH_OPTION;
import static net.runelite.api.MenuAction.PLAYER_FIFTH_OPTION;
import static net.runelite.api.MenuAction.PLAYER_FIRST_OPTION;
import static net.runelite.api.MenuAction.PLAYER_FOURTH_OPTION;
import static net.runelite.api.MenuAction.PLAYER_SECOND_OPTION;
import static net.runelite.api.MenuAction.PLAYER_SEVENTH_OPTION;
import static net.runelite.api.MenuAction.PLAYER_SIXTH_OPTION;
import static net.runelite.api.MenuAction.PLAYER_THIRD_OPTION;
import static net.runelite.api.MenuAction.RUNELITE_PLAYER;
import static net.runelite.api.MenuAction.WALK;
import static net.runelite.api.MenuAction.WIDGET_TARGET_ON_PLAYER;


@Slf4j
@PluginDescriptor(name = "World Global Chat", description = "Talk anywhere!", tags = {
		"chat" })
public class GlobalChatPlugin extends Plugin {
	@Inject
	private AblyManager ablyManager;

	@Inject
	private Client client;

	@Inject
	@Getter
	private ClientThread clientThread;

	@Getter
	@Setter
	private String friendsChat;

	@Getter
	@Setter
	private String theClanName;

	@Getter
	private final HashMap<Integer, Boolean> filteredMessageIds = new HashMap<>();


	public static final int CYCLES_PER_GAME_TICK = Constants.GAME_TICK_LENGTH / Constants.CLIENT_TICK_LENGTH;


	private static final int OVERHEAD_TEXT_TICK_TIMEOUT = 5;
	private static final int CYCLES_FOR_OVERHEAD_TEXT = OVERHEAD_TEXT_TICK_TIMEOUT * CYCLES_PER_GAME_TICK;

	@Getter
	@Setter
	private String theGuesttheClanName;

	@Getter
	private final HashMap<String, ArrayList<String>> previousMessages = new HashMap<>();

	@Getter
	@Inject
	@Named("developerMode")
	private boolean developerMode;

	@Override
	protected void startUp() throws Exception {
		ablyManager.startConnection();

	}

	@Override
	protected void shutDown() throws Exception {
		ablyManager.closeConnection();
	}

	@Subscribe
	public void onWorldChanged(WorldChanged worldChanged) {
		ablyManager.closeConnection();
		ablyManager.startConnection();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.LOGGED_IN) {
			onLoggedInGameState();
		}
	}


	@Subscribe
	public void onFriendsChatMemberJoined(FriendsChatMemberJoined event) {
		final FriendsChatMember member = event.getMember();
		String memberName = member.getName().replace('\u00A0', ' ').trim();
		String playerName = client.getLocalPlayer().getName().replace('\u00A0', ' ').trim();
		;

		Boolean isCurrentUser = memberName.equals(playerName);

		if (isCurrentUser) {
			FriendsChatManager friendsChatManager = client.getFriendsChatManager();
			friendsChat = friendsChatManager.getOwner();
			ablyManager.subscribeToCorrectChannel("f:" + friendsChat);
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
			ablyManager.subscribeToCorrectChannel("p:" + name);
			ablyManager.subscribeToCorrectChannel("w:" + String.valueOf(client.getWorld()));
			ablyManager.connectPress();
			return true;
		});
	}

	@Subscribe
	public void onClanChannelChanged(ClanChannelChanged event) {
		boolean inClanNow = event.getClanChannel() != null;
		String channelName = inClanNow ? event.getClanChannel().getName() : null;
		boolean isGuest = event.isGuest();

		if (!inClanNow) {
			String channelPrefix = isGuest ? "c:" + theGuesttheClanName : "c:" + theClanName;
			ablyManager.closeSpecificChannel(channelPrefix);

			if (isGuest) {
				theGuesttheClanName = null;
			} else {
				theClanName = null;
			}
		} else {
			String targetChannelName = "c:" + channelName;
			ablyManager.subscribeToCorrectChannel(targetChannelName);

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
			ablyManager.shouldShowMessge(cleanedName, cleanedMessage, true);
			ablyManager.publishMessage("w", cleanedMessage, "w:" + String.valueOf(client.getWorld()), "");
		} else if (event.getType().equals(ChatMessageType.PRIVATECHATOUT)) {
			ablyManager.shouldShowMessge(client.getLocalPlayer().getName(), cleanedMessage, true);
			ablyManager.publishMessage("p", cleanedMessage, "p:" + cleanedName, cleanedName);
		} else if (event.getType().equals(ChatMessageType.PRIVATECHAT)
				&& !ablyManager.shouldShowMessge(cleanedName, cleanedMessage, true)) {
			final ChatLineBuffer lineBuffer = client.getChatLineMap()
					.get(ChatMessageType.PRIVATECHAT.getType());
			lineBuffer.removeMessageNode(event.getMessageNode());
		} else if (event.getType().equals(ChatMessageType.FRIENDSCHAT) && isLocalPlayerSendingMessage) {
			ablyManager.shouldShowMessge(client.getLocalPlayer().getName(), cleanedMessage, true);
			ablyManager.publishMessage("f", cleanedMessage, "f:" + friendsChat,
					client.getFriendsChatManager().getName());
		} else if (event.getType().equals(ChatMessageType.CLAN_CHAT)
				&& isLocalPlayerSendingMessage) {
			ablyManager.shouldShowMessge(client.getLocalPlayer().getName(), cleanedMessage, true);
			ablyManager.publishMessage("c", cleanedMessage, "c:" + client.getClanChannel().getName(),
					client.getClanChannel().getName());
		} else if (event.getType().equals(ChatMessageType.CLAN_GUEST_CHAT)
				&& isLocalPlayerSendingMessage) {
			ablyManager.shouldShowMessge(client.getLocalPlayer().getName(), cleanedMessage, true);
			ablyManager.publishMessage("c", cleanedMessage, "c:" + client.getGuestClanChannel().getName(),
					client.getGuestClanChannel().getName());
		} else {
			ablyManager.shouldShowMessge(cleanedName, cleanedMessage, true);
		}
	}

	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent event) {
		if (!"chatFilterCheck".equals(event.getEventName())) {
			return;
		}

		int[] intStack = client.getIntStack();
		int intStackSize = client.getIntStackSize();
		String[] stringStack = client.getStringStack();
		int stringStackSize = client.getStringStackSize();

		// Extract the message type and message content from the event.
		final int messageType = intStack[intStackSize - 2];
		final int messageId = intStack[intStackSize - 1];
		String message = stringStack[stringStackSize - 1];

		final MessageNode messageNode = client.getMessages().get(messageId);
		final String name = messageNode.getName();
		String cleanedName = Text.sanitize(name);
		boolean isLocalPlayerSendingMessage = cleanedName.equals(client.getLocalPlayer().getName());

		boolean shouldConsiderHiding = !isLocalPlayerSendingMessage && ChatMessageType.of(messageType) == ChatMessageType.PUBLICCHAT;

		if (shouldConsiderHiding && ablyManager.isUnderCbLevel(cleanedName)) {

			intStack[intStackSize - 3] = 0;

			filteredMessageIds.put(messageId, true);

		}
		if (shouldConsiderHiding && filteredMessageIds.containsKey(messageId)) {

			intStack[intStackSize - 3] = 0;
		}
	}

	@Subscribe(priority = -2) // conflicts with chat filter plugin without this priority
	public void onOverheadTextChanged(OverheadTextChanged event)
	{
		if (!(event.getActor() instanceof Player) || event.getActor().getName() == null) return;
		String cleanedName = Text.sanitize(event.getActor().getName());
		boolean isLocalPlayerSendingMessage = cleanedName.equals(client.getLocalPlayer().getName());


		if (!isLocalPlayerSendingMessage && ablyManager.isUnderCbLevel(cleanedName))
		{
			event.getActor().setOverheadText("");
		}


	}

	@Provides
	GlobalChatConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(GlobalChatConfig.class);
	}


	@Subscribe(priority = -2)
	public void onClientTick(ClientTick clientTick) {
		if (client.isMenuOpen()) {
			return;
		}

		MenuEntry[] menuEntries = client.getMenuEntries();

		for (MenuEntry entry : menuEntries) {
			MenuAction type = entry.getType();

			if (type == WALK
					|| type == WIDGET_TARGET_ON_PLAYER
					|| type == ITEM_USE_ON_PLAYER
					|| type == PLAYER_FIRST_OPTION
					|| type == PLAYER_SECOND_OPTION
					|| type == PLAYER_THIRD_OPTION
					|| type == PLAYER_FOURTH_OPTION
					|| type == PLAYER_FIFTH_OPTION
					|| type == PLAYER_SIXTH_OPTION
					|| type == PLAYER_SEVENTH_OPTION
					|| type == PLAYER_EIGHTH_OPTION
					|| type == RUNELITE_PLAYER) {
				Player[] players = client.getCachedPlayers();
				Player player = null;

				int identifier = entry.getIdentifier();

				// 'Walk here' identifiers are offset by 1 because the default
				// identifier for this option is 0, which is also a player index.
				if (type == WALK) {
					identifier--;
				}

				if (identifier >= 0 && identifier < players.length) {
					player = players[identifier];

				}

				if (player == null) {
					return;
				}



				String oldTarget = entry.getTarget();
				String newTarget = decorateTarget(oldTarget, player.getName());

				entry.setTarget(newTarget);
			}

		}
	}
	public String decorateTarget(String oldTarget, String playerName)
	{
		PresenceMessage[] members = ablyManager.members;
		for (PresenceMessage member : members) { // Corrected variable names and types
			if (member.clientId.equals(playerName)) {
				String newTarget = oldTarget;

				newTarget = "<img=19> " + newTarget;


				return newTarget;
			}
		}
	return oldTarget;
	}



}
