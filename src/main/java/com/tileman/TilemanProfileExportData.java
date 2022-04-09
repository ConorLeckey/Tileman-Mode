package com.tileman;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TilemanProfileExportData implements Serializable {

    public TilemanProfile profile;
    public ArrayList<Integer> regionIds;
    public ArrayList<List<TilemanModeTile>> regionTiles;

    public TilemanProfileExportData(TilemanProfile profile, Map<Integer, List<TilemanModeTile>> tileDataByRegion) {
        this.profile = profile;
        this.regionIds = new ArrayList<>();
        this.regionTiles = new ArrayList<>();
        for (Integer regionId : tileDataByRegion.keySet()) {
            regionIds.add(regionId);
            regionTiles.add(tileDataByRegion.get(regionId));
        }
    }
}
