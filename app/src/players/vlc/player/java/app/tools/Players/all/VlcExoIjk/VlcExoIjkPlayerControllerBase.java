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

package app.tools.Players.all.VlcExoIjk;

import org.videolan.libvlc.MediaPlayer;

import app.tools.Players.all.Listeners;
import app.tools.Players.all.ExoIjk.ExoIjkPlayerControllerBase;

import server.web.ErrorCodeApp;

public abstract class VlcExoIjkPlayerControllerBase extends ExoIjkPlayerControllerBase {
    public VlcExoIjkPlayerControllerBase(Listeners listeners) {
        super(listeners);
    }

    public class VLCInner extends Vlc
    {
        public VLCInner(boolean videoOnly) {
            super(videoOnly);
        }

        @Override
        public boolean listenersUpdate()
        {/*
            if(!super.ListenersUpdate())
                return false;*/

            media.setEventListener(new MediaPlayer.EventListener() {
                @Override
                public void onEvent(MediaPlayer.Event event) {
                    switch (event.type) {
                        case MediaPlayer.Event.EndReached:
                            break;

                        case MediaPlayer.Event.Opening:
                            break;

                        case MediaPlayer.Event.MediaChanged:
                            onPrepareMaking();
                            break;

                        case MediaPlayer.Event.EncounteredError:
                            if(!secondPlayer)
                                listeners.onErrorListener();
                            error = true;
                            break;

                        case MediaPlayer.Event.Buffering:
                            bufferingCounter+=1;
                            float buffering = event.getBuffering();
                            if(!secondPlayer)
                                listeners.onBufferingUpdateListener((int)(buffering * 100));
                            break;

                        case MediaPlayer.Event.Playing:
                            break;

                        case MediaPlayer.Event.Paused:
                            break;

                        case MediaPlayer.Event.Stopped:
                            break;

                        case MediaPlayer.Event.TimeChanged:

                            onEndTriggered(getCurrentPosition());
                            break;

                        case MediaPlayer.Event.PositionChanged:
                            break;

                        case MediaPlayer.Event.SeekableChanged:
                            break;

                        case MediaPlayer.Event.PausableChanged:
                            break;

                        case MediaPlayer.Event.LengthChanged:
                            break;

                        case MediaPlayer.Event.Vout:
                            if(event.getVoutCount() > 0)
                            {
                                handleVoutReady();
                            }
                            break;

                        case MediaPlayer.Event.ESAdded:
                            break;

                        case MediaPlayer.Event.ESDeleted:
                            break;

                        case MediaPlayer.Event.ESSelected:
                            break;

                        case MediaPlayer.Event.RecordChanged:
                            break;
                    }
                }
            });
            return true;
        }

        @Override
        public long getDuration()
        {
            if(isNull())
                return 0;

            long max = media.getLength();
            if(max>500)
                return max;
            else
                return baseData().getMaxSeek();
        }

        @Override
        protected void onPrepareMaking() {
            super.onPrepareMaking();
            VlcExoIjkPlayerControllerBase.this.onPrepareMaking();
        }

        @Override
        protected boolean onEndTriggered(long curPos)
        {
            long duration= this.getDuration()-10000;

            if(duration>curPos)
                return false;

            end(()->{
                VlcExoIjkPlayerControllerBase.this.waitActionCompleteAndStart(()-> VlcExoIjkPlayerControllerBase.this.pause());

                if(baseData().getLoop()){
                    VlcExoIjkPlayerControllerBase.this.waitActionCompleteAndStart(()-> VlcExoIjkPlayerControllerBase.this.start(0));
                    return false;
                }

                if(baseData().getPlayListLoop()){
                    mediaStart();
                    listeners.onPlayListLoop();
                    return false;
                }

                listeners.onCompletionListener();
                return true;
            });

            return true;
        }
    }
}
