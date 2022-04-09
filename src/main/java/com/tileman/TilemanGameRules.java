package com.tileman;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

public class TilemanGameRules implements Serializable {
    public static final TilemanGameRules DEFAULT_RULES = new TilemanGameRules(
            TilemanGameMode.COMMUNITY,
            false,
            false,
            false,
            false,
            9,
            1000
    );

    public TilemanGameRules(){}
    public TilemanGameRules(TilemanGameMode gameMode, boolean enableCustomGameMode, boolean allowTileDeficit, boolean includeTotalLevel, boolean excludeExp, int tilesOffset, int expPerTile) {
        this.gameMode = gameMode;
        this.enableCustomGameMode = enableCustomGameMode;
        this.allowTileDeficit = allowTileDeficit;
        this.includeTotalLevel = includeTotalLevel;
        this.excludeExp = excludeExp;
        this.tilesOffset = tilesOffset;
        this.expPerTile = expPerTile;
    }

    @Getter @Setter private TilemanGameMode gameMode;
    @Getter @Setter private boolean enableCustomGameMode;
    @Getter @Setter private boolean allowTileDeficit;
    @Getter @Setter private boolean excludeExp;
    @Getter @Setter private int expPerTile;

    @Setter private boolean includeTotalLevel;
    @Setter private int tilesOffset;

    public boolean isIncludeTotalLevel() {
        if (enableCustomGameMode) {
            return includeTotalLevel;
        } else {
            return isIncludeTotalLevelByGameMode();
        }
    }

    private boolean isIncludeTotalLevelByGameMode() {
        switch (gameMode) {
            case ACCELERATED:
                return true;
            default:
                return false;
        }
    }

    public int getTilesOffset() {
        if (enableCustomGameMode) {
            return tilesOffset;
        } else {
            return getTilesOffsetByGameMode();
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
