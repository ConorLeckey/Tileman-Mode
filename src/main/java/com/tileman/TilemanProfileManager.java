package com.tileman;

import lombok.Getter;
import net.runelite.client.config.ConfigManager;

public class TilemanProfileManager {

    private ConfigManager configManager;

    @Getter
    private TilemanGameRules gameRules;

    public  TilemanProfileManager(ConfigManager configManager) {
        this.configManager = configManager;
        this.gameRules = loadGameRulesFromLegacySaveData();
    }

    public void setGameMode(TilemanGameMode mode) {
        gameRules.gameMode = mode;
    }

    public void setCustomGameMode(boolean state) {
        gameRules.enableCustomGameMode = state;
    }

    public void setAllowTileDeficit(boolean state) {
        gameRules.allowTileDeficit = state;
    }

    public void setIncludeTotalLevel(boolean state) {
        gameRules.includeTotalLevel = state;
    }

    public void setExcludeExp(boolean state) {
        gameRules.excludeExp = state;
    }

    public void setTileOffset(int offset) {
        gameRules.tilesOffset = offset;
    }

    public void setExpPerTile(int exp) {
        gameRules.expPerTile = exp;
    }

    private TilemanGameRules loadGameRulesFromLegacySaveData() {
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

    <T> T getFromConfigOrDefault(String configGroup, String key, Class<T> clazz, T defaultVal) {
        try {
            T val = configManager.getConfiguration(configGroup, key, clazz);
            if (val != null) {
                return val;
            }
        } catch (ClassCastException e) {}
        return defaultVal;
    }
}
