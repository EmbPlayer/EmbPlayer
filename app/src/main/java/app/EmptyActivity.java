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

import com.emb.player.R;

import java.util.function.Consumer;

import androidx.appcompat.app.AppCompatActivity;
import app.App.AppBack;
import app.tools.StaticFunctions;
import io.reactivex.rxjava3.disposables.Disposable;

import static app.tools.DisposableTools.addTaskUI;
import static app.tools.DisposableTools.waitMS;

public class EmptyActivity {
    private static AppCompatActivity current;

    public static class OnRelease extends AppCompatActivity {
        public static final LoadWithPost loadWithPost = new LoadWithPost();
        private static final Consumer<Runnable> correctThread = (r)->AppBack.Panel.runFromPanel(r);
        private static final StaticFunctions.Starter loader = new StaticFunctions.Starter() {
            @Override
            protected void firstLaunch() {
                EmptyActivity.load(OnRelease.class,correctThread);
            }

            @Override
            protected void secondLaunches() {

            }
        };

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            finishCurrent();
            current = this;
            setContentView(R.layout.empty);
            findViewById(R.id.empty).post(loadWithPost);
        }

        public static void finishMake()
        {
            loader.reset();
            finishCurrent();
        }

        public static class LoadWithPost extends StaticFunctions.Starter {
            private Runnable make = ()->{};

            public void post(Runnable make){
                this.make = make;
                reset();
                loader.run();
            }

            @Override
            protected void firstLaunch() {
                make.run();
            }

            @Override
            protected void secondLaunches() {

            }
        }
    }

    public static class EmptyIJK extends AppCompatActivity {
        private static Disposable onUI;
        private static final Consumer<Runnable> correctThread = (r)-> {
            disposeUI();
            onUI = addTaskUI(()->{
                r.run();
                return true;
            },()->"EmptyIJK");
        };

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            finishCurrent();
            current = this;
            setContentView(R.layout.empty);

            findViewById(R.id.empty).post(()-> {
                waitMS(100);
                finishCurrent();
            });
        }

        public static void load()
        {
            EmptyActivity.load(EmptyIJK.class,correctThread);
        }

        public static void finishMake()
        {
            disposeUI();
            finishCurrent();
        }

        private static void disposeUI()
        {
            if(onUI==null||onUI.isDisposed())
                return;

            onUI.dispose();
        }
    }

    public static class EmptyOem extends AppCompatActivity {

        private static final Consumer<Runnable> correctThread = (r)->AppBack.Panel.runFromPanel(r);
        private static final StaticFunctions.Starter loader = new StaticFunctions.Starter() {
            @Override
            protected void firstLaunch() {
                EmptyActivity.load(EmptyOem.class,correctThread);
            }

            @Override
            protected void secondLaunches() {

            }
        };

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            finishCurrent();
            current = this;
            setContentView(R.layout.empty);
        }

        public static void load()
        {
            loader.run();
        }

        public static void finishMake()
        {
            loader.reset();
            finishCurrent();
        }
    }

    private static void finishCurrent()
    {
        if(current==null)
            return;

        current.finish();
    }

    private static void load(Class<?> em, Consumer<Runnable> correctThread)
    {
        if(AppBack.Panel.check(BasePanel.PanelInfo.Displaying))
            correctThread.accept(()->Main.loadPage(em));
    }
}