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

import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.*;

import javax.inject.Inject;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;

public class TilemanModeOverlay extends Overlay
{
	private static final int MAX_DRAW_DISTANCE = 32;
	private final Client client;
	private final TilemanModePlugin plugin;
	private final Timer timer = new Timer();
	private float dashPhase = 0f;

	private final TilemanPath wayfinder;
	private WorldPoint lastPathStart = new WorldPoint(0,0,0);
	private WorldPoint lastPathEnd = new WorldPoint(0,0,0);
	private List<WorldPoint> pathToHoverTile;
	@Inject
	private TilemanModeConfig config;

	@Inject
	private TilemanModeOverlay(Client client, TilemanModeConfig config, TilemanModePlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.wayfinder = new TilemanPath(plugin);

		// define RuneLite rendering params
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(Overlay.PRIORITY_LOW);
		setLayer(OverlayLayer.ABOVE_SCENE);

		// Update the animation timer at a regular interval to make paths animate
		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				updateDashPhase();
			}
		}, 75, 75);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		// refresh the path if player or target has changed location
		updatePathIfOutdated();

		// Don't draw paths if menu option isn't walk here
		MenuEntry[] menuEntries = client.getMenu().getMenuEntries();
		// last element is the default left click option
		String option = menuEntries[menuEntries.length-1].getOption();
		Boolean shortCircuit = !option.startsWith("Walk here");

		// fetch the last navigation path generated, remain empty when shift is held
		Boolean shiftIsHeld = client.isKeyPressed(KeyCode.KC_SHIFT);
		final Collection<WorldPoint> pathTiles = (shiftIsHeld || shortCircuit) ? Collections.emptyList() : pathToHoverTile;

		// build subset of tiles outside the path to render
		Set<WorldPoint> simpleTiles = new HashSet<>(plugin.getTilesToRender());
		simpleTiles.removeAll(pathTiles);

		// render non path tile squares
		for (WorldPoint tile : simpleTiles) {
			Color border = getTileColor();
			Color fill = new Color(0, 0, 0, 32); // TODO - from config
			drawTile(graphics, tile, border, fill, getSolidLine());
		}

		// render path tiles
		int tilesRequired = 0;
		Boolean allUnlocked = plugin.getTilesToRender().containsAll(pathTiles);
		for (WorldPoint tile : pathTiles) {

			// render claimed path tiles as ordinary path tile squares if shift is held
			Boolean tileIsClaimed = plugin.getTilesToRender().contains(tile);
			if (shiftIsHeld && tileIsClaimed) {
				Color border = getTileColor();
				Color fill = new Color(0, 0, 0, 32); // TODO - from config
				drawTile(graphics, tile, border, fill, getSolidLine());
				continue;
			}

			// if whole path is unlocked highlight the path to hover tile
			if (allUnlocked) {
				Color border = new Color(0, 255, 0, 64); // TODO - from config
				Color fill = new Color(0, 255, 0, 16); // TODO - from config
				drawTile(graphics, tile, border, fill, getDashedLine());
				Color textColor = new Color(180, 180, 180); // TODO - from config
				continue;
			}

			// render partially claimed paths
			if (tileIsClaimed) {
				Color border = new Color(180, 180, 180, 96); // TODO - from config
				Color fill = new Color(180, 180, 180, 16); // TODO - from config
				drawTile(graphics, tile, border, fill, getSolidLine());
				Color textColor = new Color(180, 180, 180); // TODO - from config
				continue;
			}

			// dont render unclaimed tiles while shift is held
			if (shiftIsHeld){
				continue;
			}

			// draw tiles requiring fresh unlock
			tilesRequired += 1;
			Color border = new Color(180, 180, 180, 96); // TODO - from config
			Color fill = new Color(180, 180, 180, 16); // TODO - from config
			drawTile(graphics, tile, border, fill, getDashedLine());

			// draw tile cost to unlock
			Color textColor = new Color(200, 200, 200); // TODO - from config
			drawTileText(graphics, tile, textColor, String.valueOf(tilesRequired));

		}

		return null;
	}

	private void drawTileText(Graphics2D graphics, WorldPoint tile, Color color, String label)
	{
		if (label == ""){
			return;
		}

		LocalPoint lp = LocalPoint.fromWorld(client, tile);
		Point canvasCountLocation = Perspective.getCanvasTextLocation(client, graphics, lp, label, 0);
		if (canvasCountLocation != null)
		{
			OverlayUtil.renderTextLocation(graphics, canvasCountLocation, label, color);
		}
	}

	private void drawTile(Graphics2D graphics, WorldPoint point, Color border, Color fill, BasicStroke stroke)
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

		insetTilePoly(poly);

		OverlayUtil.renderPolygon(graphics, poly, border, fill, stroke);
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

	private BasicStroke getSolidLine() {
		return new BasicStroke();
	}

	private BasicStroke getDashedLine() {
		float[] dashPattern = {10.0f, 5.0f}; // 10 units on, 5 units off
		return new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, dashPattern, dashPhase);
	}

	private void updateDashPhase(){
		dashPhase += 1.0f;
		if (dashPhase > 15.0f) { // must be total length of dashPattern to loop smoothly
			dashPhase = 0;
		}
	}

	private void updatePathIfOutdated(){
		Instant startTime = Instant.now();
		WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();
		WorldPoint hoverTile = client.getSelectedSceneTile().getWorldLocation();
		Boolean playerMoved = !lastPathStart.equals(playerLocation);
		Boolean hoverTileChanged = !lastPathEnd.equals(hoverTile);
		Boolean recalculate = playerMoved || hoverTileChanged;

		if (recalculate){
			lastPathStart = playerLocation;
			lastPathEnd = hoverTile;
			pathToHoverTile = wayfinder.findPath(playerLocation, hoverTile);
			plugin.durationLastWayfind = Duration.between(startTime, Instant.now());
		}
	}

	private void insetTilePoly(Polygon source)
	{
		int centreX = (source.xpoints[0] + source.xpoints[1] + source.xpoints[2] + source.xpoints[3]) / 4;
		int centreY = (source.ypoints[0] + source.ypoints[1] + source.ypoints[2] + source.ypoints[3]) / 4;

		// 20% inset
		source.xpoints[0] = (source.xpoints[0] * 4 + centreX) / 5;
		source.xpoints[1] = (source.xpoints[1] * 4 + centreX) / 5;
		source.xpoints[2] = (source.xpoints[2] * 4 + centreX) / 5;
		source.xpoints[3] = (source.xpoints[3] * 4 + centreX) / 5;

		source.ypoints[0] = (source.ypoints[0] * 4 + centreY) / 5;
		source.ypoints[1] = (source.ypoints[1] * 4 + centreY) / 5;
		source.ypoints[2] = (source.ypoints[2] * 4 + centreY) / 5;
		source.ypoints[3] = (source.ypoints[3] * 4 + centreY) / 5;

	}

	public void updateRenderConfigCache() {
		// because render is invoked extremely frequently, we don't want to config read often
		// instead we cache the values read into variables and update when changed
	}
}
