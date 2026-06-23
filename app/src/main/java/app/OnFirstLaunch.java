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

import android.os.Bundle;

import app.tools.SData;
import io.reactivex.rxjava3.disposables.Disposable;

import static app.tools.DisposableTools.addTask;
import static app.tools.DisposableTools.addTaskUI;
import static app.tools.DisposableTools.forServer;

public class OnFirstLaunch extends Permissions{
    private Disposable appLoader;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setup();

        pBase.portSet.setOnClickListener(view -> {

            pBase.port = Integer.parseInt(pBase.portInput.getText().toString());

            if(pBase.port!=-1)
            {
                SData.setInt(SData.Data.Port, pBase.port);

                pBase.close.setOnClickListener(view1 -> {
                    appLoader = addTask(()->{
                        Main.startServiceFromDifferentActivityOrHere();
                        appLoader = addTaskUI(()->{
                            finish();
                            return true;
                        },()->"OnFirstLoaderClose");
                        return true;
                    },()->"OnFirstLoader",forServer);
                });
                pBase.close.setEnabled(true);
            }
        });

        SData.set(SData.Data.FirstStartMade,true);
    }
    
}
