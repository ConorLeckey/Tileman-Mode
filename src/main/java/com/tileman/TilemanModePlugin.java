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
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

import static com.tileman.MovementFlagUtil.NO_FLAGS;
import static com.tileman.MovementFlagUtil.containsAnyOf;

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
    private static final Type TILE_SET_TYPE = new TypeToken<Set<TilemanModeTile>>() {}.getType();

    private static final Gson GSON = new Gson();


    public static final int MOVE_NONE = 0;

    public static final int MOVE_CARDINAL_HALF_TILE = 64;
    public static final int MOVE_CARDINAL_ONE_TILE = 128;
    public static final int MOVE_CARDINAL_TWO_TILES = MOVE_CARDINAL_ONE_TILE * 2;

    public static final int MOVE_DIAGONAL_ONE_TILE = 181;
    public static final int MOVE_DIAGONAL_TWO_TILES = MOVE_DIAGONAL_ONE_TILE * 2;

    public static final int MOVE_L_SHAPE = 286;

    public static final int HOUSE_OBJECT_ID = 4525;

    @Getter(AccessLevel.PACKAGE)
    private final Set<WorldPoint> points = new HashSet<>();

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
    private TilemanModeWorldMapOverlay worldMapOverlay;

    @Inject
    private TileInfoOverlay infoOverlay;

    @Inject
    private ClientToolbar clientToolbar;

    @Provides
    TilemanModeConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TilemanModeConfig.class);
    }

    private static final MovementFlag[] FULL_BLOCK = new MovementFlag[]
            {MovementFlag.BLOCK_MOVEMENT_FLOOR,
                    MovementFlag.BLOCK_MOVEMENT_FLOOR_DECORATION,
                    MovementFlag.BLOCK_MOVEMENT_OBJECT,
                    MovementFlag.BLOCK_MOVEMENT_FULL};
    private static final int FULL_BLOCK_COMPRESSED = MovementFlag.compress(FULL_BLOCK);

    private static  final MovementFlag[] ALL_DIRECTIONS = new MovementFlag[]{
                    MovementFlag.BLOCK_MOVEMENT_NORTH_WEST,
                    MovementFlag.BLOCK_MOVEMENT_NORTH,
                    MovementFlag.BLOCK_MOVEMENT_NORTH_EAST,
                    MovementFlag.BLOCK_MOVEMENT_EAST,
                    MovementFlag.BLOCK_MOVEMENT_SOUTH_EAST,
                    MovementFlag.BLOCK_MOVEMENT_SOUTH,
                    MovementFlag.BLOCK_MOVEMENT_SOUTH_WEST,
                    MovementFlag.BLOCK_MOVEMENT_WEST
            };
    private static final int ALL_DIRECTIONS_COMPRESSED = MovementFlag.compress(ALL_DIRECTIONS);

    private final HashSet<Integer> tutorialIslandRegionIds = new HashSet<Integer>();

    private int totalTilesUsed, remainingTiles, xpUntilNextTile;
    private LocalPoint lastTile;
    private int lastPlane;
    private boolean lastAutoTilesConfig = false;
    private boolean inHouse = false;
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

            final WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, selectedSceneTile.getLocalLocation());
            final int regionId = worldPoint.getRegionID();
            final TilemanModeTile point = new TilemanModeTile(regionId, worldPoint.getRegionX(), worldPoint.getRegionY(), client.getPlane());

            client.createMenuEntry(-1)
                    .setOption(getTiles(regionId).contains(point) ? UNMARK : MARK)
                    .setTarget(event.getTarget())
                    .setType(MenuAction.RUNELITE);

        }
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        autoMark();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        if (gameStateChanged.getGameState() != GameState.LOGGED_IN) {
            lastTile = null;
            return;
        }
        loadPoints();
        updateTileCounter();
        inHouse = false;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        // Check if automark tiles is on, and if so attempt to step on current tile
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            return;
        }
        final WorldPoint playerPos = localPlayer.getWorldLocation();
        final LocalPoint playerPosLocal = LocalPoint.fromWorld(client, playerPos);
        if (playerPosLocal != null && config.automarkTiles() && !lastAutoTilesConfig) {
            handleWalkedToTile(playerPosLocal);
        }
        lastAutoTilesConfig = config.automarkTiles();
        updateTileCounter();
    }


    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        GameObject gameObject = event.getGameObject();

        if (gameObject.getId() == HOUSE_OBJECT_ID) {
            inHouse = true;
        }
    }

    @Override
    protected void startUp() {
        tutorialIslandRegionIds.add(12079);
        tutorialIslandRegionIds.add(12080);
        tutorialIslandRegionIds.add(12335);
        tutorialIslandRegionIds.add(12336);
        tutorialIslandRegionIds.add(12592);
        overlayManager.add(overlay);
        overlayManager.add(minimapOverlay);
        overlayManager.add(worldMapOverlay);
        overlayManager.add(infoOverlay);
        loadPoints();
        updateTileCounter();
        log.debug("startup");
        TilemanImportPanel panel = new TilemanImportPanel(this);
        NavigationButton navButton = NavigationButton.builder()
                .tooltip("Tileman Import")
                .icon(ImageUtil.loadImageResource(getClass(), "/icon.png"))
                .priority(70)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown() {
        tutorialIslandRegionIds.clear();
        overlayManager.remove(overlay);
        overlayManager.remove(minimapOverlay);
        overlayManager.remove(worldMapOverlay);
        overlayManager.remove(infoOverlay);
        points.clear();
    }

    private void autoMark() {
        final WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
        if (playerPos == null) {
            return;
        }

        final LocalPoint playerPosLocal = LocalPoint.fromWorld(client, playerPos);
        if (playerPosLocal == null) {
            return;
        }

        long currentTotalXp = client.getOverallExperience();
        long startAutoMark = System.currentTimeMillis();

        // If we have no last tile, we probably just spawned in, so make sure we walk on our current tile
        if ((lastTile == null
                || (lastTile.distanceTo(playerPosLocal) != 0 && lastPlane == playerPos.getPlane())
                || lastPlane != playerPos.getPlane()) && !regionIsOnTutorialIsland(playerPos.getRegionID())) {
            // Player moved
            handleWalkedToTile(playerPosLocal);
            lastTile = playerPosLocal;
            lastPlane = client.getPlane();
            updateTileCounter();
            log.debug("player moved");
            log.debug("last tile={}  distance={}", lastTile, lastTile == null ? "null" : lastTile.distanceTo(playerPosLocal));

        } else if (totalXp != currentTotalXp) {
            updateTileCounter();
            totalXp = currentTotalXp;
        }

        long stopAutoMark = System.currentTimeMillis();
        log.info("autoMark took {}ms", stopAutoMark - startAutoMark);
    }

    public void importGroundMarkerTiles() {
        // Get and store all the Ground Markers Regions
        List<String> groundMarkerRegions = getAllRegionIds("groundMarker");
        // If none, Exit function

        // Get and store array list of existing Tileman World Regions (like updateTileCounter does)
        List<String> tilemanModeRegions = getAllRegionIds(CONFIG_GROUP);

        // CONVERSION
        // Loop through Ground Marker Regions
        for (String region : groundMarkerRegions) {
            // Get Ground Markers Region's Tiles
            Set<TilemanModeTile> groundMarkerTiles = getConfiguration("groundMarker", REGION_PREFIX + region);
            // If region already exists in Tileman World Regions Array:
            if (tilemanModeRegions.contains(region)) {
                // Create Empty ArrayList for Region;
                // Get Tileman Region's tiles and add them to the region array list
                Set<TilemanModeTile> regionTiles = getTiles(region);

                // Create int for regionOriginalSize;
                // Set regionOriginalSize to arraylists length
                int regionOriginalSize = regionTiles.size();

                // Loop through Ground Markers Points
                for (TilemanModeTile groundMarkerTile : groundMarkerTiles) {
                    // If Ground Marker point already exists in Tileman World Region: Break loop iteration
                    if (regionTiles.contains(groundMarkerTile)) {
                        continue;
                    }
                    // Add point to array list
                    regionTiles.add(groundMarkerTile);
                }
                // If regionOriginalSize != current size
                if (regionOriginalSize != regionTiles.size()) {
                    // Save points for arrayList
                    savePoints(Integer.parseInt(region), regionTiles);
                }
            } else {
                // Save points for that region
                savePoints(Integer.parseInt(region), groundMarkerTiles);
            }
        }
        loadPoints();
    }

    List<String> getAllRegionIds(String configGroup) {
        return removeRegionPrefixes(configManager.getConfigurationKeys(configGroup + ".region"));
    }

    private List<String> removeRegionPrefixes(List<String> regions) {
        List<String> trimmedRegions = new ArrayList<>();
        for (String region : regions) {
            trimmedRegions.add(removeRegionPrefix(region));
        }
        return trimmedRegions;
    }

    private String removeRegionPrefix(String region) {
        return region.substring(region.indexOf('_') + 1);
    }

    Set<TilemanModeTile> getTiles(int regionId) {
        return getTiles(regionId+"");
    }

    private Set<TilemanModeTile> getTiles(String regionId) {
        return getConfiguration(CONFIG_GROUP, REGION_PREFIX + regionId);
    }

    private void updateTileCounter() {
        List<String> regions = configManager.getConfigurationKeys(CONFIG_GROUP + ".region");
        int totalTiles = 0;
        for (String region : regions) {
            Set<TilemanModeTile> regionTiles = getTiles(removeRegionPrefix(region));
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
        if (!config.excludeExp()) {
            earnedTiles += (int) client.getOverallExperience() / config.expPerTile();
        }

        // If including total level, add those tiles in
        if (config.includeTotalLevel()) {
            earnedTiles += client.getTotalLevel();
        }

        remainingTiles = earnedTiles - placedTiles;
    }

    private void updateXpUntilNextTile() {
        xpUntilNextTile = config.expPerTile() - Integer.parseInt(Long.toString(client.getOverallExperience() % config.expPerTile()));
    }

    private Set<TilemanModeTile> getConfiguration(String configGroup, String key) {

        long start = System.currentTimeMillis();
        String json = configManager.getConfiguration(configGroup, key);

        if (Strings.isNullOrEmpty(json)) {
            return Collections.emptySet();
        }

        Set<TilemanModeTile> tilemanModeTiles = GSON.fromJson(json, TILE_SET_TYPE);
        log.info("Took {}ms to get config", System.currentTimeMillis()-start);
        return tilemanModeTiles;
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
        long start = System.currentTimeMillis();
        if (points == null || points.isEmpty()) {
            configManager.unsetConfiguration(CONFIG_GROUP, REGION_PREFIX + regionId);
            return;
        }

        String json = GSON.toJson(points);
        configManager.setConfiguration(CONFIG_GROUP, REGION_PREFIX + regionId, json);
        log.info("Took {}ms to save config", System.currentTimeMillis()-start);
    }

    private Set<WorldPoint> translateToWorldPoint(Set<TilemanModeTile> points) {
        if (points.isEmpty()) {
            return Collections.emptySet();
        }

        return points.stream()
                .map(point -> WorldPoint.fromRegion(point.getRegionId(), point.getRegionX(), point.getRegionY(), point.getZ()))
                .flatMap(worldPoint ->
                {
                    final Collection<WorldPoint> localWorldPoints = WorldPoint.toLocalInstance(client, worldPoint);
                    return localWorldPoints.stream();
                })
                .collect(Collectors.toSet());
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
                inHouse ||
                !config.automarkTiles()) {
            return;
        }

        // Mark the tile they walked to
        updateTileMark(currentPlayerPoint, true);

        // If player moves 2 tiles in a straight line, fill in the middle tile
        // TODO Fill path between last point and current point. This will fix missing tiles that occur when you lag
        // TODO   and rendered frames are skipped. See if RL has an api that mimic's OSRS's pathing. If so, use that to
        // TODO   set all tiles between current tile and lastTile as marked
        if(lastTile != null){
            int xDiff = currentPlayerPoint.getX() - lastTile.getX();
            int yDiff = currentPlayerPoint.getY() - lastTile.getY();
            int yModifier = yDiff / 2;
            int xModifier = xDiff / 2;

            int distance = lastTile.distanceTo(currentPlayerPoint);
//            log.info("Walked {} distance", distance);
            switch(distance) {
                case MOVE_NONE: // Haven't moved
                case MOVE_CARDINAL_ONE_TILE: // Moved 1 tile
                    return;
                case MOVE_DIAGONAL_ONE_TILE: // Moved 1 tile diagonally
                    handleCornerMovement(xDiff, yDiff);
                    break;
                case MOVE_CARDINAL_TWO_TILES: // Moved 2 tiles straight
                case MOVE_DIAGONAL_TWO_TILES: // Moved 2 tiles diagonally
                    fillTile(new LocalPoint(lastTile.getX() + xModifier, lastTile.getY() + yModifier));
                    break;
                case MOVE_L_SHAPE: // Moved in an 'L' shape
                    handleLMovement(xDiff, yDiff);
                    break;
            }
        }
    }

    private void handleLMovement(int xDiff, int yDiff) {
        int xModifier = xDiff / 2;
        int yModifier = yDiff / 2;
        int tileBesideXDiff, tileBesideYDiff;

        // Whichever direction has moved only one, keep it 0. This is the translation to the potential 'problem' gameObject
        if (Math.abs(yDiff) == MOVE_CARDINAL_ONE_TILE) {
            tileBesideXDiff = xDiff;
            tileBesideYDiff = 0;
        } else {
            tileBesideXDiff = 0;
            tileBesideYDiff = yDiff;
        }

        int tileBesideFlagsArray = getTileMovementFlags(lastTile.getX() + tileBesideXDiff, lastTile.getY() + tileBesideYDiff);

        if (tileBesideFlagsArray == NO_FLAGS) {
            fillTile(new LocalPoint(lastTile.getX() + tileBesideXDiff / 2, lastTile.getY() + tileBesideYDiff / 2));
        } else if (containsAnyOf(FULL_BLOCK_COMPRESSED, tileBesideFlagsArray)) {
            if (yModifier == MOVE_CARDINAL_HALF_TILE) {
                yModifier = MOVE_CARDINAL_ONE_TILE;
            } else if (xModifier == MOVE_CARDINAL_HALF_TILE) {
                xModifier = MOVE_CARDINAL_ONE_TILE;
            }
            fillTile(new LocalPoint(lastTile.getX() + xModifier, lastTile.getY() + yModifier));
        } else if (containsAnyOf(ALL_DIRECTIONS_COMPRESSED, tileBesideFlagsArray)){
            MovementFlag direction1, direction2;
            if (yDiff == MOVE_CARDINAL_TWO_TILES || yDiff == -MOVE_CARDINAL_ONE_TILE) {
                // Moving 2 North or 1 South
                direction1 = MovementFlag.BLOCK_MOVEMENT_SOUTH;
            } else {
                // Moving 2 South or 1 North
                direction1 = MovementFlag.BLOCK_MOVEMENT_NORTH;
            }
            if (xDiff == MOVE_CARDINAL_TWO_TILES || xDiff == -MOVE_CARDINAL_ONE_TILE) {
                // Moving 2 East or 1 West
                direction2 = MovementFlag.BLOCK_MOVEMENT_WEST;
            } else {
                // Moving 2 West or 1 East
                direction2 = MovementFlag.BLOCK_MOVEMENT_EAST;
            }

            if (containsAnyOf(tileBesideFlagsArray, MovementFlag.compress(direction1, direction2))) {
                // Interrupted
                if (yModifier == MOVE_CARDINAL_HALF_TILE) {
                    yModifier = MOVE_CARDINAL_ONE_TILE;
                } else if (xModifier == MOVE_CARDINAL_HALF_TILE) {
                    xModifier = MOVE_CARDINAL_ONE_TILE;
                }
                fillTile(new LocalPoint(lastTile.getX() + xModifier, lastTile.getY() + yModifier));
            } else {
                // Normal Pathing
                fillTile(new LocalPoint(lastTile.getX() + tileBesideXDiff / 2, lastTile.getY() + tileBesideYDiff / 2));
            }
        }
    }


    private static final int MOVEMENT_SOUTH_AND_WEST = MovementFlag.compress(MovementFlag.BLOCK_MOVEMENT_SOUTH, MovementFlag.BLOCK_MOVEMENT_WEST);
    private static final int MOVEMENT_NORTH_AND_EAST = MovementFlag.compress(MovementFlag.BLOCK_MOVEMENT_NORTH, MovementFlag.BLOCK_MOVEMENT_EAST);
    private static final int MOVEMENT_SOUTH_AND_EAST = MovementFlag.compress(MovementFlag.BLOCK_MOVEMENT_SOUTH, MovementFlag.BLOCK_MOVEMENT_EAST);
    private static final int MOVEMENT_NORTH_AND_WEST = MovementFlag.compress(MovementFlag.BLOCK_MOVEMENT_NORTH, MovementFlag.BLOCK_MOVEMENT_WEST);


    private void handleCornerMovement(int xDiff, int yDiff) {
        LocalPoint northPoint;
        LocalPoint southPoint;
        if(yDiff > 0) {
            northPoint = new LocalPoint(lastTile.getX(), lastTile.getY() + yDiff);
            southPoint = new LocalPoint(lastTile.getX() + xDiff, lastTile.getY());
        } else {
            northPoint = new LocalPoint(lastTile.getX() + xDiff, lastTile.getY());
            southPoint = new LocalPoint(lastTile.getX(), lastTile.getY() + yDiff);
        }

        int northTile = getTileMovementFlags(northPoint);
        int southTile = getTileMovementFlags(southPoint);

        if (xDiff + yDiff == 0) {
            // Diagonal tilts north west
            if(containsAnyOf(FULL_BLOCK_COMPRESSED, northTile)
                    || containsAnyOf(northTile, MOVEMENT_SOUTH_AND_WEST)){
                fillTile(southPoint);
            } else if (containsAnyOf(FULL_BLOCK_COMPRESSED, southTile)
                    || containsAnyOf(southTile, MOVEMENT_NORTH_AND_EAST)){
                fillTile(northPoint);
            }
        } else {
            // Diagonal tilts north east
            if(containsAnyOf(FULL_BLOCK_COMPRESSED, northTile)
                    || containsAnyOf(northTile, MOVEMENT_SOUTH_AND_EAST)){
                fillTile(southPoint);
            } else {
                if (containsAnyOf(FULL_BLOCK_COMPRESSED, southTile)
                        || containsAnyOf(southTile, MOVEMENT_NORTH_AND_WEST)){
                    fillTile(northPoint);
                }
            }
        }
    }
    private int getTileMovementFlags(int x, int y) {
        LocalPoint pointBeside = new LocalPoint(x, y);

        CollisionData[] collisionData = client.getCollisionMaps();
        assert collisionData != null;
        int[][] collisionDataFlags = collisionData[client.getPlane()].getFlags();

        Set<MovementFlag> tilesBesideFlagsSet = MovementFlag.getSetFlags(collisionDataFlags[pointBeside.getSceneX()][pointBeside.getSceneY()]);
        return MovementFlag.compress(tilesBesideFlagsSet.toArray(new MovementFlag[0]));
    }

    private int getTileMovementFlags(LocalPoint localPoint) {
        return  getTileMovementFlags(localPoint.getX(), localPoint.getY());
    }

    private boolean regionIsOnTutorialIsland(int regionId) {
        return tutorialIslandRegionIds.contains(regionId);
    }

    private void fillTile(LocalPoint localPoint){
        if(lastPlane != client.getPlane()) {
            return;
        }
        updateTileMark(localPoint, true);
    }

    private void updateTileMark(LocalPoint localPoint, boolean markedValue) {
        if(containsAnyOf(getTileMovementFlags(localPoint), FULL_BLOCK_COMPRESSED)) {
            return;
        }

        WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, localPoint);

        int regionId = worldPoint.getRegionID();
        TilemanModeTile point = new TilemanModeTile(regionId, worldPoint.getRegionX(), worldPoint.getRegionY(), client.getPlane());
        log.debug("Updating point: {} - {}", point, worldPoint);

        Set<TilemanModeTile> tilemanModeTiles = getTiles(regionId);

        if (markedValue) {
            // Try add tile
            if (!tilemanModeTiles.contains(point) && (config.allowTileDeficit() || remainingTiles > 0)) {
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

}
