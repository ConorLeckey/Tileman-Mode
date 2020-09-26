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

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
        name = "Tileman Mode",
        description = "Automatically draws tiles where you walk",
        tags = {"overlay", "tiles"}
)
public class TilemanModePlugin extends Plugin {
    private static final String CONFIG_GROUP = "groundMarker";
    private static final String MARK = "Mark tile";
    private static final String UNMARK = "Unmark tile";
    private static final String WALK_HERE = "Walk here";
    private static final String REGION_PREFIX = "region_";

    private static final Gson GSON = new Gson();

    @Getter(AccessLevel.PACKAGE)
    private final List<WorldPoint> points = new ArrayList<>();

    @Inject
    private Client client;

    @Inject
    private ConfigManager configManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private TilemanModeOverlay overlay;

    @Inject
    private TilemanModeMinimapOverlay minimapOverlay;

    @Inject
    private TileInfoOverlay infoOverlay;

    private int totalTiles, remainingTiles;
    private LocalPoint lastTile;

    private void savePoints(int regionId, Collection<GroundMarkerPoint> points) {
        if (points == null || points.isEmpty()) {
            configManager.unsetConfiguration(CONFIG_GROUP, REGION_PREFIX + regionId);
            return;
        }

        String json = GSON.toJson(points);
        configManager.setConfiguration(CONFIG_GROUP, REGION_PREFIX + regionId, json);
    }

    private Collection<GroundMarkerPoint> getPoints(int regionId) {
        updateTileCounter();
        return getGroundMarkerConfiguration(REGION_PREFIX + regionId);
    }

    private void updateTileCounter() {
        List<String> regions = configManager.getConfigurationKeys("groundMarker.region");
        int totalTiles = 0;
        for (String region : regions) {
            Collection<GroundMarkerPoint> regionTiles = getGroundMarkerConfiguration(region.substring(CONFIG_GROUP.length() + 1));

            totalTiles += regionTiles.size();
        }
        if (totalTiles > 0) {
            updateTotalTilesUsed(totalTiles);
            updateRemainingTiles(totalTiles);
        }
    }

    private Collection<GroundMarkerPoint> getGroundMarkerConfiguration(String key) {
        String json = configManager.getConfiguration(CONFIG_GROUP, key);

        if (Strings.isNullOrEmpty(json)) {
            return Collections.emptyList();
        }

        return GSON.fromJson(json, new TypeToken<List<GroundMarkerPoint>>() {
        }.getType());
    }

