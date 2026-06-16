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

package server;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.ErrorHandler;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import com.emb.player.R;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;

import app.tools.Generators.Requirements.Piped.VideoResolution;
import io.reactivex.rxjava3.disposables.Disposable;
import okhttp3.OkHttpClient;
import server.tools.File;
import ssl.MediaProxyClientFactory;
import server.tools.MediaProxyServlet;
import server.web.ErrorCodeApp;

//import server.tools.HostedContents;

import app.tools.StaticFunctions;
import server.web.Sources;

import static app.services.BaseServer.restart;
import static app.tools.DisposableTools.addTask;
import static app.tools.DisposableTools.forkJoinPool;
import static app.tools.DisposableTools.waitMS;
import static server.Home.app;

public class JettyServer {

    private Server server;

    public JettyServer(int port) throws Exception{
        create(port);
    }

    private void create(int port) throws Exception{
        server = new Server(port);

        server.setHandler(getHandler());

        StaticFunctions.LoadClass.Load();

        server.start();
    }

    public void recreateServer(int port) throws Exception{
        server.stop();
        server.destroy();
        create(port);
    }

    private ServletContextHandler getHandler() throws Exception {
        ServletContextHandler handler = new ServletContextHandler();
        handler.setErrorHandler(new CustomErrorHandler());

        Home hostedSite = new Home();

        File.setup(new File[]
                {
                        new File(R.raw.bootstrap_css,"/Bootstrap.css",false),
                        new File(R.raw.bootstrap_js,"/Bootstrap.js",false),
                        new File(R.raw.index,"/index.js",false),
                        new File(R.raw.indexpagev2,"/",false)
                        //new File(R.raw.bootstrap_css,"http://192.168.1.150:8081/indexpagev2.html","/"),
                        //new File(R.raw.bootstrap_css,"http://192.168.1.150:8081/index.js","/index.js")
                        //new File(R.raw.indexpage,"https://emplayer.github.io/","/")
                },handler);

        handler.addServlet(new CustomServletHolder(hostedSite.appControl), "/Data");
        handler.addServlet(new CustomServletHolder(hostedSite.waiter), "/Ok");
        handler.addServlet(new CustomServletHolder(hostedSite.quality), "/Quality");
        handler.addServlet(new CustomServletHolder(hostedSite.links), "/Links");
        //handler.addServlet(new CustomServletHolder(hostedSite.devices), "/Devices");
        handler.addServlet(new CustomServletHolder(hostedSite.tables),"/Tables");
        handler.addServlet(new CustomServletHolder(ErrorCodeApp.class),"/Error");

        //after here is not static is requared update

        hostedSite.sources = new Sources(handler,"Sources", Sources.getAsResolution(VideoResolution.values()[app().getVideoResolutionLiveID()]));
        hostedSite.urls.addToHandler(handler);
        hostedSite.youtubeLinks.addToHandler(handler);
        hostedSite.youtubePlaylistLinks.addToHandler(handler);
        hostedSite.youtubeLiveLinks.addToHandler(handler);

        //--
        MediaProxyClientFactory proxyFactory = new MediaProxyClientFactory();
        OkHttpClient proxyClient = proxyFactory.buildClient();
        String mediaPrivateProxy = "/MediaProxy";
        handler.addServlet(new CustomServletHolder(new MediaProxyServlet(proxyClient,mediaPrivateProxy)), mediaPrivateProxy);
        //--

        //hostedSite.urlsFast.AddToHandler(handler);

        //HostedContents.GenerateProxies(handler);

        return handler;
    }

    public static class CustomServletHolder extends ServletHolder{
        public CustomServletHolder(Servlet servlet){
            super(servlet);
            base();
        }

        public CustomServletHolder() {
            super();
            base();
        }

        public CustomServletHolder(String name, Servlet servlet) {
            super(name,servlet);
            base();
        }

        public CustomServletHolder(String name, Class<? extends Servlet> servlet) {
            super(name,servlet);
            base();
        }

        public CustomServletHolder(Class<? extends Servlet> servlet) {
            super(servlet);
            base();
        }

        private void base(){
            //this.setAsyncSupported(true);
        }
    }

    public static class CustomErrorHandler extends ErrorHandler
    {
        private static Disposable restarter;

        @Override
        public void handle(String target, Request baseRequest,
                           HttpServletRequest request, HttpServletResponse response)
                throws IOException {

            if (((Response)response).getStatus() == HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
                response.setContentType("text/html;charset=utf-8");
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

                PrintWriter out = response.getWriter();

                // Retrieve error details from request attributes (Jetty standard)
                Throwable exception = (Throwable) request.getAttribute("javax.servlet.error.exception");
                String message = (String) request.getAttribute("javax.servlet.error.message");
                if (message == null || message.isBlank()) message = "Server Error";

                String requestURI = (String) request.getAttribute("javax.servlet.error.request_uri");
                if (requestURI == null) requestURI = request.getRequestURI();

                // Jetty original style + restart announcement
                out.println("<!DOCTYPE html>");
                out.println("<html>");
                out.println("<head>");
                out.println("<meta charset=\"utf-8\"/>");
                out.println("<title>Error 500</title>");
                out.println("<style>");
                out.println("body { font-family: sans-serif; margin: 0; padding: 2em; background: #f9f9f9; }");
                out.println("h1 { color: #b00; }");
                out.println("pre { background: #eee; padding: 1em; border-radius: 5px; overflow: auto; }");
                out.println("hr { border: 0; border-top: 1px solid #ccc; margin: 2em 0; }");
                out.println("</style>");
                out.println("</head>");
                out.println("<body>");
                out.println("<h1>HTTP ERROR 500</h1>");
                out.println("<p>Problem accessing <code>" + escapeHtml(requestURI) + "</code>. Reason:</p>");
                out.println("<pre>" + escapeHtml(message) + "</pre>");

                if (exception != null) {
                    out.println("<h3>Stack trace:</h3>");
                    out.println("<pre>");
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    exception.printStackTrace(pw);
                    out.println(escapeHtml(sw.toString()));
                    out.println("</pre>");
                }

                // ADDITIONAL INFO: server restart message
                out.println("<p><strong>The server/application will restart automatically.</strong></p>");
                out.println("<p>Please try again in a few moments.</p>");

                out.println("<hr/>");
                out.println("<p><i>Powered by Jetty://</i></p>");
                out.println("</body>");
                out.println("</html>");
                out.close();

                response.flushBuffer();   // make sure the page is sent

                // Restart the app asynchronously (delay ensures client receives the page)
                if(restarter==null||restarter.isDisposed())
                    restarter = addTask(()->{
                        waitMS(1500);
                        restart();
                        return true;
                    },()->"JettyError",forkJoinPool);

                return;
            }

            // For other error codes, use default Jetty handling
            super.handle(target, baseRequest, request, response);
        }

        private String escapeHtml(String s) {
            if (s == null) return "";
            return s.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;");
        }
    }
}