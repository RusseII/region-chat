/*
 * Copyright (c) 2021, Zoinkwiz <https://github.com/Zoinkwiz>
 * Copyright (c) 2016-2017, Seth <Sethtroll3@gmail.com>
 * Copyright (c) 2018, Lotto <https://github.com/devLotto>
 * Copyright (c) 2019, Trevor <https://github.com/Trevor159>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.regionchat.overlay;

import com.regionchat.Region;
import com.regionchat.Zone;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

public class RegionWidgetOverlay extends OverlayPanel
{
	private final Client client;

	@Inject
	public RegionWidgetOverlay(Client client)
	{
		this.client = client;

		setPosition(OverlayPosition.DYNAMIC);
		setPriority(OverlayPriority.HIGHEST);
		setLayer(OverlayLayer.MANUAL);
		drawAfterInterface(WidgetID.WORLD_MAP_GROUP_ID);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Widget worldMapWidget = client.getWidget(WidgetInfo.WORLD_MAP_VIEW);

		if (worldMapWidget == null)
		{
			return null;
		}
		Rectangle worldMapRectangle = worldMapWidget.getBounds();

		graphics.setClip(worldMapRectangle);
		graphics.setColor(Color.CYAN);

		for (Region region : Region.values())
		{
			for (Zone zone : region.getZones())
			{
				Point bottomLeft = Perspective.mapWorldPointToGraphicsPoint(client, new WorldPoint(zone.getMinX(),
					zone.getMinY(), 0));

				Point bottomRight = Perspective.mapWorldPointToGraphicsPoint(client, new WorldPoint(zone.getMaxX(),
					zone.getMinY(), 0));

				Point topLeft = Perspective.mapWorldPointToGraphicsPoint(client, new WorldPoint(zone.getMinX(),
					zone.getMaxY(), 0));

				Point topRight = Perspective.mapWorldPointToGraphicsPoint(client, new WorldPoint(zone.getMaxX(),
					zone.getMaxY(), 0));

				renderWorldMapLine(graphics, bottomLeft, bottomRight);
				renderWorldMapLine(graphics, bottomLeft, topLeft);
				renderWorldMapLine(graphics, topLeft, topRight);
				renderWorldMapLine(graphics, bottomRight, topRight);
			}
		}

		return null;
	}

	public void renderWorldMapLine(Graphics2D graphics, Point startPoint, Point endPoint)
	{
		if (startPoint == null || endPoint == null)
		{
			return;
		}

		graphics.drawLine(startPoint.getX(), startPoint.getY(), endPoint.getX(), endPoint.getY());
	}
}
