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

package app.tools.Generators;

import app.tools.Generators.Requirements.Generator;
import app.tools.Players.all.Listeners;
import app.tools.Players.PlayerController;
import app.tools.Players.all.PlayerControllerBase;

public class UrlGenerator extends Generator {

    private final String nameOfMedia;
    private final Listeners listeners;
    private final boolean hardware;

    public UrlGenerator(boolean isLive,String url, Listeners listeners, boolean hardware,String nameOfMedia)
    {
        this.isLive = isLive;
        this.videoURL = url;
        this.listeners = listeners;
        this.hardware = hardware;
        this.nameOfMedia = nameOfMedia;
    }

    @Override
    public PlayerControllerBase startPanel(boolean DisplayOn, boolean loop, boolean plalistLoop) {
        return PlayerController.getPlayer(listeners,loop,plalistLoop,hardware,DisplayOn, isLive(),videoURL,null);
    }

    @Override
    public String nameOfMedia() {
        return nameOfMedia;
    }
}
