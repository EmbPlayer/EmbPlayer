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

package app.tools;

import app.tools.activity.DefaultActivity;

public class LoadedData {
    public static final LData<String> oldBSSID = new LData<>();
    public static final LData<String> oldIP = new LData<>();
    public static final LData<Boolean> isSaved = new LData<>();

    public static void onLoadData(){
        oldBSSID.update(SData.getString(SData.Data.BSSID));
        oldIP.update(SData.getString(SData.Data.IP));
        isSaved.update(SData.get(SData.Data.UndefiledError));

        DefaultActivity.brightness = SData.getFloat(SData.Data.BrightnessLevel,0.5f);
    }

    public static class LData<T> {
        private T data;

        private void update(T data){
            this.data = data;
        }

        public T get(){
            return data;
        }
    }
}
