/*
 * Copyright (c) 2018, TheLonelyDev <https://github.com/TheLonelyDev>
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * Copyright (c) 2020, ConorLeckey <https://github.com/ConorLeckey>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.tileman;

import net.runelite.client.config.*;

@ConfigGroup("tilemanMode")
public interface TilemanModeConfig extends Config
{
	@ConfigSection(
		name = "Custom Game Mode",
		description = "Create a custom Tileman game mode. Be sure to 'Enable Custom Game Mode'",
		position = 99
	)
	String customGameModeSection = "customGameMode";

	public enum TilemanGameMode {
		COMMUNITY,
		STRICT,
		ACCELERATED
	}
	@Alpha
	@ConfigItem(
		keyName = "gameMode",
		name = "Game Mode",
		description = "Select your Tileman game mode",
		position = 1
	)
	default TilemanGameMode gameMode()
	{
		return TilemanGameMode.COMMUNITY;
	}

	@Alpha
	@ConfigItem(
		keyName = "automarkTiles",
		name = "Auto-mark tiles",
		description = "Automatically mark tiles as you walk.",
		position = 2
	)
	default boolean automarkTiles()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		keyName = "warningLimit",
		name = "Unspent tiles warning",
		description = "Highlights overlay when limit reached",
		position = 3
	)
	default int warningLimit()
	{
		return 50;
	}

	@Alpha
	@ConfigItem(
		keyName = "drawOnMinimap",
		name = "Draw tiles on minimap",
		description = "Configures whether marked tiles should be drawn on minimap",
		position = 4
	)
	default boolean drawTileOnMinimmap()
	{
		return false;
	}

	/***   Custom Game Mode section   ***/
	@Alpha
	@ConfigItem(
		keyName = "enableCustomGameMode",
		name = "Enable Custom Game Mode",
		description = "Settings below will override Game Mode defaults",
		section = customGameModeSection,
		position = 1
	)
	default boolean enableCustomGameMode()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		keyName = "tilesOffset",
		name = "Bonus tiles",
		description = "Add more tiles to your limit, set to 0 for off",
		section = customGameModeSection,
		position = 2
	)
	default int tilesOffset()
	{
		return 9;
	}

	@Alpha
	@ConfigItem(
		keyName = "includeTotalLevels",
		name = "Include total level",
		description = "Includes total level in usable tiles",
		section = customGameModeSection,
		position = 3
	)
	default boolean includeTotalLevel()
	{
		return false;
	}
}
