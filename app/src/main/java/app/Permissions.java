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

//import android.content.SharedPreferences;
import android.os.Bundle;

import com.emb.player.R;

import java.net.ServerSocket;

import app.services.BaseServer;
import app.tools.SData;
import app.tools.activity.DefaultActivity;
import io.reactivex.rxjava3.disposables.Disposable;

import static app.tools.DisposableTools.addTask;
import static app.tools.DisposableTools.addTaskUI;
import static app.tools.DisposableTools.forServer;
import static server.Home.app;

public class Permissions extends DefaultActivity {

    private Disposable appLoader;
    private int savedPort;
    protected PermissionsFunctionality pBase;

    void setup()
    {
        pBase = new PermissionsFunctionality();
        setContentView(R.layout.permissions);
        pBase.baseFunction(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setup();

        savedPort = pBase.port;

        pBase.portInput.setText(Integer.toString(savedPort));

        pBase.close.setOnClickListener(view ->
        {
            appLoader = addTask(()->{
                if(savedPort != pBase.port)
                {
                    BaseServer.restart();
                }
                appLoader = addTaskUI(()->{
                    finish();
                    return true;
                },()->"PermissionsLoaderFinish");
                return true;
            },()->"PermissionsLoader",forServer);
        });

        pBase.close.setEnabled(true);
        pBase.portInfo.setText("AFTER SET RESTART");

        pBase.portSet.setOnClickListener(view -> {
            updatePort();
            //BaseServer.Port(pBase.port);
            //ProcessPhoenix.triggerRebirth(getContext());
            app().sendURL();
            Main.loadUI();
        });
    }

    protected void updatePort(){
        String txt = pBase.portInput.getText().toString();
        try {
            pBase.port = Integer.parseInt(txt);

            if(pBase.port < 1)
                updatePortNext();
        }
        catch (Exception e){
            updatePortNext();
        }

        pBase.portInput.setText(Integer.toString(pBase.port));

        SData.setInt(SData.Data.Port, pBase.port);
    }

    private void updatePortNext(){
        try {
            pBase.port = new ServerSocket(0).getLocalPort();

            if(pBase.port < 1)
                pBase.port = 8080;
        } catch (Exception e) {
            pBase.port = 8080;
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
        pBase.autoStartOn = SData.get(SData.Data.AutoStart);

        pBase.autoStart.setChecked(pBase.autoStartOn);
    }

    @Override
    public void onBackPressed()
    {}
}
