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

import net.runelite.client.config.*;

import java.awt.*;

@ConfigGroup("tilemanMode")
public interface TilemanModeConfig extends Config {
    @ConfigSection(
            name = "Game Mode",
            description = "Select your Tileman game mode'",
            position = 1
    )
    String gameModeSection = "gameMode";

    @ConfigSection(
            name = "Custom Game Mode",
            description = "Create a custom Tileman game mode. Be sure to 'Enable Custom Game Mode'",
            position = 2,
            closedByDefault = true
    )
    String customGameModeSection = "customGameMode";

    @ConfigSection(
            name = "Settings",
            description = "Settings'",
            position = 3
    )
    String settingsSection = "settings";

    @ConfigSection(
            name = "Claimed Tiles",
            description = "Tile rendering options for claimed tiles'",
            position = 4
    )
    String claimedTilesSection = "claimedTiles";

    @ConfigSection(
            name = "Group Tiles",
            description = "Tile rendering options for group tiles'",
            position = 5,
            closedByDefault = true
    )
    String groupTilesSection = "groupTiles";

    @ConfigSection(
            name = "Claimed on Complete Path",
            description = "Wayfinder tile rendering options for tiles on a complete path requiring no unlocks",
            position = 6,
            closedByDefault = true
    )
    String completePathSection = "completePath";

    @ConfigSection(
            name = "Claimed on Incomplete Path",
            description = "Wayfinder tile rendering options for claimed tiles on a path that requires unlocks",
            position = 7,
            closedByDefault = true
    )
    String claimedPathSection = "claimedPath";

    @ConfigSection(
            name = "Unclaimed on Incomplete Path",
            description = "Wayfinder tile rendering options for unclaimed tiles on a path that requires unlocks",
            position = 8,
            closedByDefault = true
    )
    String unclaimedPathSection = "unclaimedPath";

    @ConfigSection(
            name = "Metrics",
            description = "Additional overlay information",
            position = 9,
            closedByDefault = true
    )
    String metricsSection = "metrics";

    ///////////////////////////////////////////////////////////////////////////
    // gameMode section

    public enum TilemanGameMode {
        COMMUNITY,
        STRICT,
        ACCELERATED
    }

    @Alpha
    @ConfigItem(
            keyName = "gameMode",
            name = "Game Mode",
            section = gameModeSection,
            description = "Select your Tileman game mode",
            position = 1
    )
    default TilemanGameMode gameMode() {
        return TilemanGameMode.COMMUNITY;
    }

    ///////////////////////////////////////////////////////////////////////////
    // customGameMode section

    @ConfigItem(
            keyName = "enableCustomGameMode",
            name = "Enable Custom Game Mode",
            description = "Settings below will override Game Mode defaults",
            section = customGameModeSection,
            position = 1
    )
    default boolean enableCustomGameMode() {
        return false;
    }

    @Range(
            min = Integer.MIN_VALUE
    )
    @ConfigItem(
            keyName = "tilesOffset",
            name = "Bonus tiles",
            description = "Add more tiles to your limit, set to 0 for off",
            section = customGameModeSection,
            position = 2
    )
    default int tilesOffset() {
        return 9;
    }

    @ConfigItem(
            keyName = "includeTotalLevels",
            name = "Include total level",
            description = "Includes total level in usable tiles",
            section = customGameModeSection,
            position = 3
    )
    default boolean includeTotalLevel() {
        return false;
    }

    @ConfigItem(
            keyName = "excludeExp",
            name = "Exclude Experience",
            description = "Includes experience / 1000 in usable tiles",
            section = customGameModeSection,
            position = 4
    )
    default boolean excludeExp() {
        return false;
    }

    @Range(
            min = 500
    )
    @ConfigItem(
            keyName = "expPerTile",
            name = "Exp per Tile",
            description = "Determines how much exp you require per tile",
            section = customGameModeSection,
            position = 5
    )
    default int expPerTile() {
        return 1000;
    }

    ///////////////////////////////////////////////////////////////////////////
    // settings section

    @ConfigItem(
            keyName = "automarkTiles",
            name = "Auto-mark tiles",
            section = settingsSection,
            description = "Automatically mark tiles as you walk.",
            position = 2
    )
    default boolean automarkTiles() {
        return false;
    }

    @Range(
            min = Integer.MIN_VALUE
    )
    @ConfigItem(
            keyName = "warningLimit",
            name = "Unspent tiles warning",
            section = settingsSection,
            description = "Highlights overlay when limit reached",
            position = 3
    )
    default int warningLimit() {
        return 20;
    }

    @ConfigItem(
            keyName = "enableTilesWarning",
            name = "Enable Tiles Warning",
            section = settingsSection,
            description = "Turns on tile warnings when you reach your set limit or 0.",
            position = 4
    )
    default boolean enableTileWarnings() {
        return false;
    }

    @ConfigItem(
            keyName = "allowTileDeficit",
            name = "Allow Tile Deficit",
            section = settingsSection,
            description = "Allows you to place tiles after you have none left.",
            position = 5
    )
    default boolean allowTileDeficit() {
        return false;
    }

