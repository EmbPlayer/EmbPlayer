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

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.emb.player.R;

import java.util.concurrent.Callable;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import app.App.AppBack;
import app.services.BaseServer;
import app.tools.AndroidOsUpdatesListener;
import app.tools.DisposableTools;
import app.tools.QrCodePage;
import app.tools.SData;
import app.tools.StaticFunctions;
import io.reactivex.rxjava3.disposables.Disposable;

import static app.tools.DisposableTools.addTask;
import static app.tools.DisposableTools.addTaskUI;
import static app.tools.DisposableTools.forServer;
import static app.tools.DisposableTools.forkJoinPool;
import static server.Home.app;

public class Main extends AppCompatActivity{

    // In your Activity or Fragment
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String REQUIRED_PERMISSION = Manifest.permission.CHANGE_WIFI_MULTICAST_STATE;

    private static Main main;
    private static Disposable appLoader;

    private static boolean notDisplaying;

    private static final onResume onResume = new onResume();

    private Bitmap qrCode;
    private String qrText;
    private Configuration config;
    private Disposable uiUpdate;

    //private SurfaceView videoPanel;
    //private SurfaceHolder videoHolder;
    private ImageView qrOutput;
    private TextView textOutput;
    private Button permissions;
    //private PairingCode code;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        appLoader = DisposableTools.addTask(()->{
            main = this;

            SData.LoadData(getBaseContext());

            if(SData.get(SData.Data.FirstStartMade)/* && savedPort!=-1*/)
                startServiceFromDifferentActivityOrHere();
            else
                appLoader = addTaskUI(()->{
                    startActivity(new Intent(getBaseContext(), OnFirstLaunch.class));
                    return true;
                },()->"loaderOnFirst");

