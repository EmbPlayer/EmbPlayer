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

import com.google.api.client.util.IOUtils;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import static app.Main.getContext;
import static app.tools.Connection.isInternetAvailable;

import java.net.MalformedURLException;
import java.net.URL;

import server.JettyServer;


public class File
{
    private final ServletHolder file;
    private final String location;

    public File(int page_ID, String location, boolean dynamic)
    {
        if(dynamic)
            this.file = new JettyServer.CustomServletHolder(new DynamicContent(page_ID));
        else
            this.file = new JettyServer.CustomServletHolder(new StaticContent(page_ID));

        this.location = location;
    }

    public File(int page_ID_offline,String url, String location) {
        JettyServer.CustomServletHolder file;
        try {
            file = new JettyServer.CustomServletHolder(new StaticContentUrl(url,page_ID_offline));
        }
        catch (MalformedURLException e)
        {
            file = null;
        }

        this.file = file;
        this.location = location;
    }

    public static void setup(File[] pages, ServletContextHandler handler)
    {
        for(int i = 0; i<pages.length;i++)
        {
            handler.addServlet(pages[i].file,pages[i].location);
        }
    }

    private static class StaticContentUrl extends StaticContent {
        private URL url;

        public StaticContentUrl(String currentURL,int offline_R_ID) throws MalformedURLException {
            super(offline_R_ID);
            url = new URL(currentURL);
        }

        @Override
        protected void doGetAdvanced(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            if(isInternetAvailable(url))
                setupURL(url, resp);
            else
                super.setup(super.id,resp);
        }

        private void setupURL(URL currentURL, HttpServletResponse resp) throws IOException {
            IOUtils.copy(currentURL.openStream(), resp.getOutputStream());
        }
    }
    private static class StaticContent extends HttpServletAdvanced {
        private int id;

        public StaticContent(int id_R) {
            id = id_R;
        }

        @Override
        protected void doGetAdvanced(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            setup(id, resp);
        }

        private void setup(int id_R, HttpServletResponse resp) throws IOException {
            IOUtils.copy(getContext().getResources().openRawResource(id_R), resp.getOutputStream());
        }
    }
    private static class DynamicContent extends StaticContent {
        public DynamicContent(int id_R) {
            super(id_R);
        }

        @Override
        protected void doGetAdvanced(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.addHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            resp.addHeader("Pragma", "no-cache");
            super.doGet(req,resp);
        }
    }
}

