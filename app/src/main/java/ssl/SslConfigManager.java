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

package ssl;

import android.os.Build;

public class SslConfigManager {

    // Enable Conscrypt for devices older than Android 10 (API 29) to backport TLS 1.3
    private static boolean useConscrypt = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q;

    // Enable insecure SSL fallbacks
    private static boolean enableInsecureSSL = true;

    // Use legacy TLS configurations for pre-Lollipop devices (< API 21)
    private static boolean legacyTlsForOldAndroid = Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP;

    public static boolean isUseConscrypt() {
        return useConscrypt;
    }

    public static boolean isEnableInsecureSSL() {
        return enableInsecureSSL;
    }

    public static boolean isLegacyTlsForOldAndroid() {
        return legacyTlsForOldAndroid;
    }
}