            return true;
        },()->"loading_App", forServer);
    }

    @Override
    public void onPause()
    {
        notDisplaying = true;
        //code.SwitchToWithoutUI();
        super.onPause();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        onResume.run();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setConfig(newConfig);
        loadMainPage();
        if(!BaseServer.serverIsNotCreated())
            updateQr();
    }

    @Override
    public void onBackPressed()
    {
        StaticFunctions.kill();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                appLoader = addTask(()->{
                    startService();
                    return true;
                },()->"startServer",forServer);
            } else {
                // Permission denied
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, REQUIRED_PERMISSION)) {
                    showPermissionDeniedDialog();
                } else {
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    public static boolean notDisplaying()
    {
        return notDisplaying;
    }

    public static Context getContext()
    {
        return main.getBaseContext();
    }

    public static void startService()
    {
        appLoader = addTask(()->{
            main.startService(new Intent(getContext(), BaseServer.class));
            return true;
        },()-> "Restarting_Server",forServer);
    }

    public static void startServiceFromDifferentActivityOrHere()
    {
        onResume.reset();
        main.setConfig(main.getResources().getConfiguration());
        main.checkPermissionsAndStartServer(()->main.loadMainPage());
    }
    public static void qrSetup()
    {
        qrFirst();
        main.updateQr();
    }
/*
    public static void RefreshQRCode()
    {
        main.QrSetup();
        if(main!=null&&!notDisplaying)
            main.UpdateQr();
    }*/

    public static void loadUI()
    {
        Disposable load = addTask((Callable<Boolean>) () -> {
            BaseServer.updateLocalhost();
            Main.qrSetup();
            return true;
        },() -> "LoadUI-Error",forkJoinPool);
    }

    public static void createUI()
    {
        Disposable load = addTask(() -> {
            BaseServer.ipAddressLoad();
            BaseServer.updateLocalhost();

            String savedHostnameOrIp = SData.getString(SData.Data.SavedIPorMac);

            String currentIpOrMac;

            if(BaseServer.isHaveHostname())
                currentIpOrMac = AndroidOsUpdatesListener.getCurrentRouterMac();
            else
                currentIpOrMac = BaseServer.getLocalhost();

            if(savedHostnameOrIp==null)
            {
                SData.setString(SData.Data.SavedIPorMac,currentIpOrMac);
            }
            else if(!savedHostnameOrIp.equals(currentIpOrMac))
            {
                SData.setString(SData.Data.SavedIPorMac,currentIpOrMac);
                SData.resetToDefault();
            }

            qrFirst();
            updateQr();
            return true;
        }, ()->"createUI",forkJoinPool);
    }

    public static void loadPage(Class<?> page)
    {
        main.startActivity(new Intent(main, page));
    }

    private static void qrFirst()
    {
        QrCodePage QrC = new QrCodePage(BaseServer.getIP(),BaseServer.getPort(),BaseServer.isHaveHostname(),BaseServer.getLocalhost());
        main.qrCode = QrC.qrImage();
        //qrText = QrC.QrMessage();
        main.qrText = QrC.qrMessage();
    }

    private static void qrSecond()
    {
        main.qrOutput.setImageBitmap(main.qrCode);
        main.textOutput.setText(main.qrText);
    }

    private static void updateQr()
    {
        if(notDisplaying())
            main.onResume.qrUpdater.reset();
        else
            main.uiUpdate = addTaskUI(() -> {
                qrSecond();
                return true;
            },() -> "UpdateQR");
    }

    private void loadMainPage()
    {
        page(R.layout.server_landscape,R.layout.server);
        qrOutput = findViewById(R.id.idIVQrcode);
        textOutput = findViewById(R.id.info);
        permissions = findViewById(R.id.permissionsButton);
        permissions.setOnClickListener(view -> startActivity(new Intent(getBaseContext(), Permissions.class)));
    }

    private void setConfig(Configuration newConfig)
    {
        config = newConfig;
    }

    private void page(int landscapePage, int portraitPage)
    {
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setContentView(landscapePage);
        } else if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
            setContentView(portraitPage);
        }
    }

    private void checkPermissionsAndStartServer(Runnable onUI) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(getBaseContext(), REQUIRED_PERMISSION)
                    != PackageManager.PERMISSION_GRANTED) {

                if (ActivityCompat.shouldShowRequestPermissionRationale(this, REQUIRED_PERMISSION)) {
                    // Explain why permission is needed
                    appLoader = addTaskUI(()->{
                        showPermissionExplanationDialog();
                        onUI.run();
                        return true;
                    },()->"uiLoader");
                } else {
                    // First time request or "Don't ask again" was selected
                    appLoader = addTaskUI(()->{
                        requestPermission();
                        onUI.run();
                        return true;
                    },()->"uiLoader");
                }
            } else {
                // Permission already granted
                startService();
                appLoader = addTaskUI(()->{
                    onUI.run();
                    return true;
                },()->"uiLoader");
            }
        } else {
            // Permission granted by default on older Android versions
            startService();
            appLoader = addTaskUI(()->{
                onUI.run();
                return true;
            },()->"uiLoader");
        }
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{REQUIRED_PERMISSION},
                PERMISSION_REQUEST_CODE
        );
    }

    private void showPermissionExplanationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Multicast Permission Needed")
                .setMessage("This app needs multicast permissions to discover local network services")
                .setPositiveButton("OK", (dialog, which) -> requestPermission())
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("You have permanently denied multicast permission. " +
                        "Please enable it in app settings.")
                .setPositiveButton("Go to Settings", (dialog, which) -> openAppSettings())
                .setNegativeButton("Cancel", null)
                .setCancelable(false)
                .show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }


    private static class onResume extends StaticFunctions.Starter {

        private final StaticFunctions.Starter qrUpdater = new StaticFunctions.StarterEmpty() {
            @Override
            protected void firstLaunch() {
                qrSecond();
            }

            @Override
            protected void secondLaunches() {

            }
        };

        private void onDisplaying()
        {
            notDisplaying = false;
            qrUpdater.run();
        }


        @Override
        protected void firstLaunch() {
            onDisplaying();
        }

        @Override
        protected void secondLaunches() {
            onDisplaying();
            /*UpdateQr();*/

            if(!app().setUp.get())
                return;

            if(!app().videoPanelIsActive())
                return;

            if(AppBack.Panel.check(BasePanel.PanelInfo.Created))
                return;

            app().reloadMedia(()->{
                app().errorHandel.getSeek();
                return true;
            },()->{});

            /*
            TextView codeOut = main.findViewById(R.id.code);
            TextView liveTime = main.findViewById(R.id.codeLiveTime);
            code.SwitchToUI(codeOut,liveTime);*/
        }
    }
}
