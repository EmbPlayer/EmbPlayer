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

package server.web;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import app.tools.StaticFunctions;
import server.tools.HttpServletAdvanced;

import static app.tools.DisposableTools.waitMS;

public class Wait extends HttpServletAdvanced {

    private static boolean isWaiting;

    public static void webUIWaitStart()
    {
        isWaiting = true;
    }

    public static void webUIWaitStop()
    {
        isWaiting = false;
    }

    @Override
    protected void doGetAdvanced(HttpServletRequest request, HttpServletResponse response){
        for (int i = 0; i<350; i++)
        {
            waitMS(250);
            if(!isWaiting)
                break;
        }

        response.setStatus(HttpServletResponse.SC_OK);
    }
}
