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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class GetLinks  extends HttpServletAdvanced {

    protected String get()
    {
        //String newline = System.getProperty("line.separator");

        String outPut = "";
        //String outPut = ""+App().ErrorHandel.mediaSession.getSecond();

        /*
        String outPut = "videoStreams :"+newline+newline;
        for(int i = 0; i<App().videoStreamList.size(); i++)
        {
            outPut = outPut+Uri.parse(App().videoStreamList.get(i).getContent()).toString()+newline+newline+newline;
        }

        outPut = outPut+"videoOnlyStreams :"+newline+newline;
        for(int i = 0; i<App().videoOnlyStreamList.size(); i++)
        {
            outPut = outPut+ Uri.parse(App().videoOnlyStreamList.get(i).getContent()).toString()+newline+newline+newline;
        }

        outPut = outPut+"audioStreams :"+newline+newline;
        for(int i = 0; i<App().audioStreams.size(); i++)
        {
            outPut = outPut+Uri.parse(App().audioStreams.get(i).getContent()).toString()+newline+newline+newline;
        }*/

        //outPut = outPut+newline+newline+App().language;
/*
        for(int i = 0; i<App().logs.size(); i++)
        {
            outPut = outPut+App().logs.get(i)+newline+newline+newline;
        }

        outPut = outPut + newline+newline+App().hls;*/

        return outPut;

        /*try {
            return App().hls;
        }
        catch (Exception l)
        {
            return null;
        }*/
    }


    @Override
    protected void doGetAdvanced(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.getWriter().write(get());
    }
}
