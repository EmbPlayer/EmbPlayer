package server.web;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import app.tools.SData;
import server.tools.HttpServletAdvanced;

public class LogsDisplay extends HttpServletAdvanced {

    @Override
    protected void doGetAdvanced(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String k = "disposableErrors: "+ SData.getStringWithArchive(SData.Data.SavedDisposableErrors)+System.lineSeparator()+
                "mediaPlayerErrors: "+ SData.getStringWithArchive(SData.Data.SavedListenersErrors)+System.lineSeparator();
        resp.getWriter().write(k);
    }
}
