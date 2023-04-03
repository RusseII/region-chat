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

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.HashMap;
import javax.inject.Inject;
import javax.inject.Named;
import lombok.Getter;
import net.runelite.api.events.WorldChanged;
import net.runelite.api.ChatLineBuffer;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.ChatLineBuffer;

@Slf4j
@PluginDescriptor(name = "World Global Chat", description = "Talk anywhere!", tags = {
		"chat" })
public class RegionChatPlugin extends Plugin {
	@Inject
	private AblyManager ablyManager;

	@Inject
	private Client client;

	@Getter
	private final HashMap<String, ArrayList<String>> previousMessages = new HashMap<>();

	@Getter
	@Inject
	@Named("developerMode")
	private boolean developerMode;

	@Override
	protected void startUp() throws Exception {
		ablyManager.startConnection();
		ablyManager.connectToGlobal();
		ablyManager.connectToRegion(String.valueOf(client.getWorld()));

	}

	@Override
	protected void shutDown() throws Exception {
		ablyManager.closeConnection();
	}

	@Subscribe
	public void onWorldChanged(WorldChanged worldChanged) {
		ablyManager.closeConnection();
		ablyManager.startConnection();
		ablyManager.connectToGlobal();
		ablyManager.connectToRegion(String.valueOf(client.getWorld()));
	}

	@Subscribe
	public void onChatMessage(ChatMessage event) {
		String cleanedName = Text.sanitize(event.getName());
		String cleanedMessage = Text.removeTags(event.getMessage());

		boolean isPublic = event.getType().equals(ChatMessageType.PUBLICCHAT);

		boolean isLocalPlayerSendingMessage = cleanedName.equals(client.getLocalPlayer().getName());
		if (isPublic && isLocalPlayerSendingMessage) {
			ablyManager.shouldShowMessge(cleanedName, cleanedMessage, true);
			ablyManager.publishMessage(cleanedMessage, false, "");
		} else if (event.getType().equals(ChatMessageType.PRIVATECHATOUT)) {
			ablyManager.shouldShowMessge(client.getLocalPlayer().getName(), cleanedMessage, true);
			ablyManager.publishMessage(cleanedMessage, true, cleanedName);
		} else if (event.getType().equals(ChatMessageType.PRIVATECHAT)
				&& !ablyManager.shouldShowMessge(cleanedName, cleanedMessage, true)) {
			final ChatLineBuffer lineBuffer = client.getChatLineMap()
					.get(ChatMessageType.PRIVATECHAT.getType());
			lineBuffer.removeMessageNode(event.getMessageNode());
		} else {
			ablyManager.shouldShowMessge(cleanedName, cleanedMessage, true);
		}
	}

	@Provides
	RegionChatConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(RegionChatConfig.class);
	}
}
