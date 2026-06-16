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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.emb.player.R;

import static app.tools.StaticFunctions.onErrorSave;

public class OverlayPermissionHelper {
    private static final String TAG = "OverlayPermissionHelper";
    public static final int OVERLAY_PERMISSION_REQUEST_CODE = 1234;

    /**
     * Request the SYSTEM_ALERT_WINDOW permission with appropriate fallbacks
     */
    public static void requestOverlayPermission(Activity activity) {
        if (hasOverlayPermission(activity)) {
            Log.d(TAG, "Already has overlay permission");
            return;
        }

        // Try standard Android method first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE);
                return;
            } catch (Exception e) {
                Log.w(TAG, "Standard overlay intent failed", e);
            }
        }

        // Try manufacturer-specific methods
        if (tryManufacturerSpecificIntents(activity)) {
            return;
        }

        // Final fallback
        showManualInstructionsDialog(activity);
    }

    /**
     * Check if overlay permission is granted
     */
    public static boolean hasOverlayPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        }
        // Pre-Marshmallow doesn't require runtime permission
        return true;
    }

    /**
     * Try manufacturer-specific ways to open overlay permission
     */
    private static boolean tryManufacturerSpecificIntents(Context context) {
        String manufacturer = Build.MANUFACTURER.toLowerCase();

        try {
            if (manufacturer.contains("xiaomi")) {
                Intent intent = new Intent("miui.intent.action.APP_PERM_EDITOR")
                        .setClassName("com.miui.securitycenter",
                                "com.miui.permcenter.permissions.PermissionsEditorActivity")
                        .putExtra("extra_pkgname", context.getPackageName());
                context.startActivity(intent);
                return true;
            } else if (manufacturer.contains("oppo")) {
                Intent intent = new Intent()
                        .setClassName("com.coloros.securitypermission",
                                "com.coloros.securitypermission.permission.PermissionAppAllPermissionActivity")
                        .putExtra("packageName", context.getPackageName());
                context.startActivity(intent);
                return true;
            } else if (manufacturer.contains("vivo")) {
                Intent intent = new Intent("com.vivo.permissionmanager.action.APPDETAIL")
                        .putExtra("packageName", context.getPackageName());
                context.startActivity(intent);
                return true;
            } else if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
                Intent intent = new Intent()
                        .setClassName("com.huawei.systemmanager",
                                "com.huawei.permissionmanager.ui.MainActivity");
                context.startActivity(intent);
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Manufacturer-specific intent failed", e);
        }

        return false;
    }

    /**
     * Open app-specific settings as fallback
     */
    public static void openAppSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + context.getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            try {
                context.startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception e2) {
                Log.e(TAG, "Could not open any settings screen", e2);
            }
        }
    }

    /**
     * Show manual instructions when automatic methods fail
     */
    private static void showManualInstructionsDialog(final Activity activity) {
        boolean isTv = activity.getPackageManager().hasSystemFeature("android.software.leanback");

        if (isTv) {
            // TV-specific instructions
            new AlertDialog.Builder(activity)
                    .setTitle("Permission Required")
                    .setMessage("Please enable 'Display over other apps' permission:\n\n" +
                            "1. Open Settings → Apps\n" +
                            "2. Select Special app access\n" +
                            "3. Choose Display over other apps\n" +
                            "4. Find and enable " + activity.getString(R.string.app_name) + "\n\n" +
                            "This permission is required for the app to function properly.")
                    .setPositiveButton("Open Settings", (dialog, which) ->
                            openAppSettings(activity))
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            // Original mobile instructions
            new AlertDialog.Builder(activity)
                    .setTitle("Permission Required")
                    .setMessage("Please enable 'Display over other apps' permission:\n" +
                            "1. Open Settings → Apps\n" +
                            "2. Find and select " + activity.getString(R.string.app_name) + "\n" +
                            "3. Tap Permissions → Display over other apps\n\n" +
                            "On some devices, look for:\n" +
                            "Settings → Special app access → Display over other apps")
                    .setPositiveButton("Open Settings", (dialog, which) ->
                            openAppSettings(activity))
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    /**
     * Handle the result of overlay permission request
     */
    public static void onActivityResult(int requestCode, int resultCode,
            PermissionResultCallback callback) {
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE && callback != null) {
            callback.onOverlayPermissionResult();
        }
    }

    public interface PermissionResultCallback {
        void onOverlayPermissionResult();
    }

    // ... [keep all existing code] ...

    /**
     * Request battery saver settings with fallback alert
     */
    public static void requestBatterySaverSettings(Activity activity) {
        try {
            Intent intent = new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
            if (intent.resolveActivity(activity.getPackageManager()) != null) {
                activity.startActivity(intent);
            } else {
                showBatterySettingsUnavailableDialog(activity);
            }
        } catch (Exception e) {
            onErrorSave("Failed to open battery saver settings",e);
            //Log.e(TAG, "Failed to open battery saver settings", e);
            showBatterySettingsUnavailableDialog(activity);
        }
    }

    /**
     * Show alert when battery settings can't be opened
     */
    private static void showBatterySettingsUnavailableDialog(final Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("Battery Settings Unavailable")
                .setMessage("Unable to open battery settings directly.\n\n" +
                        "Please manually go to:\n" +
                        "Settings → Battery → Battery Saver")
                .setPositiveButton("Try Again", (dialog, which) ->
                        requestBatterySaverSettings(activity))
                .setNegativeButton("Cancel", null)
                .show();
    }
}