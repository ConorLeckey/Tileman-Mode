package com.tileman;

import lombok.Getter;

import java.io.Serializable;

public class TilemanProfile implements Serializable {

    public static final TilemanProfile NONE = new TilemanProfile(-1, "None");

    @Getter private String accountHash;
    @Getter private String profileName;

    public TilemanProfile(long accountHash, String profileName) {
        this.accountHash = String.valueOf(accountHash);
        this.profileName = profileName;
    }

    @Override
    public String toString() {
        return profileName;
    }

    public String getProfileKey() {
        return getProfileKey(accountHash);
    }

    public static String getProfileKey(String accountHash) {
        return accountHash + "_profile";
    }

    public String getGameRulesKey() {
        return accountHash + "_gamerules";
    }

    public String getRegionKey(int regionId) {
        return getRegionPrefix() + regionId;
    }

    public String getRegionPrefix() {
        return accountHash + "_region_";
    }
}
