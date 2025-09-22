/*
 * Copyright (c) 2018, TheLonelyDev <https://github.com/TheLonelyDev>
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * Copyright (c) 2020, ConorLeckey <https://github.com/ConorLeckey>
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
package com.tileman;

import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.ui.overlay.*;

import javax.inject.Inject;
import java.awt.*;
import java.util.Collection;

public class TilemanModeOverlay extends Overlay
{
	private static final int MAX_DRAW_DISTANCE = 32;

	private final Client client;
	private final TilemanModePlugin plugin;

	@Inject
	private TilemanModeConfig config;

	@Inject
	private TilemanModeOverlay(Client client, TilemanModeConfig config, TilemanModePlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(Overlay.PRIORITY_LOW);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{

		WorldPoint hoverTile = client.getSelectedSceneTile().getWorldLocation();

		plugin.updateWayfinder();

		// draw unlocked tiles not on path or being hovered
		final Collection<WorldPoint> points = plugin.getTilesToRender();
		for (final WorldPoint point : points)
		{
			Color border = getTileColor();
			Color fill = new Color(0, 0, 0, 32); // TODO - from config
			drawTile(graphics, point, border, fill);
		}

		// draw predictive path if shift isn't held
		final Collection<WorldPoint> path = plugin.pathToHoverTile;

		Boolean shortPath = path.size() <= 1;
		if (!client.isKeyPressed(KeyCode.KC_SHIFT) && !shortPath) {
			Boolean allUnlocked = points.containsAll(path);
			int tilesRequired = 0;
			for (final WorldPoint point : path) {

				// if whole path is unlocked we're always green
				if (allUnlocked) {
					Color border = new Color(0, 255, 0, 128); // TODO - from config
					Color fill = new Color(0, 255, 0, 32); // TODO - from config
					drawTile(graphics, point, border, fill);
					continue;
				}

				// draw tiles unlocked but on a path that contains locked tiles
				Boolean unlockedTile = points.contains(point);
				if (unlockedTile) {
					Color border = new Color(255, 185, 0, 64); // TODO - from config
					Color fill = new Color(255, 185, 0, 32); // TODO - from config
					drawTile(graphics, point, border, fill);
					continue;
				}

				// draw tiles requiring fresh unlock
				tilesRequired += 1;
				Color border = new Color(255, 0, 0, 64); // TODO - from config
				Color fill = new Color(255, 0, 0, 32); // TODO - from config
				drawTile(graphics, point, border, fill);
				LocalPoint lp = LocalPoint.fromWorld(client, point);
				Point canvasTextLocation = Perspective.getCanvasTextLocation(client, graphics, lp, String.valueOf(tilesRequired), 0);
				if (canvasTextLocation != null)
				{
					Color textColor = new Color(255, 255, 255, 255); // TODO - from config
					OverlayUtil.renderTextLocation(graphics, canvasTextLocation, String.valueOf(tilesRequired), textColor);
				}
			}
		}

		return null;
	}

	private void drawTile(Graphics2D graphics, WorldPoint point, Color border, Color fill)
	{
		WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();

		if (point.distanceTo(playerLocation) >= MAX_DRAW_DISTANCE)
		{
			return;
		}

		LocalPoint lp = LocalPoint.fromWorld(client, point);
		if (lp == null)
		{
			return;
		}

		Polygon poly = Perspective.getCanvasTilePoly(client, lp);
		if (poly == null)
		{
			return;
		}

		OverlayUtil.renderPolygon(graphics, poly, border, fill, graphics.getStroke());
	}

	private Color getTileColor() {
		if(config.enableTileWarnings()) {
			if (plugin.getRemainingTiles() <= 0) {
				return Color.RED;
			} else if (plugin.getRemainingTiles() <= config.warningLimit()) {
				return new Color(255, 153, 0);
			}
		}
		return config.markerColor();
	}
}
