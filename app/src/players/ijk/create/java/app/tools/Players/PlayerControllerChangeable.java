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

package app.tools.Players;

import app.tools.Players.all.Listeners;
import app.tools.Players.all.Players;

import app.tools.Players.all.Ijk.IjkPlayerControllerBase;
import app.tools.Players.all.Ijk.IjkPlayers;

public abstract class PlayerControllerChangeable extends IjkPlayerControllerBase {
    protected final static Players controllerBase = new IjkPlayers();
    
    public PlayerControllerChangeable(Listeners listeners){
        super(listeners);
    }

    public static boolean isNotHaveErrorFromPlayers(Throwable throwable)
    {
        return false;
    }  
}