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

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.emb.player.R;

import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.Objects;
import java.util.concurrent.Callable;

import app.tools.Players.all.PlayersCollection;
import app.tools.Recyclable;
import app.tools.StaticFunctions;
import io.reactivex.rxjava3.functions.Consumer;
import server.web.ErrorCodeApp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import static app.tools.DisposableTools.forServer;
import static app.tools.DisposableTools.waitMS;

public abstract class BasePanel extends AppCompatActivity {

    public enum PanelInfo{
        notCreated,Created,notDisplaying,Displaying
    }
    public static boolean blackPanelFix;

    private static final StaticFunctions.StarterEmpty panelBlackScreenClose = new StaticFunctions.StarterEmpty() {
        @Override
        protected void firstLaunch() {
            EmptyActivity.OnRelease.finishMake();
        }

        @Override
        protected void secondLaunches() {

        }
    };
    private static Consumer<Runnable> correctThread;
    private static PanelInfo panelInfo = PanelInfo.notCreated;
    private static BasePanel panel;
    private static boolean updateScreen;
    private final Runnable onResume = new StaticFunctions.Starter() {

        @Override
        protected void firstLaunch() {
            onFirstResume();
        }

        @Override
        protected void secondLaunches() {
            try {
                correctThread.accept(()->{
                    panelInfo = PanelInfo.Displaying;

                    if(updateScreen)
                        refreshDisplayWithoutChecking();

                    setRefreshDisplay(videoController.getHolder());
                });
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    };
    private final StaticFunctions.StarterBase onPause = new StaticFunctions.StarterBase() {
        @Override
        protected void secondLaunches() {
            panelInfo = PanelInfo.notDisplaying;

            try {
                correctThread.accept(()-> setOnNullDisplay());
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    };
    private ViewGroup.LayoutParams layoutParams;
    private SurfaceView videoPanel;
    private SurfaceController videoController;
    private DisplaySize displaySize;
    //private ScreenStateReceiver screenState;
    private Callable<int[]> onRotate;
    private static final StaticFunctions.StarterPost onDestroySurface = new StaticFunctions.StarterPost();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        panel = this;
        panelInfo = PanelInfo.Displaying;

        setContentView(R.layout.display_video);
        videoPanel = findViewById(R.id.videoOutput);
        layoutParams = videoPanel.getLayoutParams();

        displaySize = new DisplaySize();

        videoController = new SurfaceController(videoPanel.getHolder());
        refreshDisplayWithoutChecking();
    }

    @Override
    protected void onDestroy(){
        try {
            super.onDestroy();
        }
        finally {
            onDestroySurface.run();
        }
    }

    @Override
    public void onBackPressed()
    {
        setOnBackPressed();
    }
    @Override
    public void onResume()
    {
        onResume.run();
        super.onResume();
    }

    public void onFirstResume(){}

    @Override
    public void onPause()
    {
        onPause.run();
        super.onPause();
    }
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if(onRotate!=null)
        {
            try {
                int[] dimensions = onRotate.call();
                layoutParams.width = dimensions[0];
                layoutParams.height = dimensions[1];
                videoPanel.setLayoutParams(layoutParams);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return;
        }

        displaySize.rotate();

        refreshDisplayWithoutChecking();/*
        if(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
            LandScapeOn(startedLandscape);
        else
            LandScapeOn(!startedLandscape);*/
    }

    public static SurfaceHolder getHolder()
    {
        return panel.videoController.getHolder();
    }
    public static WindowManager getWin()
    {
        return panel.getWindowManager();
    }
    public static void setCorrectThread(PlayersCollection player)
    {
        if(PlayersCollection.OEM==player)
            correctThread = (ex)->panel.videoPanel.post(ex);
        else
            correctThread = (ex)->ex.run();
    }
    public static boolean panelIsNull(){
        return panel == null;
    }
    public static Consumer<Recyclable.ListDisposable> close(Runnable onDestroy_)
    {
        onDestroySurface.post(()->{
            panelBlackScreenClose.run();
            panelInfo = PanelInfo.notCreated;

            waitMS(100);
            onDestroy_.run();
        });

        panel.onPause.emptySet();

        if(panelInfo == PanelInfo.notDisplaying || !blackPanelFix)
            return (n)->n.addUI(()->{
                panel.finish();
                panel = null;
            },"panelFinish");
        else{
            panelBlackScreenClose.reset();

            return (n)->n.addUI(()->EmptyActivity.OnRelease.loadWithPost.post(() -> n.addUI(()->{
                panel.finish();
                panel = null;
            },"panelFinish")),"panelFinish");
        }
    }

    public static boolean check(PanelInfo with){

        if(with == PanelInfo.Created){
            return panelInfo != PanelInfo.notCreated;
        }

        if(with == PanelInfo.notDisplaying){
            return panelInfo != PanelInfo.Displaying;
        }

        return panelInfo == with;
    }

    public static void aspectChange(int width, int height)
    {
        AspectRation.calculateAspectRatio(width,height);
        updateScreen = true;
    }
    public static void updateScreen(int width, int height){
        if(width!=0&&height!=0)
        {
            aspectChange(width, height);
            refreshDisplay();
        }
    }
    public static void refreshDisplay()
    {
        if(check(PanelInfo.Displaying))
            refreshDisplayWithoutChecking();
    }
    public static void setOnRotate(Callable<int[]> runnable)
    {
        panel.onRotate = runnable;
    }

    public static void runFromPanel(Runnable run)
    {
        if(panel!=null)
            panel.videoPanel.post(run);
    }

    private static void refreshDisplayWithoutChecking()
    {

        panel.videoPanel.post(() -> {
            panel.updateScreenSize(true);
            panel.updateScreen = false;
        });
    }

    public void screenDefault()
    {
        updateScreenSize(getContext().getResources().getDisplayMetrics().widthPixels);
    }
    public void screenSecond()
    {
        updateScreenSize(getContext().getResources().getDisplayMetrics().heightPixels);
    }
    public void updateScreenSize(int size)
    {
        // Create new layout parameters
        //layoutParams.width*9/16
        layoutParams.height = size*9/16;
        layoutParams.width = size;

        // Apply the new layout parameters
        videoPanel.setLayoutParams(layoutParams);
    }
    public void updateScreenSize(boolean isFullscreen) {


        int screenHeight = displaySize.height();
        int screenWidth = displaySize.width();

        float aspectRatio = AspectRation.ration();

        if (isFullscreen) {
            // Fullscreen behavior (like YouTube when rotated to landscape)
            // Fill the screen while maintaining aspect ratio (may crop)
            if (screenHeight > screenWidth) {
                // Portrait fullscreen (uncommon but possible)
                layoutParams.width = screenWidth;
                layoutParams.height = (int) (screenWidth * aspectRatio);
            } else {
                // Landscape fullscreen (standard YouTube behavior)
                layoutParams.height = screenHeight;
                layoutParams.width = (int) (screenHeight / aspectRatio);

                // If video is narrower than screen, center it
                if (layoutParams.width < screenWidth) {
                    if (layoutParams instanceof FrameLayout.LayoutParams) {
                        ((FrameLayout.LayoutParams) layoutParams).gravity = Gravity.CENTER;
                    }
                } else {
                    // If video is wider than screen, adjust to fit width
                    layoutParams.width = screenWidth;
                    layoutParams.height = (int) (screenWidth * aspectRatio);
                }
            }
        } else {
            // Normal mode (like YouTube in portrait)
            layoutParams.width = screenWidth;
            layoutParams.height = (int) (screenWidth * aspectRatio);

            // Always center in normal mode
            if (layoutParams instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) layoutParams).gravity = Gravity.CENTER;
            }
        }

        // Apply the layout parameters
        videoPanel.setLayoutParams(layoutParams);

        // Optional: Force layout update
        videoPanel.requestLayout();
    }

    public static Context getContext()
    {
        return panel.getContext();
    }

    private void landScapeOn(boolean on)
    {
        if(on)
            screenDefault();
        else
            screenSecond();
    }

    protected abstract void setOnLoadVideo(SurfaceHolder holder) throws Throwable;
    protected abstract void setOnBackPressed();
    protected abstract void setRefreshDisplay(SurfaceHolder holder);
    protected abstract void setOnNullDisplay();


    public static class AspectRation
    {
        private final static float defaultRatio = 9f / 16f;

        private static float aspectRatio = defaultRatio;

        public static void defaultSet()
        {
            aspectRatio = defaultRatio;
        }

        public static float ration()
        {
            return aspectRatio;
        }

        public static void calculateAspectRatio(VideoStream videoStream)
        {
            calculateAspectRatio(videoStream.getWidth(),videoStream.getHeight());
        }

        public static void calculateAspectRatio(int width, int height)
        {
            float gcd = findGCD(width, height);
            aspectRatio = (height / gcd) / (width / gcd);
        }

        // Helper method to find Greatest Common Divisor (GCD)
        private static float findGCD(float a, float b) {
            if (b == 0) return a;
            return findGCD(b, a % b);
        }
    }

    public class DisplaySize
    {
        private int screenWidth;
        private int screenHeight;

        public DisplaySize()
        {/*
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;*/
            Rect usableRect = new Rect();
            Window window = getWindow();
            window.getDecorView().getWindowVisibleDisplayFrame(usableRect);

            screenWidth = usableRect.width();
            screenHeight = usableRect.height();
        }


        public void rotate()
        {
            int data = screenWidth;
            screenWidth = screenHeight;
            screenHeight = data;
        }

        public int width()
        {
            return screenWidth;
        }

        public int height()
        {
            return screenHeight;
        }
    }

    public class SurfaceController
    {
        private SurfaceHolder holder;
        private Consumer<SurfaceHolder> holderFunction;

        public SurfaceController(SurfaceHolder Holder)
        {
            holder = Holder;
            onSurfaceCreateFunctionSet();
            holder.addCallback(new SurfaceHolder.Callback()
            {
                @Override
                public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
                    try {
                        holderFunction.accept(surfaceHolder);
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {
                }

                @Override
                public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
                    //setOnNullDisplay();
                    //onDestroySurface.run();
                }
            });
        }

        public SurfaceHolder getHolder()
        {
            return holder;
        }

        private void holderFunctionSet(Consumer<SurfaceHolder> function)
        {
            holderFunction = function;
        }

        private void onSurfaceCreateFunctionSet()
        {

            holderFunctionSet((surfaceHolder) -> {
                try {
                    setOnLoadVideo(surfaceHolder);
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }

                onSurfaceRestartFunctionSet();
            });
        }

        private void onSurfaceRestartFunctionSet()
        {
            holderFunctionSet((surfaceHolder) -> setRefreshDisplay(surfaceHolder));
        }
    }
}
