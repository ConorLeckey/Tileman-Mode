package com.tileman;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

public class TilemanGameRules implements Serializable {

    @Getter @Setter private TilemanGameMode gameMode;
    @Getter @Setter private boolean enableCustomGameMode;

    @Setter private boolean allowTileDeficit;
    @Setter private boolean tilesFromExp;
    @Setter private int expPerTile;
    @Setter private boolean tilesFromTotalLevel;
    @Setter private int tilesOffset;

    public TilemanGameRules(){}

    public TilemanGameRules(TilemanGameMode gameMode, boolean enableCustomGameMode, boolean allowTileDeficit, boolean tilesFromTotalLevel, boolean tilesFromExp, int tilesOffset, int expPerTile) {
        this.gameMode = gameMode;
        this.enableCustomGameMode = enableCustomGameMode;
        this.allowTileDeficit = allowTileDeficit;
        this.tilesFromTotalLevel = tilesFromTotalLevel;
        this.tilesFromExp = tilesFromExp;
        this.tilesOffset = tilesOffset;
        this.expPerTile = expPerTile;
    }

    public static TilemanGameRules GetDefaultRules() {
        return new TilemanGameRules(
                TilemanGameMode.COMMUNITY,
                false,
                false,
                false,
                true,
                9,
                1000
        );
    }

    public boolean isTilesFromTotalLevel() {
        return enableCustomGameMode ? tilesFromTotalLevel : getTilesFromTotalLevelByGameMode();
    }

    public int getTilesOffset() {
        return enableCustomGameMode ? tilesOffset : getTilesOffsetByGameMode();
    }

    public boolean isAllowTileDeficit() {
        return enableCustomGameMode ? allowTileDeficit : false;
    }

    public boolean isTilesFromExp() {
        return enableCustomGameMode ? tilesFromExp : true;
    }

    public int getExpPerTile() {
        return enableCustomGameMode ? expPerTile : 1000;
    }

    private boolean getTilesFromTotalLevelByGameMode() {
        switch (gameMode) {
            case ACCELERATED:
                return true;
            default:
                return false;
        }
    }

    private int getTilesOffsetByGameMode() {
        switch (gameMode) {
            case COMMUNITY:
                return 9;
            default:
                return 0;
        }
    }
}
