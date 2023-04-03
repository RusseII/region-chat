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
package com.regionchat;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Message;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WorldType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.util.Text;
import net.runelite.api.Player;
import net.runelite.api.Constants;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class AblyManager {

	public static final int CYCLES_PER_GAME_TICK = Constants.GAME_TICK_LENGTH / Constants.CLIENT_TICK_LENGTH;

	private static final int OVERHEAD_TEXT_TICK_TIMEOUT = 5;
	private static final int CYCLES_FOR_OVERHEAD_TEXT = OVERHEAD_TEXT_TICK_TIMEOUT * CYCLES_PER_GAME_TICK;

	private final Client client;

	@Inject
	Gson gson;

	private final Map<String, String> previousMessages = new HashMap<>();

	private final String CHANNEL_NAME_PREFIX = "regionchat";

	private boolean changingChannels;

	@Inject
	ChatMessageManager chatMessageManager;

	private final RegionChatConfig config;

	private AblyRealtime ablyRealtime;
	private Channel ablyRegionChannel;
	private Channel ablyGlobalChannel;

	@Inject
	public AblyManager(Client client, RegionChatConfig config) {
		this.client = client;
		this.config = config;
	}

	public void startConnection() {
		setupAblyInstances();
	}

	public void closeConnection() {
		ablyRealtime.close();
		ablyRealtime = null;
		ablyRegionChannel = null;
		ablyGlobalChannel = null;
	}

	public void publishMessage(String message, Boolean global, String to) {
		if (client.getLocalPlayer() == null) {
			return;
		}

		if (ablyRegionChannel == null && !global) {
			return;
		}

		if (ablyGlobalChannel == null && global) {
			return;
		}

		try {
			JsonObject msg = io.ably.lib.util.JsonUtils.object()
					.add("symbol", getAccountIcon())
					.add("username", client.getLocalPlayer().getName())
					.add("message", message).add("global", global.toString()).add("to", to).toJson();
			if (global) {
				ablyGlobalChannel.publish("event", msg);
			} else {
				ablyRegionChannel.publish("event", msg);
			}
		} catch (AblyException err) {
			System.out.println(err.getMessage());
		}
	}

	private void handleMessage(Message message) {
		if (client.getGameState() == GameState.LOGGED_IN) {
			handleAblyMessage(message);
		}
	}

	private void handleAblyMessage(Message message) {
		if (client.getGameState() != GameState.LOGGED_IN) {
			return;
		}

		RegionChatMessage msg = gson.fromJson((JsonElement) message.data, RegionChatMessage.class);
		String username = msg.username;
		String receivedMsg = Text.removeTags(msg.message);
		if (!shouldShowMessge(username, receivedMsg, false)) {
			return;
		}

		final ChatMessageBuilder chatMessageBuilder = new ChatMessageBuilder()
				.append(receivedMsg);

		if (username.length() > 12) {
			return;
		}
		if (msg.global.equals("true") && !username.equals(client.getLocalPlayer().getName())
				&& msg.to.equals(client.getLocalPlayer().getName())) {

			chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.PRIVATECHAT)
					.name(msg.symbol + msg.username)
					.runeLiteFormattedMessage(chatMessageBuilder.build())
					.build());
		} else if (msg.global.equals("false")) {

			chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.PUBLICCHAT)
					.name(msg.symbol + msg.username)
					.runeLiteFormattedMessage(chatMessageBuilder.build())
					.build());

			for (Player player : client.getPlayers()) {
				if (player != null &&
						player.getName() != null &&
						username.equals(player.getName())) {
					player.setOverheadText(receivedMsg);
					player.setOverheadCycle(CYCLES_FOR_OVERHEAD_TEXT);

					return;
				}
			}

		}

	}

	public boolean shouldShowMessge(String name, String message, Boolean set) {
		String prevMessage = previousMessages.get(name);

		// If someone is spamming the same message during a session, block it
		if (message.equals(prevMessage)) {
			return false;
		}
		if (set) {
			previousMessages.put(name, message);
		}

		return true;
	}

	private void setupAblyInstances() {
		try {
			ClientOptions clientOptions = new ClientOptions();
			clientOptions.key = "ubLi2Q.kA6NlA:djTnpbYSimiCtMw-5bhaOXKmDB3hd-GWsyyPtZHvh3k";
			ablyRealtime = new AblyRealtime(clientOptions);
		} catch (AblyException e) {
			e.printStackTrace();
		}
	}

	public void connectToGlobal() {
		Boolean global = true;
		String newChannelName = CHANNEL_NAME_PREFIX + ":" + "global";

		if (changingChannels) {
			return;
		}

		if (ablyGlobalChannel != null && ablyGlobalChannel.name.equals(newChannelName)) {
			if (ablyGlobalChannel.state == ChannelState.detached) {
				subscribeToChannel(global);
			}

			return;
		}

		changingChannels = true;

		if (ablyGlobalChannel == null) {
			ablyGlobalChannel = ablyRealtime.channels.get(newChannelName);
			subscribeToChannel(global);
			return;
		}

		try {
			ablyGlobalChannel.unsubscribe();
			ablyGlobalChannel.detach(detatchListener(newChannelName, global));
		} catch (AblyException err) {
			changingChannels = false;
			System.err.println(err.getMessage());
		}
	}

	public void connectToRegion(String world) {
		Boolean global = false;
		String newChannelName = CHANNEL_NAME_PREFIX + ":" + world + ":" + "global";

		if (changingChannels) {
			return;
		}

		if (ablyRegionChannel != null && ablyRegionChannel.name.equals(newChannelName)) {
			if (ablyRegionChannel.state == ChannelState.detached) {
				subscribeToChannel(global);
			}

			return;
		}

		changingChannels = true;

		if (ablyRegionChannel == null) {
			ablyRegionChannel = ablyRealtime.channels.get(newChannelName);
			subscribeToChannel(global);
			return;
		}

		try {
			ablyRegionChannel.unsubscribe();
			ablyRegionChannel.detach(detatchListener(newChannelName, global));
		} catch (AblyException err) {
			changingChannels = false;
			System.err.println(err.getMessage());
		}
	}

	public CompletionListener detatchListener(String newChannelName, Boolean global) {
		return new CompletionListener() {
			@Override
			public void onSuccess() {
				if (global) {
					ablyGlobalChannel = ablyRealtime.channels.get(newChannelName);

				} else {
					ablyRegionChannel = ablyRealtime.channels.get(newChannelName);

				}
				subscribeToChannel(global);
			}

			@Override
			public void onError(ErrorInfo reason) {
				System.err.println(reason.message);
				changingChannels = false;
			}
		};
	}

	public void disconnectFromRegions() {
		if (ablyRegionChannel != null && ablyRegionChannel.state == ChannelState.attached) {
			try {
				ablyRegionChannel.unsubscribe();
				ablyRegionChannel.detach();
			} catch (AblyException err) {
				System.err.println(err.getMessage());
			}
		}

		if (ablyGlobalChannel != null && ablyGlobalChannel.state == ChannelState.attached) {
			try {
				ablyGlobalChannel.unsubscribe();
				ablyGlobalChannel.detach();
			} catch (AblyException err) {
				System.err.println(err.getMessage());
			}
		}
	}

	private void subscribeToChannel(Boolean global) {
		try {
			if (global) {
				ablyGlobalChannel.subscribe(this::handleMessage);
			} else {
				ablyRegionChannel.subscribe(this::handleMessage);
			}
		} catch (AblyException err) {
			System.err.println(err.getMessage());
		}

		changingChannels = false;
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
		}

		return "";
	}
}
