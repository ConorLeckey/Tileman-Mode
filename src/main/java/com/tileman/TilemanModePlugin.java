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
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.game.chatbox.ChatboxPanelManager;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
        name = "Tileman Mode",
        description = "Automatically draws tiles where you walk",
        tags = {"overlay", "tiles"}
)
public class TilemanModePlugin extends Plugin {

    // Constants with values that should not be modified. Configs "in the wild" use these keys.
    public static final String CONFIG_GROUP = "tilemanMode";
    public static final String LEGACY_GROUP_TILEMAN_CONFIG_GROUP = "groupTilemanAddon";
    public static final String REGION_PREFIX_IMPORTED = "imported_";
    public static final String REGION_PREFIX_V2 = "regionv2_";
    public static final String REGION_PREFIX_V1 = "region_";

    // Constants for menu option strings that the plugin utilises
    private static final String MARK = "Unlock Tileman tile";
    private static final String UNMARK = "Clear Tileman tile";
    private static final String WALK_HERE = "Walk here";

    private GroupTilemanDataManager groupTilemanDataManager;

    @Getter(AccessLevel.PACKAGE)
    private final List<WorldPoint> tilesToRender = new ArrayList<>();

    @Getter(AccessLevel.PACKAGE)
    private final Set<WorldPoint> groupTilesToRender = new HashSet<>();

    @Inject
    private Client client;

    @Inject
    private Gson gson;

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

    @Inject
    private ChatMessageManager chatMessageManager;

    @Inject
    private ChatboxPanelManager chatboxPanelManager;

