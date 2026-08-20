package server.web;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import androidx.annotation.NonNull;
import app.App.AppBack;
import app.services.BaseServer;
import app.tools.HlsSelector;
import app.tools.Players.all.PlayersCollection;
import app.tools.SData;
import server.tools.HttpServletAdvanced;

public class HLSLiveData extends HttpServletAdvanced {
    private static String linkHLS = "";
    private static String directory;

    public static void updateData(String data){
        linkHLS = data;
    }

    public static void updateDirectory(String directory_){
        directory = directory_;
    }

    public static String getLink(){
        if(directory == null)
            directory = "/";
        return "http://"+BaseServer.getIP()+":"+BaseServer.getPort()+directory;
    }

    public static String updateAndGetLink(String url,int resolution){
        try{
            if(checkIsCorrectPlayer()){
                HLSLiveData.updateData(HlsSelector.getCorrectUrl(url,resolution));
                return HLSLiveData.getLink();
            }
        }
        catch (Exception e){}

        return url;
    }

    public static String updateAndGetLink(String url,String resolution){
        try{
            if(checkIsCorrectPlayer()){
                HLSLiveData.updateData(HlsSelector.getCorrectUrl(url,resolution));
                return HLSLiveData.getLink();
            }
        }
        catch (Exception e){}

        return url;
    }

    @Override
    protected void doGetAdvanced(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if(linkHLS==null)
            resp.getWriter().write("null");
        else
            resp.getWriter().write(linkHLS);
    }

    private static boolean checkIsCorrectPlayer(){
        PlayersCollection curLive = AppBack.getApp().getLivePlayer();
        return curLive == PlayersCollection.IJK || curLive == PlayersCollection.OEM;
    }
}
