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

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import app.tools.LinksDbHelper;
import app.tools.StaticFunctions;
import server.tools.HttpServletAdvanced;

public class Tables extends HttpServletAdvanced {

    @Override
    protected void doGetAdvanced(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.getWriter().write(StaticFunctions.setData(StaticFunctions.itemAsJsonNormal(LinksDbHelper.getTableNames())));
    }

}
