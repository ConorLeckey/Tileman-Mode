package com.tileman;

public class MovementFlagUtil {

    public static final int NO_FLAGS = 0;

    public static boolean containsAnyOf(int comparisonFlags, int flagsToCompare){
        return (comparisonFlags & flagsToCompare) > 0;
    }
}
