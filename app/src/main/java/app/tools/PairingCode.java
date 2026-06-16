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

import android.widget.TextView;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;

import static app.tools.DisposableTools.waitMS;

public class PairingCode {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final int length;
    private final int liveSeconds;

    private final Runnable defaulthRunnable;

    private int getLiveSeconds;
    private String code;
    private Disposable liveTime;
    private Runnable baseRunnable;
    private Runnable loopRunnable;
    private int savedSeconds;

    public PairingCode(int secondsMaxTime,int itemsCount)
    {
        liveSeconds = secondsMaxTime;
        getLiveSeconds = -1;
        length = itemsCount;
        defaulthRunnable = () -> {

            waitMS(1000);
            getLiveSeconds--;
        };
    }

    public void startWithoutUI()
    {
        withoutUI();
        start();
    }
    public void startWithUI(TextView codeOutput, TextView liveTimeOutput)
    {
        withUI(codeOutput,liveTimeOutput);
        start();
    }

    public void switchToUI(TextView codeOutput, TextView liveTimeOutput)
    {
        switching();
        withUI(codeOutput,liveTimeOutput);
    }

    public void switchToWithoutUI()
    {
        switching();
        withoutUI();
    }

    public String getCode()
    {
        return code;
    }

    public int getLiveSeconds()
    {
        return getLiveSeconds;
    }

    public boolean checkCode(String typedCode)
    {
        String upperCaseTypedCode = typedCode.toUpperCase();
        return upperCaseTypedCode.equals(code);
    }

    private void switching()
    {
        savedSeconds = getLiveSeconds;
        loopRunnable = () -> getLiveSeconds = -2;
        baseRunnable = () -> getLiveSeconds = -2;
        while (getLiveSeconds!=-2)
        {
            waitMS(100);
        }
    }

    private Runnable uiLivetimeFunctionality(TextView liveTimeOutput)
    {
        return () -> {
            liveTimeOutput.setText(Integer.toString(getLiveSeconds));
            defaulthRunnable.run();
        };
    }

    private String generateRandomString() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);

        // Generate random characters from the CHARACTERS set
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(CHARACTERS.length());
            sb.append(CHARACTERS.charAt(index));
        }

        return sb.toString();
    }

    private void generateCode()
    {
        if(getLiveSeconds==-1)
        {
            getLiveSeconds = liveSeconds;
            code = generateRandomString();
        }
    }

    private boolean baseLoop()
    {
        while (getLiveSeconds>-1)
        {
            loopRunnable.run();
        }
        return true;
    }

    private void start()
    {
        liveTime = Observable.interval(1, TimeUnit.SECONDS)
                .subscribe(item ->
                        {
                            baseRunnable.run();
                        }
                );
    }

    private void withoutUI()
    {
        baseRunnable = () -> {
            generateCode();
            baseLoop();
        };
        loopRunnable = defaulthRunnable;
        getLiveSeconds = savedSeconds;
    }

    private void withUI(TextView codeOutput, TextView liveTimeOutput)
    {
        baseRunnable = () -> {
            generateCode();
            codeOutput.setText(code);
            baseLoop();
        };
        loopRunnable = uiLivetimeFunctionality(liveTimeOutput);
        getLiveSeconds = savedSeconds;
    }
}
