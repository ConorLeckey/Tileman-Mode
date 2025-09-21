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

import net.runelite.api.CollisionData;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

import javax.inject.Inject;
import java.awt.*;

import static net.runelite.api.MenuAction.RUNELITE_OVERLAY_CONFIG;
import static net.runelite.client.ui.overlay.OverlayManager.OPTION_CONFIGURE;

class TileInfoOverlay extends OverlayPanel {
    private final TilemanModeConfig config;
    private final TilemanModePlugin plugin;

    private final static String UNSPENT_TILES_STRING = "Available Tiles:";
    private final static String XP_UNTIL_NEXT_TILE = "XP Until Next Tile:";
    private final static String UNLOCKED_TILES = "Tiles Unlocked:";
    private final static String[] STRINGS = new String[] {
        UNSPENT_TILES_STRING,
        XP_UNTIL_NEXT_TILE,
        UNLOCKED_TILES,
    };

    @Inject
    private TileInfoOverlay(TilemanModeConfig config, TilemanModePlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(Overlay.PRIORITY_MED);
        getMenuEntries().add(new OverlayMenuEntry(RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "Tileman Mode overlay"));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        String unspentTiles = addCommasToNumber(plugin.getRemainingTiles());
        String unlockedTiles = addCommasToNumber(plugin.getTotalTiles());
        String xpUntilNextTile = addCommasToNumber(plugin.getXpUntilNextTile());

        panelComponent.getChildren().add(LineComponent.builder()
                .left(UNSPENT_TILES_STRING)
                .leftColor(getTextColor())
                .right(unspentTiles)
                .rightColor(getTextColor())
                .build());

        if(!(config.enableCustomGameMode() && config.excludeExp())) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(XP_UNTIL_NEXT_TILE)
                    .right(xpUntilNextTile)
                    .build());
        }

        panelComponent.getChildren().add(LineComponent.builder()
                .left(UNLOCKED_TILES)
                .right(unlockedTiles)
                .build());

        LocalPoint target = plugin.getClient().getSelectedSceneTile().getLocalLocation();
        if (target != null) {

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("RegionID")
                    .right(String.valueOf(plugin.hoverTile.getRegionID()))
                    .build());

            int regionX = plugin.hoverTile.getRegionX();
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("RegionX")
                    .right(String.valueOf(regionX))
                    .build());

            int regionY = plugin.hoverTile.getRegionY();
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("RegionY")
                    .right(String.valueOf(regionY))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("RegionPlane")
                    .right(String.valueOf(plugin.hoverTile.getPlane()))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Tile")
                    .right(plugin.getTilesToRender().contains(plugin.hoverTile) ? "Unlocked" : "Locked")
                    .build());

            /*

            int wvID = plugin.getClient().getTopLevelWorldView().getId();
            CollisionData[] cd = plugin.getClient().getWorldView(wvID).getCollisionMaps();
            int plane = plugin.getClient().getTopLevelWorldView().getPlane();

            if (cd != null) {

                int[][] collisionDataFlags = cd[plane].getFlags();
                int targetFlags = collisionDataFlags[target.getSceneX()][target.getSceneY()];

                WorldArea travelCheck = plugin.hoverTile.toWorldArea();
                WorldView wv = plugin.getClient().getWorldView(wvID);
                int southOnYAxis = -1;
                int northOnYAxis = 1;
                int eastOnXAxis = 1;
                int westOnXAxis = -1;

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Tile Collision Data:")
                        .right("")
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("North")
                        .right(String.valueOf(travelCheck.canTravelInDirection(wv, 0, northOnYAxis)))
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("South")
                        .right(String.valueOf(travelCheck.canTravelInDirection(wv, 0, southOnYAxis)))
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("East")
                        .right(String.valueOf(travelCheck.canTravelInDirection(wv, eastOnXAxis, 0)))
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("West")
                        .right(String.valueOf(travelCheck.canTravelInDirection(wv, westOnXAxis, 0)))
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("NorthEast")
                        .right(String.valueOf(travelCheck.canTravelInDirection(wv, eastOnXAxis, northOnYAxis)))
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("NorthWest")
                        .right(String.valueOf(travelCheck.canTravelInDirection(wv, westOnXAxis, northOnYAxis)))
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("SouthEast")
                        .right(String.valueOf(travelCheck.canTravelInDirection(wv, eastOnXAxis, southOnYAxis)))
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("SouthWest")
                        .right(String.valueOf(travelCheck.canTravelInDirection(wv, westOnXAxis, southOnYAxis)))
                        .build());

            }

            */

        }

        panelComponent.setPreferredSize(new Dimension(
                getLongestStringWidth(STRINGS, graphics)
                        + getLongestStringWidth(new String[] {unlockedTiles, unspentTiles}, graphics),
                0));

        return super.render(graphics);
    }

    private Color getTextColor() {
        if(config.enableTileWarnings()) {
            if (plugin.getRemainingTiles() <= 0) {
                return Color.RED;
            } else if (plugin.getRemainingTiles() <= config.warningLimit()) {
                return Color.ORANGE;
            }
        }
        return Color.WHITE;
    }

    private int getLongestStringWidth(String[] strings, Graphics2D graphics) {
        int longest = graphics.getFontMetrics().stringWidth("000000");
        for(String i: strings) {
            int currentItemWidth = graphics.getFontMetrics().stringWidth(i);
            if(currentItemWidth > longest) {
                longest = currentItemWidth;
            }
        }
        return longest;
    }

    private String addCommasToNumber(int number) {
        String input = Integer.toString(number);
        StringBuilder output = new StringBuilder();
        for(int x = input.length() - 1; x >= 0; x--) {
            int lastPosition = input.length() - x - 1;
            if(lastPosition != 0 && lastPosition % 3 == 0) {
                output.append(",");
            }
            output.append(input.charAt(x));
        }
        return output.reverse().toString();
    }
}
