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

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

public enum Region
{
	BARBARIAN_FISHING("barb-fishing-ba", new Zone(new WorldPoint(2495, 3474, 0), new WorldPoint(2527, 3532, 0))),
	TEMPOROSS("tempoross", true, new Zone(12078)),
	ZEAH_RC("zeahrc",
		new Zone(new WorldPoint(1672, 3814, 0), new WorldPoint(1858, 3903, 0)),
		new Zone(new WorldPoint(1636, 3848, 0), new WorldPoint(1671, 3902, 0))
	),
	MOTHERLODE_MINE("motherlode", new Zone(14936)),
	ZALCANO("zalcano", new Zone(12116)),
	SEPULCHRE("sepulchre", new Zone(new WorldPoint(2220, 5760, 0), new WorldPoint(2591, 6039, 3))),
	SULLIUSCEP("sulliuscep", new Zone(new WorldPoint(3627, 3725, 0), new WorldPoint(3697, 3811, 0))),
	ZEAH_CATACOMBS("zeah-catacombs", new Zone(new WorldPoint(1599, 9983, 0), new WorldPoint(1730, 10115, 0))),
	WYRMS("wyrms", new Zone(new WorldPoint(1248, 10144, 0), new WorldPoint(1300, 10209, 0)));

	@Getter
	private final List<Zone> zones;

	@Getter
	private final String name;

	@Getter
	private final boolean isInstance;

	Region(String name, Zone... zone)
	{
		this.name = name;
		this.zones = Arrays.asList(zone);
		this.isInstance = false;
	}

	Region(String name, boolean isInstance, Zone... zone)
	{
		this.name = name;
		this.zones = Arrays.asList(zone);
		this.isInstance = isInstance;
	}

	public int getInstancedRegionID(WorldPoint realPlayerPoint, WorldPoint instancePlayerPoint)
	{
		WorldPoint minPoint = this.getZones().get(0).getMinWorldPoint();

		int xDiff = instancePlayerPoint.getX() - minPoint.getX();
		int yDiff = instancePlayerPoint.getY() - minPoint.getY();
		int realMinPointX = realPlayerPoint.getX() - xDiff;
		int realMinPointY = realPlayerPoint.getY() - yDiff;

		WorldPoint realMinPoint = new WorldPoint(realMinPointX, realMinPointY, 0);

		return realMinPoint.getRegionID();
	}
}
