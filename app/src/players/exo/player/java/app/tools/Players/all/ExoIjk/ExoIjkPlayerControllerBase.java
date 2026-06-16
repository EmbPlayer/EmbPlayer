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

package app.tools.Players.all.ExoIjk;

import androidx.annotation.CallSuper;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.VideoSize;
import app.tools.Players.all.Listeners;
import app.tools.Players.all.Ijk.IjkPlayerControllerBase;
import app.tools.SData;

public abstract class ExoIjkPlayerControllerBase extends IjkPlayerControllerBase {
    public ExoIjkPlayerControllerBase(Listeners listeners) {
        super(listeners);
    }

    public class ExoInner extends Exo
    {
        public ExoInner(boolean videoOnly) {
            super(videoOnly);
        }

        @CallSuper
        public boolean listenersUpdate()
        {
            if (media == null) return false;

            media.addListener(new androidx.media3.common.Player.Listener() {

                @Override
                public void onPlayerError(PlaybackException erro) {
                    // Map ExoPlayer errors to similar format as IJKPlayer
                    String errorMsg = "ExoPlayer Error: ";
                    if (erro.errorCode != PlaybackException.ERROR_CODE_UNSPECIFIED) {
                        errorMsg += "code=" + erro.errorCode + ", ";
                    }
                    errorMsg += erro.getMessage();

                    SData.setString(SData.Data.SavedListenersErrors,
                            SData.getString(SData.Data.SavedListenersErrors, "") +
                                    "[" + errorMsg + "] ");

                    if(erro.errorCode==2004)
                    {
                        listeners.onNotLoadedTryAgainToLoad();
                        return;
                    }

                    if (!secondPlayer)
                        listeners.onErrorListener();
                    error = true;
                }


                @Override
                public void onVideoSizeChanged(VideoSize videoSize) {
                    if(holder!=null)
                        listeners.onVideoSizeChangedListener(videoSize.width, videoSize.height);
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    switch (playbackState) {

                        case androidx.media3.common.Player.STATE_BUFFERING:
                            // Buffering started
                            if (!bufferingStarted) {
                                bufferingStarted = true;
                                bufferingCounter++;
                                if (!secondPlayer)
                                    listeners.onBufferingStart();
                            }
                            break;

                        case androidx.media3.common.Player.STATE_READY:
                            // Buffering ended
                            if (bufferingStarted) {
                                bufferingStarted = false;
                                if (!secondPlayer)
                                    listeners.onBufferingEnd();
                            }
                            break;

                        case androidx.media3.common.Player.STATE_ENDED:
                            // Playback completed
                            OnEnded();
                            break;
                    }
                }

                @Override
                public void onIsLoadingChanged(boolean isLoading) {
                    // Additional buffering indicator
                    if (isLoading && !bufferingStarted) {
                        bufferingStarted = true;
                        bufferingCounter++;
                        if (!secondPlayer)
                            listeners.onBufferingStart();
                    } else if (!isLoading && bufferingStarted) {
                        bufferingStarted = false;
                        if (!secondPlayer)
                            listeners.onBufferingEnd();
                    }
                }
/*
                @Override
                public void onVideoSizeChanged(VideoSize videoSize) {
                    // Equivalent to OnVideoSizeChangedListener in IJKPlayer
                    listeners.OnVideoSizeChangedListener(videoSize.width, videoSize.height);
                }*/
            });

            return true;
        }

        @Override
        protected void onPrepareMaking() {
            super.onPrepareMaking();
            ExoIjkPlayerControllerBase.this.onPrepareMaking();
        }

        @Override
        protected boolean onEndTriggered(long curPos) {
            return ExoIjkPlayerControllerBase.this.onEndTriggered(curPos);
        }

        protected void OnEnded() {
            ExoIjkPlayerControllerBase.this.onEnded();
        }
    }
}
