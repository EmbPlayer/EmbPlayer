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
import app.tools.StaticFunctions;

public class DefaultActivity extends AppCompatActivity {
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
        super.onResume();
        onResume.run();
    }
}
