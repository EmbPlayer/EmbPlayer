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
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import com.emb.player.R;

import androidx.annotation.Nullable;
import app.Main;
import app.tools.activity.DefaultActivity;
import dev.doubledot.doki.api.tasks.DokiApi;
import dev.doubledot.doki.ui.DokiActivity;
import dev.doubledot.doki.views.DokiContentView;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public class BatterySettings{

    public static View.OnClickListener onClickBatterySettingsButton(Context context){
        return view -> {
            Main.loadPage(Doki.class);
        };
    }

    public static class Doki extends DefaultActivity {

        private static final String device = Build.MANUFACTURER.toLowerCase().replace(" ", "-");

        private DokiApi api = null;

        public static final String MANUFACTURER_EXTRA = "dev.doubledot.doki.ui.DokiActivity.MANUFACTURER_EXTRA";

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            DokiContentView dokiView = new DokiContentView(this);
            setContentView(dokiView);

            String manufacturerId = device;

            if (getIntent() != null && getIntent().getExtras() != null) {
                String extra = getIntent().getExtras().getString(MANUFACTURER_EXTRA);
                if (extra != null) {
                    manufacturerId = extra;
                }
            }

            api = dokiView.loadContent(manufacturerId);

            dokiView.setOnCloseListener(view -> {
                finish();
                return Unit.INSTANCE;
            });
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            if (api != null) {
                api.cancel();
            }
        }
    }

}