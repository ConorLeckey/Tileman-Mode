/*
 * Copyright (c) 2018, TheLonelyDev <https://github.com/TheLonelyDev>
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * Copyright (c) 2020, ConorLeckey <https://github.com/ConorLeckey>
 * Copyright (c) 2022, Colton Campbell <https://github.com/Notloc>
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
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.config.ConfigManager;

import java.util.*;
import java.util.function.Consumer;

@Slf4j
public class TilemanProfileManager {

    private static final String LEGACY_REGION_PREFIX = "region_";
    private static final Gson GSON = new Gson();

    private ConfigManager configManager;
    private Client client;

    private TilemanGameRules gameRules = TilemanGameRules.GetDefaultRules();
    private TilemanProfile activeProfile = TilemanProfile.NONE;

    @Getter(AccessLevel.PACKAGE)
    private Map<Integer, List<TilemanModeTile>> tilesByRegion = new HashMap<>();

    public List<Consumer<TilemanProfile>> onProfileChangedEvent = new ArrayList<>();

    public  TilemanProfileManager(TilemanModePlugin plugin, Client client, ConfigManager configManager) {
        this.client = client;
        this.configManager = configManager;
        plugin.onLoginStateChangedEvent.add(state -> onLoginStateChanged(state));
    }

    private void onLoginStateChanged(GameState gameState) {
        if (gameState == GameState.LOGGED_IN) {
            TilemanProfile profile = getProfileForAccount(client.getAccountHash());
            setActiveProfile(profile);
        } else {
            setActiveProfile(TilemanProfile.NONE);
        }
    }

    private TilemanProfile getProfileForAccount(long accountHash) {
        String key = TilemanProfile.getProfileKey(String.valueOf(accountHash));
        return getJsonFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, key, TilemanProfile.class, TilemanProfile.NONE);
    }

    void setActiveProfile(TilemanProfile profile) {
        this.activeProfile = profile;
        this.gameRules = loadGameRules(profile);

        tilesByRegion.clear();
        tilesByRegion = loadAllTiles(profile, configManager);

        onProfileChangedEvent.forEach(l -> l.accept(profile));
    }

    private void saveProfile(TilemanProfile profile) {
        if (profile.equals(TilemanProfile.NONE)) {
            return;
        }
        configManager.setConfiguration(TilemanModeConfig.CONFIG_GROUP, TilemanProfile.getProfileKey(profile.getAccountHash()), GSON.toJson(profile));
    }

    TilemanProfile createProfile() {
        long accountHash = client.getAccountHash();
        if (accountHash == -1) {
            return TilemanProfile.NONE;
        }

        TilemanProfile profile = new TilemanProfile(accountHash, client.getLocalPlayer().getName());
        saveProfile(profile);
        return profile;
    }

    TilemanProfile createProfileWithLegacyData() {
        TilemanProfile profile = createProfile();
        saveAllTiles(profile, loadAllLegacyTilesFromConfig(configManager));
        saveGameRules(profile, loadGameRulesFromLegacySaveDataOrUseDefaults());
        return profile;
    }

    TilemanProfile createProfileWithGroundMarkerData() {
        TilemanProfile profile = createProfile();
        importGroundMarkerTilesToProfile(profile);
        return profile;
    }

    public TilemanProfile getActiveProfile() {
        return activeProfile;
    }

    public boolean hasActiveProfile() {
        return !activeProfile.equals(TilemanProfile.NONE);
    }

    String exportProfile() {
        if (!hasActiveProfile()) {
            return "";
        }
        return GSON.toJson(new TilemanProfileExportData(activeProfile, tilesByRegion));
    }

    TilemanProfile importProfileAsNew(String maybeJson, long accountHash) {
        TilemanProfileExportData importedProfileData = null;
        try {
            importedProfileData = GSON.fromJson(maybeJson, TilemanProfileExportData.class);
        } catch (JsonParseException e) {}

        if (importedProfileData == null || importedProfileData.regionIds.size() != importedProfileData.regionTiles.size()) {
            return TilemanProfile.NONE;
        }

        TilemanProfile profile = new TilemanProfile(accountHash, client.getLocalPlayer().getName());
        saveProfile(profile);
        for (int i = 0; i < importedProfileData.regionIds.size(); i++) {
            int regionId = importedProfileData.regionIds.get(i);
            List<TilemanModeTile> tiles = importedProfileData.regionTiles.get(i);
            saveTiles(profile, regionId, tiles);
        }
        return profile;
    }

    void deleteActiveProfile() {
        if (activeProfile.equals(TilemanProfile.NONE)) {
            return;
        }

        String groupPrefix = TilemanModeConfig.CONFIG_GROUP + ".";

        List<String> regionKeys = configManager.getConfigurationKeys(groupPrefix + activeProfile.getRegionPrefix());
        for (int i = 0; i < regionKeys.size(); i++) {
            regionKeys.set(i, regionKeys.get(i).replace(groupPrefix, ""));
        }
        for (String key : regionKeys) {
            configManager.unsetConfiguration(TilemanModeConfig.CONFIG_GROUP, key);
        }
        configManager.unsetConfiguration(TilemanModeConfig.CONFIG_GROUP, activeProfile.getProfileKey());
        configManager.unsetConfiguration(TilemanModeConfig.CONFIG_GROUP, activeProfile.getGameRulesKey());

        setActiveProfile(TilemanProfile.NONE);
    }

    private TilemanGameRules loadGameRules(TilemanProfile profile) {
        String rulesKey = profile.getGameRulesKey();
        return getJsonFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, rulesKey, TilemanGameRules.class, TilemanGameRules.GetDefaultRules());
    }

    private void saveGameRules(TilemanProfile profile, TilemanGameRules rules) {
        if (profile.equals(TilemanProfile.NONE)) {
            return;
        }
        String rulesKey = profile.getGameRulesKey();
        configManager.setConfiguration(TilemanModeConfig.CONFIG_GROUP, rulesKey, GSON.toJson(rules));
    }

    public TilemanGameMode getGameMode() { return gameRules.getGameMode(); }
    public void setGameMode(TilemanGameMode mode) {
        gameRules.setGameMode(mode);
        saveGameRules(activeProfile, gameRules);
    }

    public boolean isEnableCustomGameMode() { return gameRules.isEnableCustomGameMode(); }
    public void setEnableCustomGameMode(boolean state) {
        gameRules.setEnableCustomGameMode(state);
        saveGameRules(activeProfile, gameRules);
    }

    public boolean isAllowTileDeficit() { return gameRules.isAllowTileDeficit(); }
    public void setAllowTileDeficit(boolean state) {
        gameRules.setAllowTileDeficit(state);
        saveGameRules(activeProfile, gameRules);
    }

    public boolean isTilesFromTotalLevel() { return gameRules.isTilesFromTotalLevel(); }
    public void setTilesFromTotalLevel(boolean state) {
        gameRules.setTilesFromTotalLevel(state);
        saveGameRules(activeProfile, gameRules);
    }

    public boolean isTilesFromExp() { return gameRules.isTilesFromExp(); }
    public void setTilesFromExp(boolean state) {
        gameRules.setTilesFromExp(state);
        saveGameRules(activeProfile, gameRules);
    }

    public int getTilesOffset() { return gameRules.getTilesOffset(); }
    public void setTilesOffset(int offset) {
        gameRules.setTilesOffset(offset);
        saveGameRules(activeProfile, gameRules);
    }

    public int getExpPerTile() { return gameRules.getExpPerTile(); }
    public void setExpPerTile(int exp) {
        gameRules.setExpPerTile(exp);
        saveGameRules(activeProfile, gameRules);
    }


    private void saveAllTiles(TilemanProfile profile, Map<Integer, List<TilemanModeTile>> tileData) {
        for (Integer region : tileData.keySet()) {
            saveTiles(profile, region, tileData.get(region));
        }
    }

    public void saveTiles(TilemanProfile profile, int regionId, Collection<TilemanModeTile> tiles) {
        if (profile.equals(TilemanProfile.NONE)) {
            return;
        }
        String regionKey = profile.getRegionKey(regionId);

        if (tiles == null || tiles.isEmpty()) {
            configManager.unsetConfiguration(TilemanModeConfig.CONFIG_GROUP, regionKey);
            return;
        }

        String json = GSON.toJson(tiles);
        configManager.setConfiguration(TilemanModeConfig.CONFIG_GROUP, regionKey, json);
    }

    public static Map<Integer, List<TilemanModeTile>> loadAllTiles(TilemanProfile profile, ConfigManager configManager) {
        Map<Integer, List<TilemanModeTile>> tilesByRegion = new HashMap<>();

        List<String> regionStrings = configManager.getConfigurationKeys(TilemanModeConfig.CONFIG_GROUP + "." + profile.getRegionPrefix());
        regionStrings = removeKeyPrefixes(regionStrings, TilemanModeConfig.CONFIG_GROUP, profile.getRegionPrefix());

        List<Integer> regions = new ArrayList<>();
        for (String regionString : regionStrings) {
            Integer region = Integer.valueOf(regionString);
            if (region != null) {
                regions.add(region);
            }
        }

        for (int regionId : regions) {
            List<TilemanModeTile> points = loadTilesByRegion(configManager, profile, regionId);
            tilesByRegion.put(regionId, points);
        }

        return tilesByRegion;
    }

    private static List<TilemanModeTile> loadTilesByRegion(ConfigManager configManager, TilemanProfile profile, int regionId) {
        String regionKey = profile.getRegionKey(regionId);
        return loadTilesFromConfig(configManager, TilemanModeConfig.CONFIG_GROUP, regionKey);
    }

    private static List<String> removeKeyPrefixes(List<String> keys, String configGroup, String keyPrefix) {
        String fullPrefix = configGroup + "." + keyPrefix;
        List<String> trimmedRegions = new ArrayList<String>();
        for (String region : keys) {
            trimmedRegions.add(region.replace(fullPrefix, ""));
        }
        return trimmedRegions;
    }


    // LEGACY STUFF

    private TilemanGameRules loadGameRulesFromLegacySaveDataOrUseDefaults() {
        TilemanGameRules defaults = TilemanGameRules.GetDefaultRules();
        TilemanGameRules rules = new TilemanGameRules();
        rules.setGameMode(getFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, "gameMode", TilemanGameMode.class, defaults.getGameMode()));
        rules.setAllowTileDeficit(getFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, "allowTileDeficit", boolean.class, defaults.isAllowTileDeficit()));
        rules.setEnableCustomGameMode(getFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, "enableCustomGameMode", boolean.class, defaults.isEnableCustomGameMode()));
        rules.setTilesOffset(getFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, "tilesOffset", int.class, defaults.getTilesOffset()));
        rules.setTilesFromTotalLevel(getFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, "includeTotalLevels", boolean.class, defaults.isTilesFromTotalLevel()));
        rules.setTilesFromExp(!getFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, "excludeExp", boolean.class, !defaults.isTilesFromExp())); // Negations are intentional due to the option being renamed to the opposite meaning
        rules.setExpPerTile(getFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, "expPerTile", int.class, defaults.getExpPerTile()));
        return rules;
    }

    private static Map<Integer, List<TilemanModeTile>> loadAllLegacyTilesFromConfig(ConfigManager configManager) {
        Map<Integer, List<TilemanModeTile>> tileData = new HashMap<>();
        String configGroup = TilemanModeConfig.CONFIG_GROUP;

        List<String> regionIds = getAllLegacyRegionIds(configManager, configGroup);
        for (String regionIdString : regionIds) {
            Integer regionId = Integer.valueOf(regionIdString);
            List<TilemanModeTile> tiles = loadTilesFromConfig(configManager, configGroup, "region_" + regionIdString);

            if (!tiles.isEmpty() && regionId != null) {
                tileData.put(regionId, tiles);
            }
        }

        return tileData;
    }

    private static List<String> getAllLegacyRegionIds(ConfigManager configManager, String configGroup) {
        List<String> keys = configManager.getConfigurationKeys(configGroup + ".region");
        return removeKeyPrefixes(keys, configGroup,"region_");
    }

    private void importGroundMarkerTilesToProfile(TilemanProfile profile) {
        Map<Integer, List<TilemanModeTile>> profileTilesByRegion = loadAllTiles(profile, configManager);

        List<String> groundMarkerRegions = getAllLegacyRegionIds(configManager, "groundMarker");
        for (String region : groundMarkerRegions) {
            int regionId = Integer.parseInt(region);

            Set<TilemanModeTile> groundMarkerTiles = new HashSet<>(loadTilesFromConfig(configManager, "groundMarker", LEGACY_REGION_PREFIX + region));
            groundMarkerTiles.addAll(profileTilesByRegion.getOrDefault(regionId, Collections.emptyList()));

            saveTiles(profile, regionId, groundMarkerTiles);
        }
    }

    // CONFIG STUFF

    private static List<TilemanModeTile> loadTilesFromConfig(ConfigManager configManager, String configGroup, String key) {
        String json = configManager.getConfiguration(configGroup, key);
        if (Strings.isNullOrEmpty(json)) {
            return Collections.emptyList();
        }

        return GSON.fromJson(json, new TypeToken<List<TilemanModeTile>>(){}.getType());
    }

    <T> T getJsonFromConfigOrDefault(String configGroup, String key, Class<T> clazz, T defaultVal) {
        String json = getFromConfigOrDefault(configGroup, key, String.class, "");
        if (Strings.isNullOrEmpty(json)) {
            return defaultVal;
        }

        try {
            return GSON.fromJson(json, clazz);
        } catch (JsonSyntaxException e) {
            return defaultVal;
        }
    }

    <T> T getFromConfigOrDefault(String configGroup, String key, Class<T> clazz, T defaultVal) {
        try {
            Object val = configManager.getConfiguration(configGroup, key, clazz);
            if (val != null && clazz.isAssignableFrom(val.getClass())) {
                return (T)val;
            }
        } catch (ClassCastException e) {}
        return defaultVal;
    }
}
