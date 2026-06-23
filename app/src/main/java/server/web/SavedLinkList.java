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

import org.eclipse.jetty.servlet.ServletContextHandler;

import java.io.IOException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import app.tools.StaticFunctions;
import server.JettyServer;
import server.tools.HttpServletAdvanced;

import static app.tools.StaticFunctions.ItemWithId;

import static app.tools.StaticFunctions.asJsonFormat;
import static server.Home.app;

public class SavedLinkList  extends HttpServletAdvanced {

    private List<ItemWithId> links;
    private String tableName;

    public SavedLinkList(String tableName)
    {
        this.tableName = tableName;
    }

    public void addToHandler(ServletContextHandler handler)
    {
        handler.addServlet(new JettyServer.CustomServletHolder(this),"/"+tableName);
    }

    @Override
    protected void doGetAdvanced(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        loadList();
        //StaticFunctions.setData(StaticFunctions.ItemWithIdAsJson(links));

        if(links == null|| links.isEmpty())
            resp.getWriter().write("[]");
        else
            resp.getWriter().write(StaticFunctions.setData(StaticFunctions.itemWithIdAsJson(links)));
    }

    private void loadList()
    {
        links = app().getLinks(tableName);
        //links = aa[index];
    }
}
