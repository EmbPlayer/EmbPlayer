/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright 2026-present Emre Hyuseinov (plaxir) <plaxirstudio@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.tools.Players.all.Ijk;

import app.tools.StaticFunctions;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

public enum ColorFormats {

	Default(0),
	RV16(IjkMediaPlayer.SDL_FCC_RV16),
	YV12( IjkMediaPlayer.SDL_FCC_YV12),
	RV32( IjkMediaPlayer.SDL_FCC_RV32);

	private final int ijkplayerColorFormatId;


	ColorFormats(int ijkplayerColorFormatId) {
		this.ijkplayerColorFormatId = ijkplayerColorFormatId;
	}

	@Override
	public String toString() {
		return name();
	}

	public static int IjkPlayerColorFormatID(int index)
	{
		return ColorFormats.values()[index].ijkplayerColorFormatId;
	}


	public static String[] getAllColorFormatsName() {
		ColorFormats[] colorFormats = ColorFormats.values();
		String[] colorFormatsNames = new String[colorFormats.length];

		for (int i = 0;  i < colorFormatsNames.length;  i++) {
			colorFormatsNames[i] = StaticFunctions.asJsonFormat(colorFormats[i].toString());
		}

		return colorFormatsNames;
	}
}
