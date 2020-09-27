/*
 * Copyright (c) 2016-2017, Adam <Adam@sigterm.info>
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
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import java.awt.*;

import static net.runelite.api.MenuAction.RUNELITE_OVERLAY_CONFIG;
import static net.runelite.client.ui.overlay.OverlayManager.OPTION_CONFIGURE;

class TileInfoOverlay extends OverlayPanel {
    private final Client client;
    private final TilemanModeConfig config;
    private final TilemanModePlugin plugin;

    @Inject
    private TileInfoOverlay(Client client, TilemanModeConfig config, TilemanModePlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(OverlayPriority.MED);
        getMenuEntries().add(new OverlayMenuEntry(RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "Tileman Mode overlay"));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        String unspentTiles = String.valueOf(plugin.getRemainingTiles());
        String unlockedTiles = String.valueOf(plugin.getTotalTiles());
        int xpTowardsNextTile = Integer.parseInt(StringUtils.right(Long.toString(client.getOverallExperience()), 3));
        String xpUntilNextTile = String.valueOf(1000 - xpTowardsNextTile);

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Available Tiles:")
                .leftColor(getTextColor())
                .right(unspentTiles)
                .rightColor(getTextColor())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("XP Until Next Tile:")
                .right(xpUntilNextTile)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Tiles Unlocked:")
                .right(unlockedTiles)
                .build());

        panelComponent.setPreferredSize(new Dimension(
                110 + graphics.getFontMetrics().stringWidth(unlockedTiles),
                0));

        return super.render(graphics);
    }

    private Color getTextColor() {
        if (plugin.getRemainingTiles() <= 0) {
            return Color.RED;
        } else if (plugin.getRemainingTiles() < config.warningLimit()) {
            return Color.ORANGE;
        }
        return Color.WHITE;
    }
}
