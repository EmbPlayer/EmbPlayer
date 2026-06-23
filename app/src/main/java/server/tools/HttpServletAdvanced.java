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

package server.tools;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import app.tools.Recyclable;
import app.tools.StaticFunctions;

public abstract class HttpServletAdvanced extends HttpServlet {

    protected static final Recyclable.ListDisposable actions = new Recyclable.ListDisposable(HttpServletAdvanced.class);

    @Override
    protected final void doGet(HttpServletRequest req, HttpServletResponse resp) {
        resp.addHeader("Access-Control-Allow-Origin", "*");
        resp.addHeader("Access-Control-Allow-Methods", "GET, POST");/*, PUT, DELETE, OPTIONS*/
        resp.setCharacterEncoding("UTF-8");
        //resp.setContentType("application/json; charset=UTF-8");

        try {
            doGetAdvanced(req, resp);
        } catch (IOException | ServletException e) {
            StaticFunctions.onThrows(e);
        }
    }

    @Override
    protected final void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doPostAdvanced(req,resp);
        } catch (IOException | ServletException e) {
            StaticFunctions.onThrows(e);
        }
    }

    protected void doPostAdvanced(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        super.doPost(request,response);
    }

    protected void doGetAdvanced(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        super.doGet(request,response);
    }
}
