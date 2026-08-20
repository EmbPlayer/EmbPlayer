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

package battery;

import android.content.Context;
import android.view.View;
import android.app.AlertDialog;
import app.tools.activity.DefaultActivity;
import app.tools.StaticFunctions.StarterEmpty;

public class BatterySettings{

    private final static StarterEmpty change = new StarterEmpty() {
        @Override
        protected void firstLaunch() {
            app.PermissionsFunctionality.batteryOptimizationNotSupportingText();
        }

        @Override
        protected void secondLaunches() {

        }
    };

    private final static View.OnClickListener click = view -> change.run();

    public static View.OnClickListener onClickBatterySettingsButton(){
        change.reset();
        return click;
    }
}

