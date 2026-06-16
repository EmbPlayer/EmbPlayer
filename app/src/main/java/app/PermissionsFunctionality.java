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

package app;

import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import com.emb.player.R;

import androidx.appcompat.app.AppCompatActivity;
import app.tools.OverlayPermissionHelper;
import app.tools.SData;

public class PermissionsFunctionality {

    Switch autoStart;
    Button close;
    EditText portInput;
    Button portSet;
    TextView portInfo;
    boolean autoStartOn;
    int port;

    private Button batterySettings;
    private boolean notSupportOverAppAsk;
    private boolean isTv;
    private boolean supportBatteryOptions;

    public static boolean isTV(Context context) {
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    void baseFunction(AppCompatActivity activity)
    {
        autoStartOn = SData.get(SData.Data.AutoStart);

        portInput = activity.findViewById(R.id.portInput);
        portSet = activity.findViewById(R.id.portSet);
        portInfo = activity.findViewById(R.id.portInfo);

        autoStart = activity.findViewById(R.id.autoStart);
        batterySettings = activity.findViewById(R.id.battery);
        close = activity.findViewById(R.id.close);

        port = SData.getInt(SData.Data.Port,-1);

        deviceOptions(activity.getBaseContext());

        if (!notSupportOverAppAsk)
            autoStart.setOnClickListener(view -> {
                //startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
                OverlayPermissionHelper.requestOverlayPermission(activity);
                autoStartClick();
            });
        else
            autoStart.setOnClickListener(view -> autoStartClick());


        if (supportBatteryOptions)
            batterySettings.setOnClickListener(view -> OverlayPermissionHelper.requestBatterySaverSettings(activity));
        else
        {
            TextView batteryText = activity.findViewById(R.id.batteryText);
            batteryText.setText("Your device does not support managing battery optimization.");
        }
    }
    private void deviceOptions(Context context)
    {
        notSupportOverAppAsk = Build.VERSION.SDK_INT < Build.VERSION_CODES.M;
        isTv = isTV(context);
        supportBatteryOptions = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1;
    }
    private void autoStartClick()
    {
        autoStartOn = !autoStartOn;
        SData.set(SData.Data.AutoStart,autoStartOn);
    }
}