    @ConfigItem(
            keyName = "enableWayfinderSystem",
            name = "Enable Wayfinder",
            section = settingsSection,
            description = "Draws the path to the tile underneath the cursor",
            position = 6
    )
    default boolean enableWayfinder() {
        return true;
    }

    ///////////////////////////////////////////////////////////////////////////
    // claimedTiles section

    @ConfigItem(
            keyName = "drawOnMinimap",
            name = "Draw on minimap",
            section = claimedTilesSection,
            description = "Configures whether marked tiles should be drawn on minimap",
            position = 1
    )
    default boolean drawTilesOnMinimap() {
        return true;
    }

    @ConfigItem(
            keyName = "drawTilesOnWorldMap",
            name = "Draw on world map",
            section = claimedTilesSection,
            description = "Configures whether marked tiles should be drawn on world map",
            position = 2
    )
    default boolean drawTilesOnWorldMap() {
        return true;
    }

    @ConfigItem(
            keyName = "drawTilesUnderPaths",
            name = "Draw on wayfinder path",
            section = claimedTilesSection,
            description = "Enable to draw claimed tiles under the predictive wayfinder paths",
            position = 3
    )
    default boolean drawTilesUnderPaths() {
        return true;
    }

    @Alpha
    @ConfigItem(
            // keyName here should not be made consistent as this is a legacy schema field
            // we don't want upgrading version to stop respecting old settings
            keyName = "markerColor",
            name = "Border Color",
            section = claimedTilesSection,
            description = "Border color of unlocked tiles",
            position = 4
    )
    default Color claimedTileBorderColor() { return new Color(123,232,0,79); }

    @Alpha
    @ConfigItem(
            keyName = "claimedTileFillColor",
            name = "Fill Color",
            section = claimedTilesSection,
            description = "Fill color of unlocked tiles",
            position = 5
    )
    default Color claimedTileFillColor() { return new Color(0, 0, 0, 32); }

    @Alpha
    @ConfigItem(
            keyName = "claimedTileWarningColor",
            name = "Warning Color",
            section = claimedTilesSection,
            description = "Color claimed tile borders change to at the warning threshold",
            position = 6
    )
    default Color claimedTileWarningColor() { return new Color(255, 153, 0, 90); }

    @Alpha
    @ConfigItem(
            keyName = "claimedTileDeficitColor",
            name = "Deficit Color",
            section = claimedTilesSection,
            description = "Color claimed tile borders change to when no tile unlocks remain.",
            position = 7
    )
    default Color claimedTileDeficitColor() { return new Color(255, 0, 0, 128); }

    @ConfigItem(
            keyName = "insetClaimedTiles",
            name = "Inset",
            section = claimedTilesSection,
            description = "Reduces the rendered tile size by 20%",
            position = 8
    )
    default boolean insetClaimedTiles() {
        return false;
    }

    ///////////////////////////////////////////////////////////////////////////
    // groupTiles section

    @ConfigItem(
            keyName = "drawGroupTilesOnMinimap",
            name = "Draw on minimap",
            section = groupTilesSection,
            description = "Configures whether group tiles should be drawn on minimap",
            position = 1
    )
    default boolean drawGroupTilesOnMinimap() {
        return true;
    }

    @ConfigItem(
            keyName = "drawGroupTilesOnWorldMap",
            name = "Draw on world map",
            section = groupTilesSection,
            description = "Configures whether group tiles should be drawn on world map",
            position = 2
    )
    default boolean drawGroupTilesOnWorldMap() {
        return true;
    }

    @ConfigItem(
            keyName = "drawGroupTilesUnderPaths",
            name = "Draw on wayfinder path",
            section = groupTilesSection,
            description = "Draw group tiles under the predictive wayfinder paths",
            position = 3
    )
    default boolean drawGroupTilesUnderPaths() {
        return true;
    }

    @ConfigItem(
            keyName = "drawGroupTilesUnderClaimedTiles",
            name = "Draw under claimed tiles",
            section = groupTilesSection,
            description = "Draw group tiles under player claimed tiles",
            position = 4
    )
    default boolean drawGroupTilesUnderClaimedTiles() {
        return true;
    }

    @Alpha
    @ConfigItem(
            // keyName here should not be made consistent as this is a legacy schema field
            // we don't want upgrading version to stop respecting old settings
            keyName = "groupTileBorderColor",
            name = "Border Color",
            section = groupTilesSection,
            description = "Border color of unlocked tiles",
            position = 5
    )
    default Color groupTileBorderColor() { return new Color(123,232,0,79); }

    @Alpha
    @ConfigItem(
            keyName = "groupTileFillColor",
            name = "Fill Color",
            section = groupTilesSection,
            description = "Fill color of group tiles",
            position = 6
    )
    default Color groupTileFillColor() { return new Color(0, 0, 0, 32); }

    @ConfigItem(
            keyName = "insetGroupTiles",
            name = "Inset",
            section = groupTilesSection,
            description = "Reduces the rendered tile size by 20%",
            position = 7
    )
    default boolean insetGroupTiles() {
        return false;
    }

    ///////////////////////////////////////////////////////////////////////////
    // completePath section

