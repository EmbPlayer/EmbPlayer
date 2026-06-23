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

package app.tools.Generators.Requirements;

import androidx.annotation.NonNull;
import app.tools.StaticFunctions;

public enum MediaSourceProviders {

    YOUTUBE,
    VIDEO_URL,
    LIVE_VIDEO_URL,
    AUDIO_URL,
    LIVE_AUDIO_URL,
    EXTRACTOR_AUDIO,
    EXTRACTOR;

    public int id(){
        return this.ordinal();
    }

    public enum ClientSide {

        YOUTUBE("Youtube"),
        VIDEO_URL("Video URL"),
        LIVE_VIDEO_URL("Live Video URL"),
        AUDIO_URL("Audio URL"),
        LIVE_AUDIO_URL("Live Audio URL");

        private final String output;

        ClientSide(String output) {
            this.output = output;
        }

        public int id(){
            return this.ordinal();
        }

        @NonNull
        @Override
        public String toString() {
            return output;
        }

        public static String[] getAllMediaSourceTypeName() {
            MediaSourceProviders.ClientSide[] mediaSourceTypes = MediaSourceProviders.ClientSide.values();
            String[] mediaSourceTypesNames = new String[mediaSourceTypes.length];

            for (int i = 0;  i < mediaSourceTypesNames.length;  i++) {
                mediaSourceTypesNames[i] = StaticFunctions.asJsonFormat(mediaSourceTypes[i].toString());
            }

            return mediaSourceTypesNames;
        }
    }
}
