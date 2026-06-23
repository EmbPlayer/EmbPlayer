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

package app.tools.Players.all;

import android.view.SurfaceHolder;

import java.io.IOException;

import androidx.annotation.CallSuper;
import app.App.AppBack;
import app.BasePanel;
import server.web.ErrorCodeApp;

public abstract class Video<T extends Player> extends Audio<T> implements IVideoPlayer {

    //private final DisplayCorrectly displayCorrectly = new DisplayCorrectly();
    public boolean hardwareDecoding;

    public Video(Listeners listeners) {
        super(listeners);
    }

    @CallSuper
    public void setDisplay(SurfaceHolder holder) {
        if(AppBack.Panel.check(BasePanel.PanelInfo.notDisplaying))
            return;
        data.setDisplaySurface(holder);
    }

    @CallSuper
    @Override
    public void loadVolume()
    {
        data.emptyPanelClose();
        super.loadVolume();
    }

    @CallSuper
    @Override
    public void modifyRelease()
    {
        data.emptyPanelClose();
        super.modifyRelease();
    }

    @CallSuper
    @Override
    public void beforeOnErrorStarted()
    {
        data.emptyPanelOpen();
    }

    @CallSuper
    public void nullDisplay() {

        data.nullDisplay();

            /*displayCorrectly.RunInAction(()->{
                ErrorCodeApp.code19="BeforeL";
                data.NullDisplay();
                ErrorCodeApp.code19="BeforeN";
                baseData.seekSecond = data.GetCurrentPosition();

                ErrorCodeApp.code19 = "NullDisplay";
            },()->{
                NewAndIsPlaying();
                AfterLoadStart(IsPlaying());
            });*/
    }

    @Override
    public void refreshDisplay(SurfaceHolder holder) {

        setDisplay(holder);
/*
            if(BaseData().seeking||!IsPlaying())
                return;

            Start();*/

            /*displayCorrectly.RunInAction(()->{
                ErrorCodeApp.code14="Rlld-1";
                baseData.seekSecond = (int)data.GetCurrentPosition();

                ErrorCodeApp.code14="Rlld-1";
                NewAndIsPlaying();

                ErrorCodeApp.code14="Rlld0";
                SetDisplay(holder);

                ErrorCodeApp.code14="Rlld";

                // = "A105";
            },()->{
                AfterLoadStart(IsPlaying());
            });*/
    }

    @Override
    public void inLoadTriggeredLoad() throws IOException {
        preset();
        super.inLoadTriggeredLoad();
    }

    @Override
    public void setScreenOnWhilePlaying(boolean on) {
        data.dontSleep(on);
    }

    @Override
    public void dispose(){
        //displayCorrectly.MakerClean();
        super.dispose();
    }

    @Override
    @CallSuper
    protected void preset()
    {
        //SetScreenOnWhilePlaying(true);
    }
}
