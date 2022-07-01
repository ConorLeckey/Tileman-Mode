/*
 * Copyright (c) 2016-2017, Adam <Adam@sigterm.info>
 * Copyright (c) 2020, ConorLeckey <https://github.com/ConorLeckey>
 * Copyright (c) 2022, Elg <https://github.com/elgbar>
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
import java.lang.reflect.Type;
import java.util.*;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;

@Singleton
@Slf4j
class TileRepository {

    public static final String CONFIG_GROUP = "tilemanMode";
    public static final String REGION_PREFIX = "region_";
    private static final Type TILE_SET_TYPE = new TypeToken<Set<TilemanModeTile>>() {}.getType();

    private static final Gson GSON = new Gson();

    @Inject private ConfigManager configManager;
    @Inject private Client client;
    @Inject private TilemanModeConfigEvaluator config;

    @Getter private int totalTilesUsed, remainingTiles, xpUntilNextTile;

    private static List<String> removeRegionPrefixes(List<String> regions) {
        List<String> trimmedRegions = new ArrayList<>();
        for (String region : regions) {
            trimmedRegions.add(removeRegionPrefix(region));
        }
        return trimmedRegions;
    }

    private static String removeRegionPrefix(String region) {
        return region.substring(region.indexOf('_') + 1);
    }

    List<String> getAllRegionIds(String configGroup) {
        return removeRegionPrefixes(configManager.getConfigurationKeys(configGroup + ".region"));
    }

    public void savePoints(int regionId, Collection<TilemanModeTile> points) {
        savePoints(regionId + "", points);
    }

    public void savePoints(String regionId, Collection<TilemanModeTile> points) {
        long start = System.currentTimeMillis();
        if (points == null || points.isEmpty()) {
            configManager.unsetConfiguration(CONFIG_GROUP, REGION_PREFIX + regionId);
            return;
        }

        String json = GSON.toJson(points);
        configManager.setConfiguration(CONFIG_GROUP, REGION_PREFIX + regionId, json);
        log.info("Took {}ms to save config", System.currentTimeMillis() - start);
    }

    public Set<TilemanModeTile> getConfiguration(String configGroup, String key) {

        long start = System.currentTimeMillis();
        String json = configManager.getConfiguration(configGroup, key);

        if (Strings.isNullOrEmpty(json)) {
            return new HashSet<>();
        }

        Set<TilemanModeTile> tilemanModeTiles = GSON.fromJson(json, TILE_SET_TYPE);
        log.info("Took {}ms to get config", System.currentTimeMillis() - start);
        assert tilemanModeTiles != null;
        return tilemanModeTiles;
    }

    Set<TilemanModeTile> getTiles(int regionId) {
        return getTiles(regionId + "");
    }

    public Set<TilemanModeTile> getTiles(String regionId) {
        return getConfiguration(CONFIG_GROUP, REGION_PREFIX + regionId);
    }

    void updateTileCounter() {
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
        xpUntilNextTile =
                config.expPerTile()
                        - Integer.parseInt(
                                Long.toString(client.getOverallExperience() % config.expPerTile()));
    }
}
