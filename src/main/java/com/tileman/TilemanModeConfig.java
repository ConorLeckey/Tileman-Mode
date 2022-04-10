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

import static com.tileman.TilemanModeConfig.CONFIG_GROUP;

@ConfigGroup(CONFIG_GROUP)
public interface TilemanModeConfig extends Config {
    public static final String CONFIG_GROUP = "tilemanMode";

    @ConfigSection(
            name = "Settings",
            description = "Settings'",
            position = 2
    )
    String settingsSection = "settings";

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
            keyName = "drawOnMinimap",
            name = "Draw tiles on minimap",
            section = settingsSection,
            description = "Configures whether marked tiles should be drawn on minimap",
            position = 6
    )
    default boolean drawTilesOnMinimap() {
        return false;
    }

    @ConfigItem(
            keyName = "drawTilesOnWorldMap",
            name = "Draw tiles on world map",
            section = settingsSection,
            description = "Configures whether marked tiles should be drawn on world map",
            position = 5
    )
    default boolean drawTilesOnWorldMap() {
        return false;
    }

    @Alpha
    @ConfigItem(
            keyName = "markerColor",
            name = "Tile Color",
            section = settingsSection,
            description = "Configures the color of the tiles",
            position = 6
    )
    default Color markerColor() {
        return Color.YELLOW;
    }

    @ConfigItem(
            keyName = "showAdvancedOptions",
            name = "Show Advanced Options",
            section = settingsSection,
            description = "Show advanced options in the plugin panel",
            position = 7
    )
    default boolean showAdvancedOptions() {
        return false;
    }

}