    @Alpha
    @ConfigItem(
            keyName = "completePathBorderColor",
            name = "Border Color",
            section = completePathSection,
            description = "Border color of path tiles on paths with all tiles claimed",
            position = 1
    )
    default Color completePathBorderColor() { return new Color(0, 255, 0, 64); }

    @Alpha
    @ConfigItem(
            keyName = "completePathFillColor",
            name = "Fill Color",
            section = completePathSection,
            description = "Fill color of path tiles on paths with all tiles claimed",
            position = 2
    )
    default Color completePathFillColor() { return new Color(0, 255, 0, 16); }

    @ConfigItem(
            keyName = "insetCompletePathTiles",
            name = "Inset",
            section = completePathSection,
            description = "Reduces the rendered tile size by 20%",
            position = 3
    )
    default boolean insetCompletePathTiles() { return true; }

    @ConfigItem(
            keyName = "animateCompletePathTiles",
            name = "Animate",
            section = completePathSection,
            description = "Makes the border of the tile smoothly pulse.",
            position = 4
    )
    default boolean animateCompletePathTiles() { return true; }

    ///////////////////////////////////////////////////////////////////////////
    // claimedPath section

    @Alpha
    @ConfigItem(
            keyName = "claimedPathBorderColor",
            name = "Border Color",
            section = claimedPathSection,
            description = "Border color of claimed path tiles on paths that are partially claimed",
            position = 1
    )
    default Color claimedPathBorderColor() { return new Color(0, 255, 0, 64); }

    @Alpha
    @ConfigItem(
            keyName = "claimedPathFillColor",
            name = "Fill Color",
            section = claimedPathSection,
            description = "Fill color of claimed path tiles on paths that are partially claimed",
            position = 2
    )
    default Color claimedPathFillColor() { return new Color(0, 255, 0, 16); }

    @ConfigItem(
            keyName = "insetClaimedPathTiles",
            name = "Inset",
            section = claimedPathSection,
            description = "Reduces the rendered tile size by 20%",
            position = 3
    )
    default boolean insetClaimedPathTiles() { return true; }

    @ConfigItem(
            keyName = "animateClaimedPathTiles",
            name = "Animate",
            section = claimedPathSection,
            description = "Makes the border of the tile smoothly pulse.",
            position = 4
    )
    default boolean animateClaimedPathTiles() { return true; }

    ///////////////////////////////////////////////////////////////////////////
    // unclaimedPath section

    @Alpha
    @ConfigItem(
            keyName = "unclaimedPathBorderColor",
            name = "Border Color",
            section = unclaimedPathSection,
            description = "Border color of unclaimed path tiles on paths that are partially claimed",
            position = 1
    )
    default Color unclaimedPathBorderColor() { return new Color(180, 180, 180, 96); }

    @Alpha
    @ConfigItem(
            keyName = "unclaimedPathFillColor",
            name = "Fill Color",
            section = unclaimedPathSection,
            description = "Fill color of unclaimed path tiles on paths that are partially claimed",
            position = 2
    )
    default Color unclaimedPathFillColor() { return new Color(180, 180, 180, 16); }

    @ConfigItem(
            keyName = "insetUnclaimedPathTiles",
            name = "Inset",
            section = unclaimedPathSection,
            description = "Reduces the rendered tile size by 20%",
            position = 3
    )
    default boolean insetUnclaimedPathTiles() { return true; }

    @ConfigItem(
            keyName = "animateUnclaimedPathTiles",
            name = "Animate",
            section = unclaimedPathSection,
            description = "Makes the border of the tile smoothly pulse.",
            position = 4
    )
    default boolean animateUnclaimedPathTiles() { return true; }

    @ConfigItem(
            keyName = "showWayfinderClaimCosts",
            name = "Show claim cost",
            section = unclaimedPathSection,
            description = "Shows the cost to unlock tiles on the wayfinder path.",
            position = 5
    )
    default boolean showClaimCosts() { return true; }

    @ConfigItem(
            keyName = "showWayfinderClaimCostsAsRemaining",
            name = "Show costs as remaining",
            section = unclaimedPathSection,
            description = "Shows the cost to unlock tiles as the balance remaining after claiming.",
            position = 6
    )
    default boolean showClaimCostsAsRemaining() { return false; }

    @ConfigItem(
            keyName = "wayfinderCostsTextColor",
            name = "Tile Costs Color",
            section = unclaimedPathSection,
            description = "Text color of claim costs",
            position = 7
    )
    default Color claimCostsColor() { return new Color(200, 200, 200); }

    ///////////////////////////////////////////////////////////////////////////
    // metrics section

    @ConfigItem(
            keyName = "showTileInfo",
            name = "Show Tile Info",
            description = "Shows additional information about the tile under the players cursor.",
            section = metricsSection,
            position = 1
    )
    default boolean showTileInfo() {
        return false;
    }

    @ConfigItem(
            keyName = "showPerformanceInfo",
            name = "Show Performance Info",
            description = "Shows additional information about plugin performance",
            section = metricsSection,
            position = 2
    )
    default boolean showPerformanceInfo() {
        return false;
    }

}
