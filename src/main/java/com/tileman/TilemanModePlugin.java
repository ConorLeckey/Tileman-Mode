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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import org.apache.commons.lang3.StringUtils;

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
    private static final String CONFIG_GROUP = "tilemanMode";
    private static final String MARK = "Unlock Tileman tile";
    private static final String UNMARK = "Clear Tileman tile";
    private static final String WALK_HERE = "Walk here";
    private static final String REGION_PREFIX = "region_";

    private static final Gson GSON = new Gson();

    @Getter(AccessLevel.PACKAGE)
    private final List<WorldPoint> points = new ArrayList<>();

    @Inject
    private Client client;

    @Inject
    private TilemanModeConfigEvaluator config;

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

    @Inject
    private ClientToolbar clientToolbar;

    @Provides
    TilemanModeConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TilemanModeConfig.class);
    }

    private int totalTilesUsed, remainingTiles, xpUntilNextTile;
    private LocalPoint lastTile;
    private TilemanImportPanel panel;
    private NavigationButton navButton;
    private boolean panelEnabled = false;
    private long totalXp;

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
            final TilemanModeTile point = new TilemanModeTile(regionId, worldPoint.getRegionX(), worldPoint.getRegionY(), client.getPlane());

            menuEntry.setOption(getTiles(regionId).contains(point) ? UNMARK : MARK);
            menuEntry.setTarget(event.getTarget());
            menuEntry.setType(MenuAction.RUNELITE.getId());

            client.setMenuEntries(menuEntries);
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        final WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
        if (playerPos == null) {
            return;
        }

        final LocalPoint playerPosLocal = LocalPoint.fromWorld(client, playerPos);
        if (playerPosLocal == null) {
            return;
        }

        long currentTotalXp = client.getOverallExperience();

        // If we have no last tile, we probably just spawned in, so make sure we walk on our current tile
        if (lastTile == null || lastTile.distanceTo(playerPosLocal) != 0) {
            // Player moved
            handleWalkedToTile(playerPosLocal);
            lastTile = playerPosLocal;
            updateTileCounter();
            log.debug("player moved");
            log.debug("last tile={}  distance={}", lastTile, lastTile==null ? "null" : lastTile.distanceTo(playerPosLocal));
        } else if (totalXp != currentTotalXp) {
            updateTileCounter();
            totalXp = currentTotalXp;
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        if (gameStateChanged.getGameState() != GameState.LOGGED_IN) {
            lastTile = null;
            return;
        }
        loadPoints();
        updateTileCounter();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        // Check if automark tiles is on, and if so attempt to step on current tile
        final WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
        final LocalPoint playerPosLocal = LocalPoint.fromWorld(client, playerPos);
        if(playerPosLocal != null && config.automarkTiles()) {
            handleWalkedToTile(playerPosLocal);
        }
        updateTileCounter();
    }

    @Override
    protected void startUp() {
        overlayManager.add(overlay);
        overlayManager.add(minimapOverlay);
        overlayManager.add(infoOverlay);
        loadPoints();
        updateTileCounter();
        log.debug("startup");
        panel = new TilemanImportPanel(this);
        navButton = NavigationButton.builder()
                .tooltip("Tileman Import")
                .icon(ImageUtil.getResourceStreamFromClass(getClass(), "/icon.png"))
                .priority(70)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        overlayManager.remove(minimapOverlay);
        overlayManager.remove(infoOverlay);
        points.clear();
    }

    public void importGroundMarkerTiles() {
        // Get and store all the Ground Markers Regions
        List<String> groundMarkerRegions = removeRegionPrefixes(configManager.getConfigurationKeys("groundMarker.region"));
        // If none, Exit function

        // Get and store array list of existing Tileman World Regions (like updateTileCounter does)
        List<String> tilemanModeRegions = removeRegionPrefixes(configManager.getConfigurationKeys(CONFIG_GROUP + ".region"));

        // CONVERSION
        // Loop through Ground Marker Regions
        for (String region: groundMarkerRegions) {
            // Get Ground Markers Region's Tiles
            ArrayList<TilemanModeTile> groundMarkerTiles =
                    new ArrayList<>(getConfiguration("groundMarker", REGION_PREFIX + region));
            // If region already exists in Tileman World Regions Array:
            if(tilemanModeRegions.contains(region)) {
                // Create Empty ArrayList for Region;
                // Get Tileman Region's tiles and add them to the region array list
                ArrayList<TilemanModeTile> regionTiles = new ArrayList<>(getTiles(region));

                // Create int for regionOriginalSize;
                // Set regionOriginalSize to arraylists length
                int regionOriginalSize = regionTiles.size();

                // Loop through Ground Markers Points
                for(TilemanModeTile groundMarkerTile: groundMarkerTiles) {
                    // If Ground Marker point already exists in Tileman World Region: Break loop iteration
                    if(regionTiles.contains(groundMarkerTile)){
                        continue;
                    }
                    // Add point to array list
                    regionTiles.add(groundMarkerTile);
                }
                // If regionOriginalSize != current size
                if(regionOriginalSize != regionTiles.size()) {
                    // Save points for arrayList
                    savePoints(Integer.parseInt(region), regionTiles);
                }
            } else {
                // Save points for that region
                savePoints(Integer.parseInt(region), groundMarkerTiles);
            }
        }
    }

    private List<String> removeRegionPrefixes(List<String> regions) {
        List<String> trimmedRegions = new ArrayList<String>();
        for (String region : regions) {
            trimmedRegions.add(removeRegionPrefix(region));
        }
        return trimmedRegions;
    }

    private String removeRegionPrefix(String region) {
        return region.substring(region.indexOf('_') + 1);
    }

    private Collection<TilemanModeTile> getTiles(int regionId) {
        return getConfiguration(CONFIG_GROUP, REGION_PREFIX + regionId);
    }

    private Collection<TilemanModeTile> getTiles(String regionId) {
        return getConfiguration(CONFIG_GROUP, REGION_PREFIX + regionId);
    }

    private void updateTileCounter() {
        List<String> regions = configManager.getConfigurationKeys(CONFIG_GROUP + ".region");
        int totalTiles = 0;
        for (String region : regions) {
            Collection<TilemanModeTile> regionTiles = getTiles(removeRegionPrefix(region));

            totalTiles += regionTiles.size();
        }

        log.debug("Updating tile counter");

        updateTotalTilesUsed(totalTiles);
        updateRemainingTiles(totalTiles);
        updateXpUntilNextTile();
    }

    private void updateTotalTilesUsed(int totalTilesCount) {
        totalTilesUsed = totalTilesCount;
    }

    private void updateRemainingTiles(int placedTiles) {
        // Start with tiles offset. We always get these
        int earnedTiles = config.tilesOffset();

        // If including xp, add those tiles in
        if(!config.excludeExp()){
            earnedTiles += (int) client.getOverallExperience() / 1000;
        }

        // If including total level, add those tiles in
        if (config.includeTotalLevel()) {
            earnedTiles += client.getTotalLevel();
        }

        remainingTiles = earnedTiles - placedTiles;
    }

    private void updateXpUntilNextTile() {
        xpUntilNextTile = 1000 - Integer.parseInt(StringUtils.right(Long.toString(client.getOverallExperience()), 3));
    }

    private Collection<TilemanModeTile> getConfiguration(String configGroup, String key) {
        String json = configManager.getConfiguration(configGroup, key);

        if (Strings.isNullOrEmpty(json)) {
            return Collections.emptyList();
        }

        return GSON.fromJson(json, new TypeToken<List<TilemanModeTile>>() {
        }.getType());
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
            Collection<WorldPoint> worldPoint = translateToWorldPoint(getTiles(regionId));
            points.addAll(worldPoint);
        }
        updateTileCounter();
    }

    private void savePoints(int regionId, Collection<TilemanModeTile> points) {
        if (points == null || points.isEmpty()) {
            configManager.unsetConfiguration(CONFIG_GROUP, REGION_PREFIX + regionId);
            return;
        }

        String json = GSON.toJson(points);
        configManager.setConfiguration(CONFIG_GROUP, REGION_PREFIX + regionId, json);
    }

    private Collection<WorldPoint> translateToWorldPoint(Collection<TilemanModeTile> points) {
        if (points.isEmpty()) {
            return Collections.emptyList();
        }

        return points.stream()
                .map(point -> WorldPoint.fromRegion(point.getRegionId(), point.getRegionX(), point.getRegionY(), point.getZ()))
                .collect(Collectors.toList());
    }

    int getTotalTiles() {
        return totalTilesUsed;
    }

    int getRemainingTiles() {
        return remainingTiles;
    }

    private void handleMenuOption(LocalPoint selectedPoint, boolean markedValue) {
        if (selectedPoint == null) {
            return;
        }
        updateTileMark(selectedPoint, markedValue);
    }

    private void handleWalkedToTile(LocalPoint currentPlayerPoint) {
        if (currentPlayerPoint == null ||
                !config.automarkTiles() ||
                client.isInInstancedRegion()) {
            return;
        }

        // Mark the tile they walked to
        updateTileMark(currentPlayerPoint, true);

        // If player moves 2 tiles in a straight line, fill in the middle tile
        // TODO Fill path between last point and current point. This will fix missing tiles that occur when you lag
        // TODO   and rendered frames are skipped. See if RL has an api that mimic's OSRS's pathing. If so, use that to
        // TODO   set all tiles between current tile and lastTile as marked
        if (lastTile != null
                && (lastTile.distanceTo(currentPlayerPoint) == 256
                || lastTile.distanceTo(currentPlayerPoint) == 286
                || lastTile.distanceTo(currentPlayerPoint) == 362)) {
            int xDiff = currentPlayerPoint.getX() - lastTile.getX();
            int yDiff = currentPlayerPoint.getY() - lastTile.getY();
            int yModifier = yDiff / 2;
            int xModifier = xDiff / 2;

            if(lastTile.distanceTo(currentPlayerPoint) == 286) {
                int tileBesideXDiff, tileBesideYDiff;

                // Whichever direction has moved only one, keep it 0. This is the translation to the potential 'problem' gameObject
                if(Math.abs(yDiff) == 128) {
                    tileBesideXDiff = xDiff;
                    tileBesideYDiff = 0;
                } else {
                    tileBesideXDiff = 0;
                    tileBesideYDiff = yDiff;
                }

                LocalPoint pointBeside = new LocalPoint(lastTile.getX() + tileBesideXDiff, lastTile.getY() + tileBesideYDiff);

                CollisionData[] collisionData = client.getCollisionMaps();
                assert collisionData != null;
                int[][] collisionDataFlags = collisionData[client.getPlane()].getFlags(); // Add movement flags when available

                if( collisionDataFlags[pointBeside.getSceneX()][pointBeside.getSceneY()] == 0) {
                    log.info(MovementFlag.getSetFlags(collisionDataFlags[pointBeside.getSceneX()][pointBeside.getSceneY()]).toString());
                    updateTileMark(new LocalPoint(lastTile.getX() + tileBesideXDiff / 2, lastTile.getY() + tileBesideYDiff / 2), true);
                } else {
                    log.info(MovementFlag.getSetFlags(collisionDataFlags[pointBeside.getSceneX()][pointBeside.getSceneY()]).toString());
                    if(yModifier == 64) {
                        yModifier = 128;
                    } else  if(xModifier == 64) {
                        xModifier = 128;
                    }
                    updateTileMark(new LocalPoint(lastTile.getX() + xModifier, lastTile.getY() + yModifier), true);
                }

            } else {
                updateTileMark(new LocalPoint(lastTile.getX() + xModifier, lastTile.getY() + yModifier), true);
            }
        }
    }

    private void updateTileMark(LocalPoint localPoint, boolean markedValue) {
        WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, localPoint);

        int regionId = worldPoint.getRegionID();
        TilemanModeTile point = new TilemanModeTile(regionId, worldPoint.getRegionX(), worldPoint.getRegionY(), client.getPlane());
        log.debug("Updating point: {} - {}", point, worldPoint);

        List<TilemanModeTile> tilemanModeTiles = new ArrayList<>(getTiles(regionId));

        if (markedValue) {
            // Try add tile
            if (!tilemanModeTiles.contains(point) && remainingTiles > 0) {
                tilemanModeTiles.add(point);
            }
        } else {
            // Try remove tile
            tilemanModeTiles.remove(point);
        }

        savePoints(regionId, tilemanModeTiles);
        loadPoints();
    }

    int getXpUntilNextTile() {
        return xpUntilNextTile;
    }

    @AllArgsConstructor
    enum MovementFlag
    {
        BLOCK_MOVEMENT_NORTH_WEST(CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST),
        BLOCK_MOVEMENT_NORTH(CollisionDataFlag.BLOCK_MOVEMENT_NORTH),
        BLOCK_MOVEMENT_NORTH_EAST(CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST),
        BLOCK_MOVEMENT_EAST(CollisionDataFlag.BLOCK_MOVEMENT_EAST),
        BLOCK_MOVEMENT_SOUTH_EAST(CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST),
        BLOCK_MOVEMENT_SOUTH(CollisionDataFlag.BLOCK_MOVEMENT_SOUTH),
        BLOCK_MOVEMENT_SOUTH_WEST(CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST),
        BLOCK_MOVEMENT_WEST(CollisionDataFlag.BLOCK_MOVEMENT_WEST),

        BLOCK_MOVEMENT_OBJECT(CollisionDataFlag.BLOCK_MOVEMENT_OBJECT),
        BLOCK_MOVEMENT_FLOOR_DECORATION(CollisionDataFlag.BLOCK_MOVEMENT_FLOOR_DECORATION),
        BLOCK_MOVEMENT_FLOOR(CollisionDataFlag.BLOCK_MOVEMENT_FLOOR),
        BLOCK_MOVEMENT_FULL(CollisionDataFlag.BLOCK_MOVEMENT_FULL);

        @Getter
        private int flag;

        /**
         * @param collisionData The tile collision flags.
         * @return The set of {@link MovementFlag}s that have been set.
         */
        public static Set<MovementFlag> getSetFlags(int collisionData)
        {
            return Arrays.stream(values())
                    .filter(movementFlag -> (movementFlag.flag & collisionData) != 0)
                    .collect(Collectors.toSet());
        }
    }

}
