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
import com.regionchat.overlay.RegionWidgetOverlay;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import javax.inject.Named;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Varbits;
import net.runelite.api.WorldType;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(name = "Ably Region Chat", description = "Talk to others even if they go to another fishing spot!", tags = {
		"chat" })
public class RegionChatPlugin extends Plugin {
	@Inject
	private AblyManager ablyManager;

	@Inject
	private Client client;

	@Inject
	private RegionChatConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private RegionWidgetOverlay regionWidgetOverlay;

	@Getter
	private final HashMap<String, ArrayList<String>> previousMessages = new HashMap<>();

	@Getter
	@Inject
	@Named("developerMode")
	private boolean developerMode;

	boolean inPvp;

	@Override
	protected void startUp() throws Exception {
		ablyManager.startConnection();
		ablyManager.connectToRegion(String.valueOf(client.getWorld()));

	}

	@Override
	protected void shutDown() throws Exception {
		ablyManager.closeConnection();
	}

	@Subscribe
	public void onChatMessage(ChatMessage event) {
		String cleanedName = Text.sanitize(event.getName());
		String cleanedMessage = Text.removeTags(event.getMessage());

		boolean isPublicOrPrivate = event.getType().equals(ChatMessageType.PUBLICCHAT)
				|| event.getType().equals(ChatMessageType.PRIVATECHATOUT);

		boolean isLocalPlayerSendingMessage = cleanedName.equals(client.getLocalPlayer().getName())
				|| event.getType().equals(ChatMessageType.PRIVATECHATOUT);

		if (isPublicOrPrivate && isLocalPlayerSendingMessage) {
			ablyManager.tryUpdateMessages(cleanedName, cleanedMessage);
			ablyManager.publishMessage(cleanedMessage);
		} else {
			ablyManager.tryUpdateMessages(cleanedName, cleanedMessage);

		}

	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted commandExecuted) {
		if (developerMode && commandExecuted.getCommand().equals("regionchat")) {
			if (commandExecuted.getArguments().length == 0 ||
					(Arrays.stream(commandExecuted.getArguments()).toArray()[0]).equals("hide")) {
				overlayManager.remove(regionWidgetOverlay);
			} else if ((Arrays.stream(commandExecuted.getArguments()).toArray()[0]).equals("show"))
				overlayManager.add(regionWidgetOverlay);
		}
	}

	@Provides
	RegionChatConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(RegionChatConfig.class);
	}
}
