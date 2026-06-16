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

import android.graphics.Bitmap;
import java.text.MessageFormat;
import java.util.Objects;
import app.tools.QRCode.io.nayuki.qrcodegen.QrCode;

public class QrCodePage {
    private final String ip;
    private final String withHostname;

    public QrCodePage(String ip,int port,boolean isHaveHostname,String hostname){
        this.ip = make(ip,port);

        if(isHaveHostname)
            withHostname = this.ip + System.lineSeparator() + make(hostname,port);
        else
            withHostname = this.ip;
    }

    public String qrMessage()
    {
        return String.join(System.lineSeparator()+System.lineSeparator(),
                "For the controlling the audio player in another device.",
                MessageFormat.format("Open link in browser:{0}{1}",System.lineSeparator(), withHostname),
                "Or scan the QR code:");
    }

    public Bitmap qrImage()
    {
        return toImage(QrCode.encodeText(ip, QrCode.Ecc.LOW),15,2,0xFFFFFF, 0x000000);
    }

    private String make(String ipOrDomain,int port){
        return MessageFormat.format("{0}{1}:{2}","http://",ipOrDomain,Integer.toString(port));
    }

    private Bitmap toImage(QrCode qr, int scale, int border, int lightColor, int darkColor) {
        Objects.requireNonNull(qr);
        if (scale <= 0 || border < 0)
            throw new IllegalArgumentException("Value out of range");
        if (border > Integer.MAX_VALUE / 2 || qr.size + border * 2L > Integer.MAX_VALUE / scale)
            throw new IllegalArgumentException("Scale or border too large");

        Bitmap result = Bitmap.createBitmap((qr.size + border * 2) * scale, (qr.size + border * 2) * scale, Bitmap.Config.RGB_565);
        for (int y = 0; y < result.getHeight(); y++) {
            for (int x = 0; x < result.getWidth(); x++) {
                boolean color = qr.getModule(x / scale - border, y / scale - border);
                result.setPixel(x, y, color ? darkColor : lightColor);
            }
        }
        return result;
    }
}
