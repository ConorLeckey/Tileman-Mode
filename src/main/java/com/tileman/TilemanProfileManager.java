package com.tileman;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class TilemanProfileManager {

    private static final String REGION_PREFIX = "region_";
    private static final Gson GSON = new Gson();

    private ConfigManager configManager;
    private Client client;

    @Getter
    private TilemanGameRules gameRules;
    private TilemanProfile activeProfile = TilemanProfile.NONE;

    @Getter
    private List<TilemanModeTile> tiles;

    public List<Consumer<TilemanProfile>> onProfileChanged = new ArrayList<>();

    public  TilemanProfileManager(Client client, ConfigManager configManager) {
        this.client = client;
        this.configManager = configManager;
        this.gameRules = loadGameRules();
        if (this.gameRules == null) {
            this.gameRules = loadGameRulesFromLegacySaveDataOrUseDefaults();
        }
    }

    public void setGameMode(TilemanGameMode mode) {
        gameRules.gameMode = mode;
        saveGameRules(gameRules);
    }

    public void setCustomGameMode(boolean state) {
        gameRules.enableCustomGameMode = state;
        saveGameRules(gameRules);
    }

    public void setAllowTileDeficit(boolean state) {
        gameRules.allowTileDeficit = state;
        saveGameRules(gameRules);
    }

    public void setIncludeTotalLevel(boolean state) {
        gameRules.includeTotalLevel = state;
        saveGameRules(gameRules);
    }

    public void setExcludeExp(boolean state) {
        gameRules.excludeExp = state;
        saveGameRules(gameRules);
    }

    public void setTileOffset(int offset) {
        gameRules.tilesOffset = offset;
        saveGameRules(gameRules);
    }

    public void setExpPerTile(int exp) {
        gameRules.expPerTile = exp;
        saveGameRules(gameRules);
    }



    private TilemanGameRules loadGameRules() {
        return getJsonFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, "gameRules", TilemanGameRules.class, null);
    }

    void saveGameRules(TilemanGameRules rules) {
        configManager.setConfiguration(TilemanModeConfig.CONFIG_GROUP, "gameRules", GSON.toJson(rules));
    }













    void saveProfile(TilemanProfile profile) {
        if (profile.getAccountHash() == -1) {
            return;
        }
        configManager.setConfiguration(TilemanModeConfig.CONFIG_GROUP, "profile_" + profile.getGuid(), GSON.toJson(profile));
    }


    List<TilemanProfile> getProfiles() {
        List<String> profileKeys = configManager.getConfigurationKeys(TilemanModeConfig.CONFIG_GROUP+".profile");
        List<TilemanProfile> profiles = new ArrayList<>();
        for (String key : profileKeys) {
            key = key.replace(TilemanModeConfig.CONFIG_GROUP+".", "");
            TilemanProfile profile = getJsonFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, key, TilemanProfile.class, null);
            if (profile != null) {
                profiles.add(profile);
            }
        }
        return profiles;
    }


    boolean createProfile() {
        long accountHash = client.getAccountHash();
        if (accountHash == -1) {
            return false;
        }

        TilemanProfile profile = new TilemanProfile(accountHash, client.getLocalPlayer().getName());
        saveProfile(profile);
        setActiveProfile(profile);
        return true;
    }



    TilemanProfile getActiveProfile() {
        return activeProfile;
    }

    void setActiveProfile(TilemanProfile profile) {
        this.activeProfile = profile;
        onProfileChanged.forEach(l -> l.accept(profile));
    }





    public void saveTiles(int regionId, Collection<TilemanModeTile> tiles) {
        if (tiles == null || tiles.isEmpty()) {
            configManager.unsetConfiguration(TilemanModeConfig.CONFIG_GROUP, REGION_PREFIX + regionId);
            return;
        }

        String json = GSON.toJson(tiles);
        configManager.setConfiguration(TilemanModeConfig.CONFIG_GROUP, REGION_PREFIX + regionId, json);
    }

    public Collection<TilemanModeTile> loadTiles(String region) {
        int regionId = Integer.parseInt(removeRegionPrefix(region));
        return loadTiles(regionId);
    }

    public Collection<TilemanModeTile> loadTiles(int regionId) {
        String json = getFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, REGION_PREFIX + regionId, String.class, "");
        if (Strings.isNullOrEmpty(json)) {
            return Collections.emptyList();
        }
        return GSON.fromJson(json, new TypeToken<List<TilemanModeTile>>() {}.getType());
    }

    public void loadAllTiles() {
        points.clear();

        int[] regions = client.getMapRegions();

        if (regions == null) {
            return;
        }

        for (int regionId : regions) {
            // load points for region
            log.debug("Loading points for region {}", regionId);
            Collection<WorldPoint> worldPoint = translateToWorldPoint(profileManager.loadTiles(regionId));
            points.addAll(worldPoint);
        }
        updateTileCounter();
    }














    private TilemanGameRules loadGameRulesFromLegacySaveDataOrUseDefaults() {
        TilemanGameRules rules = new TilemanGameRules();
        rules.gameMode = getFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, "gameMode", TilemanGameMode.class, TilemanGameMode.COMMUNITY);
        rules.allowTileDeficit = getFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, "allowTileDeficit", boolean.class, false);
        rules.enableCustomGameMode = getFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, "enableCustomGameMode", boolean.class, false);
        rules.tilesOffset = getFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, "tilesOffset", int.class, 9);
        rules.includeTotalLevel = getFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, "includeTotalLevels", boolean.class, false);
        rules.excludeExp = getFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, "excludeExp", boolean.class, false);
        rules.expPerTile = getFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, "expPerTile", int.class, 1000);
        return rules;
    }

    public boolean hasLegacySaveData() {
        List<String> oldKeys = configManager.getConfigurationKeys(TilemanModeConfig.CONFIG_GROUP+".region");
        return !oldKeys.isEmpty();
    }






    public void importGroundMarkerTiles() {
        // Get and store all the Ground Markers Regions
        List<String> groundMarkerRegions = getAllRegionIds("groundMarker");
        // If none, Exit function

        // Get and store array list of existing Tileman World Regions (like updateTileCounter does)
        List<String> tilemanModeRegions = getAllRegionIds(TilemanModeConfig.CONFIG_GROUP);

        // CONVERSION
        // Loop through Ground Marker Regions
        for (String region : groundMarkerRegions) {
            // Get Ground Markers Region's Tiles
            ArrayList<TilemanModeTile> groundMarkerTiles =
                    new ArrayList<>(getConfiguration("groundMarker", REGION_PREFIX + region));
            // If region already exists in Tileman World Regions Array:
            if (tilemanModeRegions.contains(region)) {
                // Create Empty ArrayList for Region;
                // Get Tileman Region's tiles and add them to the region array list
                ArrayList<TilemanModeTile> regionTiles = new ArrayList<>(loadTiles(region));

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
                    saveTiles(Integer.parseInt(region), regionTiles);
                }
            } else {
                // Save points for that region
                saveTiles(Integer.parseInt(region), groundMarkerTiles);
            }
        }
        loadAllTiles();
    }

    List<String> getAllRegionIds(String configGroup) {
        return removeRegionPrefixes(configManager.getConfigurationKeys(configGroup + ".region"));
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

    private Collection<TilemanModeTile> getConfiguration(String configGroup, String key) {
        String json = configManager.getConfiguration(configGroup, key);

        if (Strings.isNullOrEmpty(json)) {
            return Collections.emptyList();
        }

        return GSON.fromJson(json, new TypeToken<List<TilemanModeTile>>() {
        }.getType());
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
