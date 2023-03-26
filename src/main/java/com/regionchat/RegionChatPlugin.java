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
@PluginDescriptor(
	name = "Ably Region Chat",
	description = "Talk to others even if they go to another fishing spot!",
	tags = { "chat" }
)
public class RegionChatPlugin extends Plugin
{
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
	protected void startUp() throws Exception
	{
		initRegions();
		ablyManager.startConnection();
	}

	@Override
	protected void shutDown() throws Exception
	{
		ablyManager.closeConnection();
	}

	// TODO: If not logged in, close channel

	@Subscribe
	public void onGameTick(GameTick event)
	{
		LocalPoint currentPos = client.getLocalPlayer().getLocalLocation();
		WorldPoint currentWorldPos = client.getLocalPlayer().getWorldLocation();

		WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, currentPos);

		boolean foundRegion = false;

		for (Region region : Region.values())
		{
			boolean validRegion = false;
			try
			{
				validRegion = (boolean) regionsToConfigs.get(region).call();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}

			if (validRegion && region.getZones().stream().anyMatch((zone) -> zone.contains(worldPoint)))
			{
				int regionID = region.getInstancedRegionID(currentWorldPos, worldPoint);
				foundRegion = true;
				String channelName = "";
				channelName += client.getWorld();
				if (region.isInstance())
				{
					channelName +=  ":" + regionID;
				}

				ablyManager.connectToRegion(region, channelName);
			}
		}

		if (!foundRegion)
		{
			ablyManager.disconnectFromRegions();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged e)
	{
		inPvp = client.getVar(Varbits.PVP_SPEC_ORB) == 1;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		EnumSet<WorldType> wt = client.getWorldType();

		if (wt.contains(WorldType.BOUNTY) ||
			wt.contains(WorldType.DEADMAN) ||
			wt.contains(WorldType.PVP) ||
			inPvp
		)
		{
			return;
		}

		String cleanedName = Text.sanitize(event.getName());
		String cleanedMessage = Text.removeTags(event.getMessage());


		if (event.getType() != ChatMessageType.PUBLICCHAT ||
			!cleanedName.equals(client.getLocalPlayer().getName()))
		{
			return;
		}

		ablyManager.tryUpdateMessages(cleanedName, cleanedMessage);
		ablyManager.publishMessage(cleanedMessage);
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted commandExecuted)
	{
		if (developerMode && commandExecuted.getCommand().equals("regionchat"))
		{
			if (commandExecuted.getArguments().length == 0 ||
				(Arrays.stream(commandExecuted.getArguments()).toArray()[0]).equals("hide"))
			{
				overlayManager.remove(regionWidgetOverlay);
			}
			else if ((Arrays.stream(commandExecuted.getArguments()).toArray()[0]).equals("show"))
				overlayManager.add(regionWidgetOverlay);
		}
	}

	@Provides
	RegionChatConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RegionChatConfig.class);
	}

	@Getter
	private final Map<Region, Callable> regionsToConfigs = new HashMap<>();

	private void initRegions()
	{
		regionsToConfigs.put(Region.BARBARIAN_FISHING, config::barbFishingBaRegion);
		regionsToConfigs.put(Region.ZEAH_RC, config::zeahRcRegion);
		regionsToConfigs.put(Region.TEMPOROSS, config::temporossRegion);
		regionsToConfigs.put(Region.MOTHERLODE_MINE, config::motherlodeMineRegion);
		regionsToConfigs.put(Region.ZALCANO, config::zalcanoRegion);
		regionsToConfigs.put(Region.SEPULCHRE, config::sepulchreRegion);
		regionsToConfigs.put(Region.SULLIUSCEP, config::sulliuscepRegion);
		regionsToConfigs.put(Region.ZEAH_CATACOMBS, config::zeahCatacombRegion);
		regionsToConfigs.put(Region.WYRMS, config::wyrmRegion);
	}
}
