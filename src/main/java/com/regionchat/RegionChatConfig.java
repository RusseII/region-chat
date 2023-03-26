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

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.ui.JagexColors;

@ConfigGroup("regionchat")
public interface RegionChatConfig extends Config
{
	@ConfigItem(
		keyName = "regionChatColour",
		name = "Region chat colour",
		description = "The colour the region messages should appear in"
	)
	default Color regionChatColour()
	{
		return JagexColors.CHAT_PUBLIC_TEXT_OPAQUE_BACKGROUND;
	}

	@ConfigItem(
		keyName = "shouldShowStateChanges",
		name = "Show enter/leave messages for regions",
		description = "Receive a message saying whenever you've entered/left a region"
	)
	default boolean shouldShowStateChanges()
	{
		return true;
	}

	@ConfigSection(
		position = 1,
		name = "Regions",
		description = "Which regions to have chat in"
	)
	String regions = "regionsSection";

	@ConfigItem(
		keyName = "barbFishingBaRegion",
		name = "Region chat at Barb Fishing",
		description = "Join region chat at Barbarian Fishing locations",
		section = regions
	)
	default boolean barbFishingBaRegion()
	{
		return true;
	}

	@ConfigItem(
		keyName = "temporossRegion",
		name = "Region chat at Tempoross",
		description = "Join region chat at Tempoross",
		section = regions
	)
	default boolean temporossRegion()
	{
		return true;
	}

	@ConfigItem(
		keyName = "zeahRcRegion",
		name = "Region chat whilst Zeah Runecrafting",
		description = "Join region chat at the Zeah Runecrafting areas",
		section = regions
	)
	default boolean zeahRcRegion()
	{
		return true;
	}

	@ConfigItem(
		keyName = "zeahCatacombRegion",
		name = "Region chat whilst in the Zeah Catacombs",
		description = "Join region chat at the Zeah Catacombs",
		section = regions
	)
	default boolean zeahCatacombRegion()
	{
		return true;
	}

	@ConfigItem(
		keyName = "wyrmRegion",
		name = "Region chat whilst at Wyrms",
		description = "Join region chat at Wyrms",
		section = regions
	)
	default boolean wyrmRegion()
	{
		return true;
	}

	@ConfigItem(
		keyName = "motherlodeMineRegion",
		name = "Region chat in the Motherlode Mine",
		description = "Join region chat at the Motherlode Mine",
		section = regions
	)
	default boolean motherlodeMineRegion()
	{
		return true;
	}

	@ConfigItem(
		keyName = "sepulchreRegion",
		name = "Region chat in the Sepulchre",
		description = "Join region chat at the Sepulchre",
		section = regions
	)
	default boolean sepulchreRegion()
	{
		return true;
	}

	@ConfigItem(
		keyName = "zalcanoRegion",
		name = "Region chat at Zalcano",
		description = "Join region chat at Zalcano",
		section = regions
	)
	default boolean zalcanoRegion()
	{
		return true;
	}

	@ConfigItem(
		keyName = "sulliuscepRegion",
		name = "Region chat at Sulliusceps",
		description = "Join region chat at Sulliusceps",
		section = regions
	)
	default boolean sulliuscepRegion()
	{
		return true;
	}
}
