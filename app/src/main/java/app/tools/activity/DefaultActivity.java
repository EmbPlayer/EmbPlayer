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
package app.tools.activity;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import java.util.function.Consumer;

import androidx.annotation.CallSuper;
import androidx.appcompat.app.AppCompatActivity;
import app.tools.DisposableTools;
import app.tools.SData;
import app.tools.StaticFunctions;
import io.reactivex.rxjava3.disposables.Disposable;

public class DefaultActivity extends AppCompatActivity {
    private static Disposable brightnessUpdate;
    private static DefaultActivity currentActivity;
    private static Consumer<Activity> hideNavigationButtons = (curr)->{
        Consumer<Activity> temp;

        // --- HIDE NAVIGATION BAR WITH SWIPE-TO-SHOW BEHAVIOR ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            temp = (current)->{
                // Android 11 (API 30) and above - The official, non-deprecated way
                WindowInsetsController controller = current.getWindow().getInsetsController();
                if (controller != null) {
                    // Hide the bottom navigation bar
                    controller.hide(WindowInsets.Type.navigationBars());
                    // This makes it reappear temporarily when the user swipes from the bottom
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    );
                }
            };
        else
            temp = (current)->{
                // Android 10 (API 29) and below - The legacy fallback
                @SuppressWarnings("deprecation")
                int uiFlags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                current.getWindow().getDecorView().setSystemUiVisibility(uiFlags);
            };

        hideNavigationButtons = temp;
        temp.accept(curr);
    };
    public static float brightness;

    private static void dontSleep(Activity current){
        current.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private final StaticFunctions.Starter onResume = new StaticFunctions.Starter() {
        @Override
        protected void firstLaunch() {
            hideNavigationButtons.accept(DefaultActivity.this);
            dontSleep(DefaultActivity.this);
        }

        @Override
        protected void secondLaunches() {

        }
    };

    @Override
    @CallSuper
    public void onResume() {
        currentActivity = this;
        setAppBrightness(DefaultActivity.brightness);
        super.onResume();
        onResume.run();
    }

    public static void setBrightness(float brightness){
        DefaultActivity.brightness = brightness;
        SData.setFloat(SData.Data.BrightnessLevel,brightness);

        if(currentActivity==null)
            return;

        currentActivity.setAppBrightness(brightness);
    }

    public static int getBrightnessAsInt(){
        return (int)(brightness*100);
    }

    private void setAppBrightness(float brightness) {
        if(brightnessUpdate!=null&&!brightnessUpdate.isDisposed())
            brightnessUpdate.dispose();

        DisposableTools.addTaskUI(()->{
            WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
            layoutParams.screenBrightness = brightness;
            getWindow().setAttributes(layoutParams);
            return true;
        },()->"Brightness_Update");
    }
}
