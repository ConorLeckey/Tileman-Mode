# Tileman Mode
Tileman Mode is based on a YouTube series:
check out the playlist <a href="https://www.youtube.com/playlist?list=PLLNTajexsGYaw5pcyLOMyrW6w8_IMDG90">here</a>.

The game mode is defined by the player being limited to standing on certain tiles. The amount of tiles you are allowed to stand on depends on your total experience. Each 1000 exp represents one tile you can 'spend'. 

This plugin sets out to make the marking of tiles and the counting of both your used and remaining tiles automated.

## Features
- Automatically claim tiles as you walk/run using the 'Auto-mark' feature.
- Can render claimed tiles on the minimap, world map and main game as an overlay.
- Ability for the tiles/overlay change colour to notify you when you've gone over your set tile limit or when you use up all your tiles.
- Choose from a selection of game modes, each with different rules and difficulties.
- Option to add additional tiles to your total usable tiles.
- Option to include your total level in the total usable tiles, alongside your exp tiles.
- Natively supports import/export of tiles for Group Tileman gameplay (no additional plugins required).

## Known Issues
Moving between map chunks will sometimes cause tiles to be missed.

## Contact
Feel free to contact me on discord at Conleck#6160 with any bug reports or suggestions :) 

# Changelog

## November 2025 Minor Update

**Expanded overlay customization options**
> - A large number of new customization options are available for rendering tile overlays.

**Added wayfinder functionality**
> - Enabling the wayfinder in settings will overlay the path your character will walk to the tile you are hovering in realtime.
> - The unlock cost to walk to the tile can also be displayed as a text overlay on unclaimed tiles.

## October 2025 Major Update

**Significantly improved plugin performance (approx 40x faster)**
> - The plugin has been migrated to operate on an updated config file format.
> - Users should be automatically migrated to the new format without requiring any user action.
> - Users at high tile counts should now be able to enable auto tile without performance issues.

**Added native Group Tileman support**
> - Historically players have had to use an additional plugin to manage group play.
    With the migration to the new data format for performance fixes, this is no longer compatible.
    To address this issue this functionality has gone core and was integrated into the main plugin.
> - The new implementation is fully compatible with previously exported data, simply import and go.
> - Accessible via the new "Group Tileman Data" navigation menu on the RuneLite sidebar.
> - Includes tools for cleaning remnant data from the deprecated "Group tileman addon" plugin.
> - The plugin will generate three warnings in chat about this change to ensure visibility to players.
> - Auto-mark functionality now respects imported tile sets, preventing claim on group tiles.

**Ground marker import functionality removed**
> - This legacy migration feature from ground marker plugin was removed as no longer required.