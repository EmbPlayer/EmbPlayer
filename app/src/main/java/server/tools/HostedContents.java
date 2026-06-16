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

import org.eclipse.jetty.servlet.ServletContextHandler;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import app.services.BaseServer;
import server.JettyServer;

public class HostedContents {
    private static GetContent audio;
    private static GetContent video;

    public static GetContent getAudioOrBase() {
        return audio;
    }

    public static GetContent getVideo() {
        return video;
    }

    public static void generateProxies(ServletContextHandler handler) {
        audio = new GetContent("/AudioStream", handler);
        video = new GetContent("/VideoStream", handler);
    }

    public static void nullAll() {
        audio.content = null;
        video.content = null;
    }

    public static class GetContent extends HttpServletAdvanced {
        private String content;
        private final String contentPath;

        public GetContent(String contentPath, ServletContextHandler handler) {
            this.contentPath = contentPath;
            handler.addServlet(new JettyServer.CustomServletHolder(this), this.contentPath);
        }

        public String getContentUrl() {
            return "http://" + BaseServer.getLocalhost() + ":" + BaseServer.getPort() + contentPath;
        }

        public void updateContent(String content) {
            this.content = content;
        }

        @Override
        protected void doGetAdvanced(HttpServletRequest request, HttpServletResponse response) throws IOException {
            if (content == null)
                notFound(response);
            else
                found(response);
        }

        private void notFound(HttpServletResponse response) throws IOException {
            // Handle 404 Not Found
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().println("<h1>404 Not Found</h1>");
        }

        private void found(HttpServletResponse response) throws IOException {
            response.sendRedirect(content);
        }
    }
}