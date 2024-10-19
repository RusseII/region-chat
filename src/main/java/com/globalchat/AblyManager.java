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

import java.util.HashSet;
import java.util.Arrays;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.realtime.Presence;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.Message;
import io.ably.lib.types.Param;
import io.ably.lib.types.PresenceMessage;
import java.util.Base64;

import java.util.Set;
import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
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
import net.runelite.api.Friend;
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

	@Getter
	@Setter
	public PresenceMessage[] members;

	private final Map<String, String> previousMessages = new HashMap<>();

	private final HashMap<String, Integer> playerCombats = new HashMap<>();

	private boolean changingChannels;

	@Inject
	ChatMessageManager chatMessageManager;

	private final GlobalChatConfig config;

	private AblyRealtime ablyRealtime;

	@Inject
	public AblyManager(Client client, GlobalChatConfig config) {
		this.client = client;
		this.config = config;
	}

	public void startConnection() {
		setupAblyInstances();
	}

	public void closeSpecificChannel(String channelName) {
		try {
			ablyRealtime.channels.get(channelName).detach();
		} catch (AblyException err) {
			log.error("error", err);
		}
	}

	public void closeConnection() {
		ablyRealtime.close();
		ablyRealtime = null;
	}

	public boolean isUnderCbLevel(String username) {
		String cleanedName = Text.sanitize(username);
		Integer cachedCbLevel = playerCombats.get(cleanedName);
		if (cachedCbLevel != null) {
			return cachedCbLevel < config.filterOutFromBelowCblvl();
		}

		for (Player player : client.getPlayers()) {
			if (player != null && player.getName() != null && cleanedName.equals(player.getName())) {
				playerCombats.put(cleanedName, player.getCombatLevel());
				return player.getCombatLevel() < config.filterOutFromBelowCblvl();
			}
		}
		return false; // If no matching player is found, return false.
	}

	public boolean isSpam(String message) {

		Set<String> spamMessages = new HashSet<>();
		spamMessages
				.addAll(Arrays.asList(
						"In the name of Saradomin, protector of us all, I now join you in the eyes of Saradomin.",
						"Thy cause was false, thy skills did lack; See you in Lumbridge when you get back.",
						"Go in peace in the name of Saradomin; May his glory shine upon you like the sun.",
						"The currency of goodness is honour; It retains its value through scarcity. This is Saradomin's wisdom.",
						"Two great warriors, joined by hand, to spread destruction across the land. In Zamorak's name, now two are one.",
						"The weak deserve to die, so the strong may flourish. This is the creed of Zamorak.",
						"May your bloodthirst never be sated, and may all your battles be glorious. Zamorak bring you strength.",
						"There is no opinion that cannot be proven true...by crushing those who choose to disagree with it. Zamorak give me strength!",
						"Battles are not lost and won; They simply remove the weak from the equation. Zamorak give me strength!",
						"Those who fight, then run away, shame Zamorak with their cowardice. Zamorak give me strength!",
						"Battle is by those who choose to disagree with it. Zamorak give me strength!",
						"Strike fast, strike hard, strike true: The strength of Zamorak will be with you. Zamorak give me strength!",
						"Light and dark, day and night, balance arises from contrast. I unify thee in the name of Guthix.",
						"Thy death was not in vain, for it brought some balance to the world. May Guthix bring you rest.",
						"May you walk the path, and never fall, for Guthix walks beside thee on thy journey. May Guthix bring you peace.",
						"The trees, the earth, the sky, the waters; All play their part upon this land. May Guthix bring you balance.",
						"Big High War God want great warriors. Because you can make more... I bind you in Big High War God name.",
						"You not worthy of Big High War God; you die too easy.",
						"Big High War God make you strong... so you smash enemies.",
						"War is best, peace is for weak. If you not worthy of Big High War God... you get made dead soon.",
						"As ye vow to be at peace with each other... and to uphold high values of morality and friendship... I now pronounce you united in the law of Armadyl.",
						"Thou didst fight true... but the foe was too great. May thy return be as swift as the flight of Armadyl.",
						"For thy task is lawful... May the blessing of Armadyl be upon thee.",
						"Peace shall bring thee wisdom; Wisdom shall bring thee peace. This is the law of Armadyl.",
						"Ye faithful and loyal to the Great Lord... May ye together succeed in your deeds. Ye are now joined by the greatest power.",
						"Thy faith faltered, no power could save thee. Like the Great Lord, one day you shall rise again.",
						"By day or night, in defeat or victory... the power of the Great Lord be with thee.",
						"Follower of the Great Lord be relieved: One day your loyalty will be rewarded. Power to the Great Lord!",
						"Just say neigh to gambling!", "Eww stinky!", "I will burn with you.",
						"Burn with me!", "Here fishy fishies!",
						"For Camelot!", "Raarrrrrgggggghhhhhhh", "Taste vengeance!", "Smashing!", "*yawn*"));
		// Messages from tobMistakeTrackerSpam
		spamMessages.addAll(Arrays.asList(
				"I'm planking!", // Note: Only need to add "I'm planking!" once
				"I'm drowning in Maiden's blood!",
				"I'm stunned!",
				"Bye!",
				"I'm eating cabbages!",
				"I can't count to four!",
				"I'm PKing my team!",
				"I was stuck in a web!",
				"I'm healing Verzik!"));
		// Messages from TOAMistakeTrackerSpam
		spamMessages.addAll(Arrays.asList(
				"Argh! It burns!",
				"Come on and slam!",
				"Ah! It burns!",
				"Embrace Darkness!",
				"I'm too slow!",
				"I'm griefing!",
				"?",
				"This jug feels a little light...",
				"I'm drowning in acid!",
				"I'm on a blood cloud!",
				"Nihil!",
				"I'm surfing!",
				"I'm exploding!",
				"The swarms are going in!",
				"I've been hatched!",
				"I'm fuming!",
				"The sky is falling!",
				"I've been corrupted!",
				"It's venomous!",
				"Come on and slam!|And welcome to the jam!",
				"I got rocked!",
				"They see me rollin'...",
				"It's raining!",
				"Who put that there?",
				"I'm going down!",
				"I'm disco-ing!",
				"I'm dancing!",
				"I'm winded!",
				"I'm getting bombed!",
				"I'm in jail!",
				"What even was that attack?",
				"I'm tripping!"));

		// Check if the message is in the set
		return spamMessages.contains(message);
	}

	public void publishMessage(String t, String message, String channel, String to) {
		try {

			Channel currentChannel;
			if (t.equals("p")) {
				Friend friend = client.getFriendContainer().findByName(to);
				String key = String.valueOf(friend.getWorld());
				String paddedKeyString = padKey(key, 16); // Ensure the key is 16 bytes long
				String base64EncodedKey = Base64.getEncoder().encodeToString(paddedKeyString.getBytes());
				ChannelOptions options = ChannelOptions.withCipherKey(base64EncodedKey);
				currentChannel = ablyRealtime.channels.get(channel, options);

			} else {
				String paddedKeyString = padKey("pub", 16); // Ensure the key is 16 bytes long
				String base64EncodedKey = Base64.getEncoder().encodeToString(paddedKeyString.getBytes());
				ChannelOptions options = ChannelOptions.withCipherKey(base64EncodedKey);
				currentChannel = ablyRealtime.channels.get(channel, options);

			}
			if (client.getLocalPlayer() == null) {
				return;
			}
			if (config.readOnlyMode()) {
				return;
			}

			String username = Text.removeTags(client.getLocalPlayer().getName());
			JsonObject msg = io.ably.lib.util.JsonUtils.object()
					.add("symbol", getAccountIcon())
					.add("username", username)
					.add("message", message).add("type", t).add("to", to).toJson();
			currentChannel.publish("event", msg);
		} catch (AblyException err) {
			log.error("error", err);
		}
	}

	public void handleMessage(Message message) {
		if (client.getGameState() == GameState.LOGGED_IN) {
			handleAblyMessage(message);
		}
	}

	public void meowHiss(PresenceMessage message) {

		try {
			String world = String.valueOf(client.getWorld());

			Channel currentChannel = ablyRealtime.channels.get("pr:" + world);

			members = currentChannel.presence.get(false);

		}

		catch (AblyException e) {
		}
	}

	private String getValidAccountIcon(String accountIcon) {
		if (accountIcon.equals("<img=2>"))
			return accountIcon;
		if (accountIcon.equals("<img=10>"))
			return accountIcon;
		if (accountIcon.equals("<img=3>"))
			return accountIcon;
		return "";
	}

	private void handleAblyMessage(Message message) {
		if (client.getGameState() != GameState.LOGGED_IN) {
			return;
		}

		GlobalChatMessage msg = gson.fromJson((JsonElement) message.data, GlobalChatMessage.class);
		String username = Text.removeTags(msg.username);
		String receivedMsg = Text.removeTags(msg.message);
		if (!shouldShowMessge(username, receivedMsg, false)) {
			return;
		}
		if (!shouldShowCurrentMessage(receivedMsg, username)) {
			return;
		}

		String symbol = getValidAccountIcon(msg.symbol);

		if (msg.type.equals("w")) {
			symbol = "<img=19> " + msg.symbol;
		}

		final ChatMessageBuilder chatMessageBuilder = new ChatMessageBuilder()
				.append(receivedMsg);

		if (username.length() > 12) {
			return;
		}
		if (msg.type.equals("p") && !username.equals(client.getLocalPlayer().getName())
				&& msg.to.equals(client.getLocalPlayer().getName())) {

			chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.PRIVATECHAT)
					.name(symbol + username)
					.runeLiteFormattedMessage(chatMessageBuilder.build())
					.build());
		} else if (msg.type.equals("w")) {

			chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.PUBLICCHAT)
					.name(symbol + username)
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

		else if (msg.type.equals("f") && !username.equals(client.getLocalPlayer().getName())) {

			chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.FRIENDSCHAT)
					.name(symbol + username).sender(msg.to)
					.runeLiteFormattedMessage(chatMessageBuilder.build())
					.build());
		} else if (msg.type.equals("c") && !username.equals(client.getLocalPlayer().getName())) {

			chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.CLAN_CHAT)
					.name(symbol + username).sender(msg.to)
					.runeLiteFormattedMessage(chatMessageBuilder.build())
					.build());
		}

	}

	// Checks for bits someone could insert in to be icons
	// Important in case it's a JMod icon or something
	private boolean isInvalidUsername(String username) {
		return username.toLowerCase().startsWith("mod ");
	}

	public boolean shouldShowCurrentMessage(String message, String name) {
		if (config.hideSpamMessages()) {
			if (isSpam(message)) {
				return false;
			}
		}
		if (isInvalidUsername(name))
			return false;

		if (isUnderCbLevel(name)) {
			return false;
		}
		return true;
	}

	public boolean shouldShowMessge(String name, String message, Boolean set) {
		final String sanitizedName = Text.toJagexName(Text.removeTags(name));

		String prevMessage = previousMessages.get(sanitizedName);

		// If someone is spamming the same message during a session, block it
		if (message.equals(prevMessage)) {
			return false;
		}
		if (set) {
			previousMessages.put(sanitizedName, message);
		}

		return true;
	}

	private void setupAblyInstances() {
		try {
			ClientOptions clientOptions = new ClientOptions();
			String name = Text.sanitize(client.getLocalPlayer().getName());
			Param[] params = new Param[] {
					new Param("clientId", name),
			};
			clientOptions.authHeaders = params;
			clientOptions.authUrl = "https://global-chat-plugin.vercel.app/api/token";

			ablyRealtime = new AblyRealtime(clientOptions);
		} catch (AblyException e) {
			e.printStackTrace();
		}
	}

	public void connectPress(String world, String name) {
		if (client.getLocalPlayer() == null) {
			return;
		}
		try {
			Channel currentChannel = ablyRealtime.channels.get("pr:" + world);
			currentChannel.presence.subscribe(PresenceMessage.Action.enter, this::meowHiss);
			currentChannel.presence.enterClient(name, "enter");

		} catch (AblyException err) {
			log.error("error", err);
		}
	}

	private static String padKey(String key, int length) {
		if (key.length() >= length) {
			return key.substring(0, length);
		}
		StringBuilder keyBuilder = new StringBuilder(key);
		while (keyBuilder.length() < length) {
			keyBuilder.append("0"); // Pad the key with zeros
		}
		return keyBuilder.toString();
	}

	public Channel subscribeToCorrectChannel(String channelName, String key) {

		try {
			String paddedKeyString = padKey(key, 16); // Ensure the key is 16 bytes long
			String base64EncodedKey = Base64.getEncoder().encodeToString(paddedKeyString.getBytes());
			ChannelOptions options = ChannelOptions.withCipherKey(base64EncodedKey);
			Channel currentChannel = ablyRealtime.channels.get(channelName, options);
			currentChannel.subscribe(this::handleMessage);
			return currentChannel;
		} catch (AblyException err) {
			log.error("error", err);
		}
		return null;

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
