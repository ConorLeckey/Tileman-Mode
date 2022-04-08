package com.tileman;

import lombok.Getter;

import java.io.Serializable;

public class TilemanProfile implements Serializable {

    public static final TilemanProfile NONE = new TilemanProfile(-1, "None");

    @Getter private long accountHash;
    @Getter private String profileName;

    public TilemanProfile(long accountHash, String profileName) {
        this.accountHash = accountHash;
        this.profileName = profileName;
    }

    public String getGuid() {
        return String.valueOf(accountHash);
    }

    @Override
    public String toString() {
        return profileName;
    }
}