    @Provides
    TilemanModeConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TilemanModeConfig.class);
    }

    private final MovementFlag[] fullBlock = new MovementFlag[]
            {MovementFlag.BLOCK_MOVEMENT_FLOOR,
                    MovementFlag.BLOCK_MOVEMENT_FLOOR_DECORATION,
                    MovementFlag.BLOCK_MOVEMENT_OBJECT,
                    MovementFlag.BLOCK_MOVEMENT_FULL};

    private final MovementFlag[] allDirections = new MovementFlag[]
            {
                    MovementFlag.BLOCK_MOVEMENT_NORTH_WEST,
                    MovementFlag.BLOCK_MOVEMENT_NORTH,
                    MovementFlag.BLOCK_MOVEMENT_NORTH_EAST,
                    MovementFlag.BLOCK_MOVEMENT_EAST,
                    MovementFlag.BLOCK_MOVEMENT_SOUTH_EAST,
                    MovementFlag.BLOCK_MOVEMENT_SOUTH,
                    MovementFlag.BLOCK_MOVEMENT_SOUTH_WEST,
                    MovementFlag.BLOCK_MOVEMENT_WEST
            };

    private final HashSet<Integer> tutorialIslandRegionIds = new HashSet<Integer>();

    private int totalTilesUsed, remainingTiles, xpUntilNextTile;
    private LocalPoint lastTile;
    public int lastPlane;
    private boolean lastAutoTilesConfig = false;
    private boolean inHouse = false;
    private long totalXp;
    private boolean dataMigrationInProgress = false;

    public Duration durationLastStart;
    public Duration durationLastWayfind;
    public Duration durationLastRegionRead;
    public Duration durationLastRegionWrite;
    public Duration durationLastMove;
    public Duration durationLastTilesUpdate;
    public Duration durationLastRender;

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

            if (worldPoint.getPlane() != client.getPlane()) {
                return;
            }

            client.createMenuEntry(-1)
                .setOption(tilesToRender.contains(worldPoint) ? UNMARK : MARK)
                .setTarget(event.getTarget())
                .setType(MenuAction.RUNELITE);
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick) {

        // update tile claims based on movement
        autoMark();

    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {

        // Guard against doing anything until the player is actually logged in
        if (gameStateChanged.getGameState() != GameState.LOGGED_IN) {
            lastTile = null;
            return;
        }

        updateTileCountFromConfigs();
        updateTilesToRender();
        inHouse = false;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        // exit early if change made is during an automatic data migration
        if (dataMigrationInProgress){
            return;
        }

        if (client.getGameState() != GameState.LOGGED_IN){
            return;
        }

        // Check if automark tiles is on, and if so attempt to step on current tile
        final WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
        final LocalPoint playerPosLocal = LocalPoint.fromWorld(client, playerPos);
        if (playerPosLocal != null && config.automarkTiles() && !lastAutoTilesConfig) {
            handleWalkedToTile(playerPosLocal);
        }
        lastAutoTilesConfig = config.automarkTiles();
        updateTileCountFromConfigs();
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        GameObject gameObject = event.getGameObject();

        if (gameObject.getId() == 4525) {
            inHouse = true;
        }
    }

    @Override
    protected void startUp() {

        log.debug("TileManMode Startup - Start");

        performConfigVersionMigrations();

        tutorialIslandRegionIds.add(12079);
        tutorialIslandRegionIds.add(12080);
        tutorialIslandRegionIds.add(12335);
        tutorialIslandRegionIds.add(12336);
        tutorialIslandRegionIds.add(12592);
        overlayManager.add(overlay);
        overlayManager.add(minimapOverlay);
        overlayManager.add(worldMapOverlay);
        overlayManager.add(infoOverlay);

        // update so we render if the plugin has just been freshly enabled.
        updateTileCountFromConfigs();
        updateTilesToRender();

        groupTilemanDataManager = new GroupTilemanDataManager(this, configManager, gson);
        NavigationButton navButton = NavigationButton.builder()
                .tooltip("Group Tileman Data")
                .icon(ImageUtil.getResourceStreamFromClass(getClass(), "/icon.png"))
                .priority(70)
                .panel(groupTilemanDataManager)
                .build();

        clientToolbar.addNavigation(navButton);

        // check if legacy group tileman data is found
        groupTilemanDataManager.generateLegacyGroupTilemanPluginWarning();
    }

    @Override
    protected void shutDown() {
        tutorialIslandRegionIds.clear();
        overlayManager.remove(overlay);
        overlayManager.remove(minimapOverlay);
        overlayManager.remove(worldMapOverlay);
        overlayManager.remove(infoOverlay);
        tilesToRender.clear();
        groupTilesToRender.clear();
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

        // If we have no last tile, we probably just spawned in, so make sure we walk on our current tile
        if ((lastTile == null
                || (lastTile.distanceTo(playerPosLocal) != 0 && lastPlane == playerPos.getPlane())
                || lastPlane != playerPos.getPlane()) && !regionIsOnTutorialIsland(playerPos.getRegionID())) {
            // Player moved
            handleWalkedToTile(playerPosLocal);
            lastTile = playerPosLocal;
            log.debug("moved to tile={} distance={}", lastTile, lastTile == null ? "null" : lastTile.distanceTo(playerPosLocal));
        }

        // Refresh metrics
        long currentTotalXp = client.getOverallExperience();
        if (totalXp != currentTotalXp) {
            totalXp = currentTotalXp;
        }
        updateXpUntilNextTile();
        updateRemainingTiles();
    }

    Set<Integer> getAllRegionIds(String configGroup, String regionPrefix) {

        List<String> allKeys = configManager.getConfigurationKeys(configGroup + "." + regionPrefix);
        Set<Integer> regionIds = new HashSet<>();

        for (String key : allKeys) {
            key = key.replace(configGroup + "." + regionPrefix, "");
            key = key.replace("_0", "");
            key = key.replace("_1", "");
            key = key.replace("_2", "");
            key = key.replace("_3", "");
            regionIds.add(Integer.parseInt(key));
        }

        return regionIds;
    }

    private void updateTileCountFromConfigs() {
        log.debug("Updating tile counter");

        int totalTiles = 0;
        Set<Integer> regions = getAllRegionIds(CONFIG_GROUP, REGION_PREFIX_V2);
        for (int regionId : regions) {
            for (int plane = 0; plane < 4; plane++) {
                Collection<TilemanModeTile> regionTiles = readTiles(regionId, plane);
                totalTiles += regionTiles.size();
            }
        }

        totalTilesUsed = totalTiles;
        updateRemainingTiles();
    }

    private void updateRemainingTiles() {
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

        remainingTiles = earnedTiles - totalTilesUsed;
    }

    private void updateXpUntilNextTile() {
        xpUntilNextTile = config.expPerTile() - Integer.parseInt(Long.toString(client.getOverallExperience() % config.expPerTile()));
    }

    private void performConfigVersionMigrations() {
        Instant startTime = Instant.now();
        // Progressively move v1 -> v2, then v2 -> v3 etc so users configs are always on the latest version.
        // This ensures runtime code is always using the most efficient / current implementation.
        // Use string literals here rather than constants in case somebody removes or changes the constants in future

        dataMigrationInProgress = true;

        // v1 to v2 data
        String prefix = "tilemanMode.region_";
        List<String> v1keys = configManager.getConfigurationKeys(prefix);
        for (String key : v1keys){
            Integer regionId = Integer.parseInt(key.replace(prefix, ""));
            String json = configManager.getConfiguration("tilemanMode", "region_" + regionId);
            List<TilemanModeTile> tiles = gson.fromJson(json, new TypeToken<List<TilemanModeTile>>(){}.getType());
            log.debug("TileManMode performConfigVersionMigrations - " + tiles.size() + " tiles to migrate");
            for (int plane = 0; plane < 4; plane++) {
                List<TilemanModeTile> filteredTiles = new ArrayList<TilemanModeTile>();
                for (TilemanModeTile tile : tiles) {
                    if (tile.getZ() == plane){
                        filteredTiles.add(tile);
                    }
                }
                key = "regionv2_" + regionId + "_" + plane;
                writeV2FormatData(filteredTiles, key);
            }
        }
        configManager.sendConfig(); // v1 -> v2 saved to disk

        // cleanup v1 keys now the v2 keys have been safely saved to disk
        for (String key : v1keys) {
            Integer regionId = Integer.parseInt(key.replace(prefix, ""));
            configManager.unsetConfiguration("tilemanMode", "region_" + regionId); // remove old v1 format
        }
        configManager.sendConfig(); // v1 removed from configs and saved to disk

        // any future migrations should be added here migrating from v2 data to v3 and so on.

        dataMigrationInProgress = false;
        durationLastStart = Duration.between(startTime, Instant.now());
    }

    private void writeTiles(int regionId, Collection<TilemanModeTile> tiles, int plane) {
        // Wrap data writes using this handler so if the format changes in future only one location needs updating
        Instant startTime = Instant.now();
        String key = REGION_PREFIX_V2 + regionId + "_" + plane;
        writeV2FormatData(tiles, key);
        durationLastRegionWrite = Duration.between(startTime, Instant.now());
    }

    public Collection<TilemanModeTile> readTiles(int regionId, int plane) {
        // Wrap most data reads using this handler so if the format changes in future only one location needs updating
        Instant startTime = Instant.now();
        Collection<TilemanModeTile> tiles = readV2FormatData(REGION_PREFIX_V2, regionId, plane);
        durationLastRegionRead = Duration.between(startTime, Instant.now());
        return tiles;
    }

    public Collection<TilemanModeTile> readImportedTileSet(String tileSetName, int regionId, int plane) {
        Collection<TilemanModeTile> tiles = readV2FormatData(REGION_PREFIX_IMPORTED + tileSetName + "_", regionId, plane);
        return tiles;
    }

    private Collection<TilemanModeTile> readV1FormatData(String configGroup, String key) {
        // retained to allow reading of legacy V1 format data

        String json = configManager.getConfiguration(configGroup, key);

        if (Strings.isNullOrEmpty(json)) {
            return Collections.emptyList();
        }

        return gson.fromJson(json, new TypeToken<List<TilemanModeTile>>() {
        }.getType());
    }

    private Collection<TilemanModeTile> readV2FormatData(String prefix, int regionID, int plane) {

        // generate the list to build or return empty.
        List<TilemanModeTile> tilesStoredInV2Format = new ArrayList<>();

        // grab the raw encoded string in Base64 from the config file
        String key = prefix + regionID + "_" + plane;
        String encoded = configManager.getConfiguration(CONFIG_GROUP, key);

        if (encoded == null){
            return tilesStoredInV2Format;
        }

        // decode to a byte array, then interpret it as a Bitset
        byte[] bytes = Base64.getUrlDecoder().decode(encoded);

        // return if there's no data under that key
        if (bytes == null || bytes.length == 0){
            return tilesStoredInV2Format;
        }

        BitSet bitSet = BitSet.valueOf(bytes);

        // find bits set to 1, and create a tile when they're found
        for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
            int tileRegionY = i / 64;
            int tileRegionX = i - (64 * tileRegionY);
            tilesStoredInV2Format.add(new TilemanModeTile(regionID, tileRegionX, tileRegionY, plane));
        }

        return tilesStoredInV2Format;
    }

    public void updateTilesToRender() {
        Instant startTime = Instant.now();

        // clear any existing rendering arrays
        tilesToRender.clear();
        groupTilesToRender.clear();

        // we only want to update tiles to render if they are around the player
        int[] regions = client.getMapRegions();
        if (regions == null) {
            return;
        }

        int plane = client.getTopLevelWorldView().getPlane();
        for (int regionId : regions) {

            // update player centric tile claims
            Collection<WorldPoint> worldPoint = translateToWorldPoint(readTiles(regionId, plane));
            tilesToRender.addAll(worldPoint);

            // update group tileman claims
            for (String tileSetName : groupTilemanDataManager.getImportedDataSetKeys()) {
                Collection<WorldPoint> groupTiles = translateToWorldPoint(readImportedTileSet(tileSetName, regionId, plane));
                groupTilesToRender.addAll(groupTiles);
            }
        }

        durationLastTilesUpdate = Duration.between(startTime, Instant.now());
    }

    public void writeV2FormatData(Collection<TilemanModeTile> tiles, String key) {

        // don't write empty regions. remove them instead.
        if (tiles == null || tiles.isEmpty()) {
            configManager.unsetConfiguration(CONFIG_GROUP, key);
            return;
        }

        // 4096 = 64x64 because that's Runelite's region dimensions
        BitSet out = new BitSet(4096);
        for (TilemanModeTile tile : tiles) {
            out.set(tile.getRegionY() * 64 + tile.getRegionX());
        }

        // write out the plane data directly to base64 encoded string.
        configManager.setConfiguration(CONFIG_GROUP, key, out.toByteArray());
    }

    private Collection<WorldPoint> translateToWorldPoint(Collection<TilemanModeTile> points) {
        if (points.isEmpty()) {
            return Collections.emptyList();
        }

        return points.stream()
                .map(point -> WorldPoint.fromRegion(point.getRegionId(), point.getRegionX(), point.getRegionY(), point.getZ()))
                .flatMap(worldPoint ->
                {
                    final Collection<WorldPoint> localWorldPoints = WorldPoint.toLocalInstance(client, worldPoint);
                    return localWorldPoints.stream();
                })
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
        updateTileMark(selectedPoint, markedValue, true);
    }

    private void handleWalkedToTile(LocalPoint currentPlayerPoint) {
        if (currentPlayerPoint == null ||
                inHouse ||
                !config.automarkTiles()) {
            return;
        }

        // Mark the tile they walked to
        updateTileMark(currentPlayerPoint, true, false);

        // If player moves 2 tiles in a straight line, fill in the middle tile
        // TODO Fill path between last point and current point. This will fix missing tiles that occur when you lag
        // TODO   and rendered frames are skipped. See if RL has an api that mimic's OSRS's pathing. If so, use that to
        // TODO   set all tiles between current tile and lastTile as marked
        if(lastTile != null){
            int xDiff = currentPlayerPoint.getX() - lastTile.getX();
            int yDiff = currentPlayerPoint.getY() - lastTile.getY();
            int yModifier = yDiff / 2;
            int xModifier = xDiff / 2;

            switch(lastTile.distanceTo(currentPlayerPoint)) {
                case 0: // Haven't moved
                case 128: // Moved 1 tile
                    return;
                case 181: // Moved 1 tile diagonally
                    handleCornerMovement(xDiff, yDiff);
                    break;
                case 256: // Moved 2 tiles straight
                case 362: // Moved 2 tiles diagonally
                    fillTile(new LocalPoint(lastTile.getX() + xModifier, lastTile.getY() + yModifier));
                    break;
                case 286: // Moved in an 'L' shape
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
        if (Math.abs(yDiff) == 128) {
            tileBesideXDiff = xDiff;
            tileBesideYDiff = 0;
        } else {
            tileBesideXDiff = 0;
            tileBesideYDiff = yDiff;
        }

        MovementFlag[] tileBesideFlagsArray = getTileMovementFlags(lastTile.getX() + tileBesideXDiff, lastTile.getY() + tileBesideYDiff);

        if (tileBesideFlagsArray.length == 0) {
            fillTile(new LocalPoint(lastTile.getX() + tileBesideXDiff / 2, lastTile.getY() + tileBesideYDiff / 2));
        } else if (containsAnyOf(fullBlock, tileBesideFlagsArray)) {
            if (Math.abs(yModifier) == 64) {
                yModifier *= 2;
            } else if (Math.abs(xModifier) == 64) {
                xModifier *= 2;
            }
            fillTile(new LocalPoint(lastTile.getX() + xModifier, lastTile.getY() + yModifier));
        } else if (containsAnyOf(allDirections, tileBesideFlagsArray)){
            MovementFlag direction1, direction2;
            if (yDiff == 256 || yDiff == -128) {
                // Moving 2 North or 1 South
                direction1 = MovementFlag.BLOCK_MOVEMENT_SOUTH;
            } else {
                // Moving 2 South or 1 North
                direction1 = MovementFlag.BLOCK_MOVEMENT_NORTH;
            }
            if (xDiff == 256 || xDiff == -128) {
                // Moving 2 East or 1 West
                direction2 = MovementFlag.BLOCK_MOVEMENT_WEST;
            } else {
                // Moving 2 West or 1 East
                direction2 = MovementFlag.BLOCK_MOVEMENT_EAST;
            }

            if (containsAnyOf(tileBesideFlagsArray, new MovementFlag[]{direction1, direction2})) {
                // Interrupted
                if (yModifier == 64) {
                    yModifier = 128;
                } else if (xModifier == 64) {
                    xModifier = 128;
                }
                fillTile(new LocalPoint(lastTile.getX() + xModifier, lastTile.getY() + yModifier));
            } else {
                // Normal Pathing
                fillTile(new LocalPoint(lastTile.getX() + tileBesideXDiff / 2, lastTile.getY() + tileBesideYDiff / 2));
            }
        }
    }

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

        MovementFlag[] northTile = getTileMovementFlags(northPoint);
        MovementFlag[] southTile = getTileMovementFlags(southPoint);

        if (xDiff + yDiff == 0) {
            // Diagonal tilts north west
            if(containsAnyOf(fullBlock, northTile)
                    || containsAnyOf(northTile, new MovementFlag[]{MovementFlag.BLOCK_MOVEMENT_SOUTH, MovementFlag.BLOCK_MOVEMENT_WEST})){
                fillTile(southPoint);
            } else if (containsAnyOf(fullBlock, southTile)
                    || containsAnyOf(southTile, new MovementFlag[]{MovementFlag.BLOCK_MOVEMENT_NORTH, MovementFlag.BLOCK_MOVEMENT_EAST})){
                fillTile(northPoint);
            }
        } else {
            // Diagonal tilts north east
            if(containsAnyOf(fullBlock, northTile)
                    || containsAnyOf(northTile, new MovementFlag[]{MovementFlag.BLOCK_MOVEMENT_SOUTH, MovementFlag.BLOCK_MOVEMENT_EAST})){
                fillTile(southPoint);
            } else if (containsAnyOf(fullBlock, southTile)
                    || containsAnyOf(southTile, new MovementFlag[]{MovementFlag.BLOCK_MOVEMENT_NORTH, MovementFlag.BLOCK_MOVEMENT_WEST})){
                fillTile(northPoint);
            }
        }
    }

    private MovementFlag[] getTileMovementFlags(int x, int y) {
        LocalPoint pointBeside = new LocalPoint(x, y);

        CollisionData[] collisionData = client.getCollisionMaps();
        assert collisionData != null;
        int[][] collisionDataFlags = collisionData[client.getPlane()].getFlags();

        Set<MovementFlag> tilesBesideFlagsSet = MovementFlag.getSetFlags(collisionDataFlags[pointBeside.getSceneX()][pointBeside.getSceneY()]);
        MovementFlag[] tileBesideFlagsArray = new MovementFlag[tilesBesideFlagsSet.size()];
        tilesBesideFlagsSet.toArray(tileBesideFlagsArray);

        return tileBesideFlagsArray;
    }

    private MovementFlag[] getTileMovementFlags(LocalPoint localPoint) {
        return  getTileMovementFlags(localPoint.getX(), localPoint.getY());
    }

    private boolean containsAnyOf(MovementFlag[] comparisonFlags, MovementFlag[] flagsToCompare) {
        if (comparisonFlags.length == 0 || flagsToCompare.length == 0) {
            return false;
        }
        for (MovementFlag flag : flagsToCompare) {
            if (Arrays.asList(comparisonFlags).contains(flag)) {
                return true;
            }
        }
        return false;
    }

    private boolean regionIsOnTutorialIsland(int regionId) {
        return tutorialIslandRegionIds.contains(regionId);
    }

    private void fillTile(LocalPoint localPoint){
        if(lastPlane != client.getPlane()) {
            return;
        }
        updateTileMark(localPoint, true, false);
    }

    private void updateTileMark(LocalPoint localPoint, boolean claimTile, boolean ignoreImportedTiles) {
        Instant startTime = Instant.now();

        // never modify a blocked tile
        if(containsAnyOf(getTileMovementFlags(localPoint), fullBlock)) {
            return;
        }

        int plane = client.getPlane();
        WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, localPoint);
        int regionId = worldPoint.getRegionID();
        TilemanModeTile tile = new TilemanModeTile(regionId, worldPoint.getRegionX(), worldPoint.getRegionY(), plane);
        log.debug("Updating point: {} - {}", tile, worldPoint);
        Collection<TilemanModeTile> tiles = readTiles(regionId, plane);
        Boolean tileIsUnlocked = tiles.contains(tile);
        Boolean stateChanged = false;
        Boolean groupTilemanClaimed = !ignoreImportedTiles && groupTilesToRender.contains(WorldPoint.fromLocalInstance(client, localPoint));

        // attempt to unlock
        if (claimTile && !tileIsUnlocked && !groupTilemanClaimed) {
            if ((config.allowTileDeficit() || remainingTiles > 0)) {
                tiles.add(tile);
                tilesToRender.add(worldPoint);
                totalTilesUsed += 1;
                stateChanged = true;
            }
        }

        // release lock
        if (!claimTile && tileIsUnlocked)
        {
            tiles.remove(tile);
            tilesToRender.remove(worldPoint);
            totalTilesUsed -= 1;
            stateChanged = true;
        }

        // do updates only if state changes to prevent updates when unchanged
        if (stateChanged)
        {
            writeTiles(regionId, tiles, plane);
        }

        durationLastMove = Duration.between(startTime, Instant.now());
    }

    public void handlePlaneChanged() {
        int plane = client.getPlane();
        if (lastPlane != plane){
            lastPlane = plane;
            updateTilesToRender();
        }
    }

    public String getPlayerName() {
        return client.getLocalPlayer().getName();
    }

    int getXpUntilNextTile() {
        return xpUntilNextTile;
    }

    public Client getClient() {
        return client;
    }

    public void sendChatMessage(String message)
    {
        // display the stylised message
        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.FRIENDSCHAT)
                .sender("Tileman Mode Plugin")
                .runeLiteFormattedMessage(message)
                .timestamp((int) (System.currentTimeMillis() / 1000))
                .build());
    }

    GroupTilemanDataManager getGroupTilemanDataManager() {
        return groupTilemanDataManager;
    }

    @AllArgsConstructor
    enum MovementFlag {
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
        public static Set<MovementFlag> getSetFlags(int collisionData) {
            return Arrays.stream(values())
                    .filter(movementFlag -> (movementFlag.flag & collisionData) != 0)
                    .collect(Collectors.toSet());
        }
    }

}