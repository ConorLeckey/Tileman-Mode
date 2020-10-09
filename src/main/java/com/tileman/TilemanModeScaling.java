package com.tileman;

public class TilemanModeScaling
{
    private static int tilesUnlocked;
    private static int tileCost;
    private static int xpToNextTile;

    public static void processScaling( int xp )
    {
        long scaleStartTile =         //retrieve from config
        long scaleEveryTiles =        //retrieve from config
        long costIncreasePerScale =   //retrieve from config
        long baseTileCost =           //retrieve from config
        long maxTileCost =            //retrieve from config

        long scale = 0;
        long tiles = 0;
        long cost = Math.min( baseTileCost, maxTileCost );
        long nextScaleXp, remainder;

        if( xp >= scaleStartTile * baseTileCost )
        {
            scale = 1;

            tiles += scaleStartTile;
            xp -= scaleStartTile * baseTileCost;

            cost = Math.min( baseTileCost + costIncreasePerScale * scale, maxTileCost );
            nextScaleXp = scaleEveryTiles * cost;
            while( xp >= nextScaleXp )
            {
                tiles += scaleEveryTiles;
                xp -= nextScaleXp;

                cost = Math.min( baseTileCost + costIncreasePerScale * scale, maxTileCost );
                nextScaleXp = scaleEveryTiles * cost;

                scale++;
            }

            if( xp > cost )
            {
                tiles += xp / cost;
                xp = xp % cost;
            }
        }
        else
        {
            tiles += xp / baseTileCost;
            xp = xp % baseTileCost;
        }

        remainder = cost - xp;

        //cost == cost of next tile
        //remainder == xp to next Tile
        //tiles == available tiles at this xp
    }

    public static int getTilesUnlocked()
    {
        return tilesUnlocked;
    }

    public static int getTileCost()
    {
        return tileCost;
    }

    public static int getXpToNextTile()
    {
        return xpToNextTile;
    }
}
