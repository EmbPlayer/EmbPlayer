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

package app.tools.Players.all.oem;

import android.media.MediaPlayer;
import android.view.SurfaceHolder;

import androidx.annotation.CallSuper;
import app.tools.Players.all.Listeners;
import app.tools.Players.all.PlayerControllerBase;
import app.tools.SData;

public abstract class OemPlayerControllerBase extends PlayerControllerBase {
    public OemPlayerControllerBase(Listeners listeners) {
        super(listeners);
    }
    public class OemInner extends Oem
    {
        public OemInner(boolean videoOnly) {
            super(videoOnly);
        }

        @CallSuper
        public boolean listenersUpdate()
        {
            /*if(!super.ListenersUpdate())
                return false;*/
            //SData.SetString(SData.savedDisposableErrors,SData.GetString(SData.savedDisposableErrors,"")+"["+output+"] ");
            media.setOnErrorListener((mp, what, extra) -> {
                SData.setString(SData.Data.SavedListenersErrors,SData.getString(SData.Data.SavedListenersErrors,"")+"[what:"+what+", extra:"+extra+", "+getWhatErrorString(what)+"] ");
                if(!secondPlayer)
                    listeners.onErrorListener();
                else if(OemPlayerControllerBase.this.isPlaying()){
                    long s = OemPlayerControllerBase.this.getCurrentPosition();
                    if(s<media.getDuration()-1500)
                    {
                        s = s-1000;
                        if(s<0)
                            s = 0;
                        long finalS = s;
                        OemPlayerControllerBase.this.waitActionCompleteAndStart(()-> OemPlayerControllerBase.this.start(finalS));
                    }
                }
                error = true;
                return true;
            });
            media.setOnPreparedListener(mp -> onPrepareMaking());
            media.setOnCompletionListener(mp -> onEnded());
            media.setOnBufferingUpdateListener((mp, percent) -> {
                if(!secondPlayer)
                    listeners.onBufferingUpdateListener(percent);
            });
            media.setOnInfoListener((mp, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                    bufferingStarted = true;
                    bufferingCounter++;
                    if(!secondPlayer)
                        listeners.onBufferingStart();
                }
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
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
            media.setOnVideoSizeChangedListener((mediaPlayer, i, i1) -> {
                listeners.onVideoSizeChangedListener(i, i1);
            });
            super.modifySetDisplaySurface(holder);
        }

        @Override
        protected void onPrepareMaking() {
            startOnPrepared();
            super.onPrepareMaking();
            OemPlayerControllerBase.this.onPrepareMaking();
        }

        @Override
        protected boolean onEndTriggered(long curPos) {
            return OemPlayerControllerBase.this.onEndTriggered(curPos);
        }

        protected void onEnded() {
            OemPlayerControllerBase.this.onEnded();
        }
    }
}