    @Provides
    TilemanModeConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TilemanModeConfig.class);
    }

    private void loadPoints() {
        points.clear();

        int[] regions = client.getMapRegions();

        if (regions == null) {
            return;
        }

        for (int regionId : regions) {
            // load points for region
            log.debug("Loading points for region {}", regionId);
            Collection<GroundMarkerPoint> regionPoints = getPoints(regionId);
            Collection<WorldPoint> worldPoint = translateToWorldPoint(regionPoints);
            points.addAll(worldPoint);
        }
    }

    private Collection<WorldPoint> translateToWorldPoint(Collection<GroundMarkerPoint> points) {
        if (points.isEmpty()) {
            return Collections.emptyList();
        }

        return points.stream()
                .map(point -> WorldPoint.fromRegion(point.getRegionId(), point.getRegionX(), point.getRegionY(), point.getZ()))
                .collect(Collectors.toList());
    }

    @Override
    protected void startUp() {
        overlayManager.add(overlay);
        overlayManager.add(minimapOverlay);
        overlayManager.add(infoOverlay);
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        overlayManager.remove(minimapOverlay);
        overlayManager.remove(infoOverlay);
        points.clear();
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (event.getMenuAction().getId() != MenuAction.RUNELITE.getId() ||
                !(event.getMenuOption().equals(MARK) || event.getMenuOption().equals(UNMARK))) {
            return;
        }

        Tile target = client.getSelectedSceneTile();
        if (target == null) {
            return;
        }
        handleMenuOption(target.getLocalLocation(), event.getMenuOption().equals(MARK));
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        final boolean hotKeyPressed = client.isKeyPressed(KeyCode.KC_SHIFT);
        if (hotKeyPressed && event.getOption().equals(WALK_HERE)) {
            final Tile selectedSceneTile = client.getSelectedSceneTile();

            if (selectedSceneTile == null) {
                return;
            }

            MenuEntry[] menuEntries = client.getMenuEntries();
            menuEntries = Arrays.copyOf(menuEntries, menuEntries.length + 1);
            MenuEntry menuEntry = menuEntries[menuEntries.length - 1] = new MenuEntry();

            final WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, selectedSceneTile.getLocalLocation());
            final int regionId = worldPoint.getRegionID();
            final GroundMarkerPoint point = new GroundMarkerPoint(regionId, worldPoint.getRegionX(), worldPoint.getRegionY(), client.getPlane());

            menuEntry.setOption(getPoints(regionId).contains(point) ? UNMARK : MARK);
            menuEntry.setTarget(event.getTarget());
            menuEntry.setType(MenuAction.RUNELITE.getId());

            client.setMenuEntries(menuEntries);
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        if (gameStateChanged.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        loadPoints();
    }

    private void updateTotalTilesUsed(int totalTilesCount) {
        totalTiles = totalTilesCount;
    }

    private void updateRemainingTiles(int totalTilesCount) {
        int remainingTilesCount = (int) client.getOverallExperience() / 1000 - totalTilesCount;
        if (config.includeTotalLevel()) {
            remainingTilesCount += client.getTotalLevel();
        }
        remainingTiles = remainingTilesCount + config.tilesOffset();
    }

    int getTotalTiles() {
        return totalTiles;
    }

    int getRemainingTiles() {
        return remainingTiles;
    }

    void handleMenuOption(LocalPoint selectedPoint, boolean markedValue) {
        if (selectedPoint == null) {
            return;
        }
        updateTileMark(selectedPoint, markedValue);
    }

    void handleMovement(LocalPoint currentPlayerPoint) {
        if (currentPlayerPoint == null || !config.automarkTiles() || client.isInInstancedRegion()) {
            return;
        }

        // Mark the tile they walked to
        updateTileMark(currentPlayerPoint, true);

        // If player moves 2 tiles in a straight line, fill in the middle tile
        // TODO Fill path between last point and current point. This will fix missing tiles that occur when you lag
        // TODO   and rendered frames are skipped. See if RL has an api that mimic's OSRS's pathing. If so, use that to
        // TODO   set all tiles between current tile and lastTile as marked
        if (lastTile != null
                && (lastTile.distanceTo(currentPlayerPoint) == 256 || lastTile.distanceTo(currentPlayerPoint) == 362)) {
            int xDiff = lastTile.getX() - currentPlayerPoint.getX();
            int yDiff = lastTile.getY() - currentPlayerPoint.getY();
            int yModifier = yDiff / 2;
            int xModifier = xDiff / 2;

            updateTileMark(new LocalPoint(currentPlayerPoint.getX() + xModifier, currentPlayerPoint.getY() + yModifier), true);
        }
        lastTile = currentPlayerPoint;
    }

    void updateTileMark(LocalPoint localPoint, boolean markedValue) {
        WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, localPoint);

        int regionId = worldPoint.getRegionID();
        GroundMarkerPoint point = new GroundMarkerPoint(regionId, worldPoint.getRegionX(), worldPoint.getRegionY(), client.getPlane());
        log.debug("Updating point: {} - {}", point, worldPoint);

        List<GroundMarkerPoint> groundMarkerPoints = new ArrayList<>(getPoints(regionId));

        if(markedValue) {
            // Try add tile
            if(!groundMarkerPoints.contains(point) && remainingTiles > 0) {
                groundMarkerPoints.add(point);
            }
        } else {
            // Try remove tile
            groundMarkerPoints.remove(point);
        }

        savePoints(regionId, groundMarkerPoints);
        loadPoints();
    }

    void markTile(LocalPoint localPoint, boolean menuOption) {
        if (localPoint == null || client.isInInstancedRegion() || (lastTile != null && lastTile.distanceTo(localPoint) == 0)) {
            return;
        }

        // If player moves 2 tiles in a straight line, fill in the middle tile
        if (!menuOption && lastTile != null
                && (lastTile.distanceTo(localPoint) == 256 || lastTile.distanceTo(localPoint) == 362)) {
            int xDiff = lastTile.getX() - localPoint.getX();
            int yDiff = lastTile.getY() - localPoint.getY();
            int yModifier = yDiff / 2;
            int xModifier = xDiff / 2;

            markTile(new LocalPoint(localPoint.getX() + xModifier, localPoint.getY() + yModifier), false);
        }
        lastTile = localPoint;

        WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, localPoint);

        int regionId = worldPoint.getRegionID();
        GroundMarkerPoint point = new GroundMarkerPoint(regionId, worldPoint.getRegionX(), worldPoint.getRegionY(), client.getPlane());
        log.debug("Updating point: {} - {}", point, worldPoint);

        List<GroundMarkerPoint> groundMarkerPoints = new ArrayList<>(getPoints(regionId));
        if (groundMarkerPoints.contains(point)) {
            if (menuOption) {
                groundMarkerPoints.remove(point);
            } else {
                return;
            }
        } else if (remainingTiles > 0) {
            groundMarkerPoints.add(point);
        }

        savePoints(regionId, groundMarkerPoints);

        loadPoints();
    }
}
