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

package app.tools.Players.all.Ijk;

import android.view.SurfaceHolder;

import androidx.annotation.CallSuper;
import app.EmptyActivity;
import app.tools.Players.all.Listeners;
import app.tools.Players.all.oem.OemPlayerControllerBase;
import app.tools.SData;
import app.tools.StaticFunctions;

import static app.tools.DisposableTools.waitMS;
import static tv.danmaku.ijk.media.player.IMediaPlayer.MEDIA_INFO_BUFFERING_END;
import static tv.danmaku.ijk.media.player.IMediaPlayer.MEDIA_INFO_BUFFERING_START;

public abstract class IjkPlayerControllerBase extends OemPlayerControllerBase {
    public IjkPlayerControllerBase(Listeners listeners) {
        super(listeners);
    }
    public class IJKInner extends Ijk
    {
        private boolean isDisplaying;
        private final PanelFixer panelFix = new PanelFixer();

        public IJKInner(boolean videoOnly, int colorFormatIndex) {
            super(videoOnly, colorFormatIndex);
        }

        @Override
        public void modifyNullDisplay() {
            super.modifyNullDisplay();
            isDisplaying = false;
        }

        @Override
        public final void start() {
            super.start();
            panelFix.run();
        }

        @Override
        protected final void afterCheckingStart(long seek){
            super.afterCheckingStart(seek);
            panelFix.run();
        }

        @CallSuper
        public boolean listenersUpdate()
        {
            /*if(!super.ListenersUpdate())
                return false;*/
            //SData.SetString(SData.savedDisposableErrors,SData.GetString(SData.savedDisposableErrors,"")+"["+output+"] ");
            media.setOnErrorListener((mp, what, extra) -> {
                SData.setString(SData.Data.SavedListenersErrors,SData.getString(SData.Data.SavedListenersErrors,"")+"[what:"+what+", extra:"+extra+", "+getWhatErrorString(what)+"] ");
                if(!secondPlayer){
                    panelFix.reset();
                    listeners.onErrorListener();
                }
                else if(IjkPlayerControllerBase.this.isPlaying()){
                    long s = IjkPlayerControllerBase.this.getCurrentPosition();
                    if(s<media.getDuration()-1500)
                    {
                        s = s-1000;
                        if(s<0)
                            s = 0;
                        long finalS = s;
                        IjkPlayerControllerBase.this.waitActionCompleteAndStart(()-> IjkPlayerControllerBase.this.start(finalS));
                    }
                }
                error = true;
                return true;
            });
            media.setOnPreparedListener(mp -> onPrepareMaking());
            media.setOnCompletionListener(mp -> OnEnded());
            media.setOnBufferingUpdateListener((mp, percent) -> {
                if(!secondPlayer)
                    listeners.onBufferingUpdateListener(percent);
            });
            media.setOnInfoListener((mp, what, extra) -> {
                if (what == MEDIA_INFO_BUFFERING_START) {
                    bufferingStarted = true;
                    bufferingCounter++;
                    if(!secondPlayer)
                        listeners.onBufferingStart();
                }
                if (what == MEDIA_INFO_BUFFERING_END) {
                    if(!secondPlayer)
                        listeners.onBufferingEnd();
                    bufferingStarted = false;
                }
                return true;
            });/*

            media.setOnNativeInvokeListener(new IjkMediaPlayer.OnNativeInvokeListener() {
                @Override
                public boolean onNativeInvoke(int what, Bundle args) {
                    // Optional: log or intercept messages
                    //App().logs.add(what+"");
                    return false;
                }
            });*/
            return true;
        }

        @Override
        public void modifySetDisplaySurface(SurfaceHolder holder)
        {
            isDisplaying = true;
            media.setOnVideoSizeChangedListener((mp, width, height, sarNum, sarDen) -> {
                listeners.onVideoSizeChangedListener(width,height);
            });
            super.modifySetDisplaySurface(holder);
        }

        @Override
        protected void onPrepareMaking() {
            super.onPrepareMaking();
            IjkPlayerControllerBase.this.onPrepareMaking();
        }

        @Override
        protected boolean onEndTriggered(long curPos) {
            return IjkPlayerControllerBase.this.onEndTriggered(curPos);
        }

        protected void OnEnded() {
            IjkPlayerControllerBase.this.onEnded();
        }

        public class PanelFixer extends StaticFunctions.StarterEmpty
        {
            @Override
            protected void firstLaunch() {
                if(isDisplaying)
                    EmptyActivity.EmptyIJK.load();
            }

            @Override
            protected void secondLaunches() {}
        }
    }
}
