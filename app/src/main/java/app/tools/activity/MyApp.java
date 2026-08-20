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

import androidx.multidex.MultiDexApplication;

public class MyApp extends MultiDexApplication {/*

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
    @Override
    public void onCreate() {
        super.onCreate();

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
                hideNavigationButtons.accept(activity);
                dontSleep(activity);
            }

            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }

    private static void dontSleep(Activity current){
        current.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }*/
}