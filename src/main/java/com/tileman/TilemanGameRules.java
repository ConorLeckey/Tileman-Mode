package com.tileman;

import java.io.Serializable;

public class TilemanGameRules implements Serializable {
    TilemanGameMode gameMode;

    boolean enableCustomGameMode;
    boolean allowTileDeficit;
    int tilesOffset;
    boolean includeTotalLevel;
    boolean excludeExp;
    int expPerTile;
}
