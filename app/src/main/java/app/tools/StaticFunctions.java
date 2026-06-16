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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import java.util.Date;
import java.util.List;

import androidx.annotation.CallSuper;
import app.services.BaseServer;
import app.tools.Generators.Requirements.Piped.VideoQuality;
import app.tools.Generators.YoutubeGenerator;
import io.reactivex.rxjava3.disposables.Disposable;
import server.web.Wait;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;
import java.net.InetAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import static app.Main.getContext;
import static app.tools.DisposableTools.addTask;
import static app.tools.DisposableTools.forkJoinPool;

public class StaticFunctions {
    public static void onThrows(Thread thread, Throwable throwable) {
        String errorFrom = "UncaughtException: ";
        saveError(errorFrom + StaticFunctions.getFullReport(errorFrom,thread,throwable, getContext()));
        BaseServer.restart();
    }
    public static void onThrows(Throwable throwable) {
        onErrorSave("UncaughtException: ",throwable);
        BaseServer.restart();
    }
    public static void onErrorSave(String errorFrom, Throwable throwable)
    {
        saveError(errorFrom + StaticFunctions.getFullReport(errorFrom,Thread.currentThread(),throwable, getContext()));
    }

    public static String[] objectsToString(Object... raw)
    {
        String[] out = new String[raw.length];

        for(int i = 0; i<raw.length; i++)
        {
            out[i] = raw[i]+"";
        }

        return out;
    }
    public static String getInfo(String name) {
        return getFullReport(name,Thread.currentThread(),null,null);
    }

    private static String getFullReport(String title,Thread currentThread, Throwable throwable, Context context) {
        StringBuilder report = new StringBuilder();

        // 1. Header & Timestamp
        report.append("=== ").append(title != null ? title : "REPORT").append(" ===\n");
        report.append("Timestamp: ").append(new Date().toString()).append("\n");

        // 2. App Version (Requires Context)
        if (context != null) {
            try {
                PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                report.append("App Version: ").append(pInfo.versionName)
                        .append(" (").append(pInfo.versionCode).append(")\n");
            } catch (Exception e) {
                report.append("App Version: Unknown\n");
            }
        }

        // 3. Device / Android Info
        report.append("Android Version: ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        report.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        report.append("Product: ").append(Build.PRODUCT).append("\n");

        // 4. Thread Info (Your specific request)
        report.append("Thread: ").append(currentThread.getName()).append("\n\n");

        // 5. Error Details OR Stack Trace
        if (throwable != null) {
            // If an exception exists, print the full stack trace of that exception
            report.append("Error Details:\n");
            report.append(android.util.Log.getStackTraceString(throwable));
        } else {
            // If no exception, capture the current call stack (like your first method)
            report.append("Call Stack:\n");
            StackTraceElement[] stackTrace = currentThread.currentThread().getStackTrace();
            // Skip first 3-4 frames to avoid showing this utility method in the trace
            for (int i = 3; i < stackTrace.length; i++) {
                report.append("    at ").append(stackTrace[i].toString()).append("\n");
            }
        }

        return report.toString();
    }
    private static void saveError(String output)
    {
        SData.setString(SData.Data.SavedDisposableErrors,SData.getString(SData.Data.SavedDisposableErrors,"")+"["+output+"] ");
    }

    public static String asJsonFormat(VideoQuality value)
    {
        return asJsonFormat(value.name());
    }

    public static String asJsonFormat(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }
    public static String[] getAllForJson(String[] elements) {
        String[] resStringList = new String[elements.length];

        for (int i = 0;  i < elements.length;  i++) {
            resStringList[i] = StaticFunctions.asJsonFormat(elements[i]);
        }

        return resStringList;
    }

    public static String setData(String... Data)
    {
        return "["+String.join(",",Data)+"]";
    }

    public static String[] itemAsJson(List<Item> devices)
    {
        String[] output = new String[devices.size()];

        for(int i = 0; i<output.length; i++)
        {
            Item device = devices.get(i);

            String name = device.name();
            String result = name.substring(0, 1).toUpperCase() + name.substring(1);

            output[i] = setData(new String[]{asJsonFormat(result), asJsonFormat(device.value())});
        }

        return output;
    }

    public static String[] itemAsJsonNormal(Item[] table)
    {
        String[] output = new String[table.length];

        for(int i = 0; i<output.length; i++)
        {
            Item device = table[i];

            output[i] = setData(new String[]{asJsonFormat(device.name()), asJsonFormat(device.value())});
        }

        return output;
    }

    public static String[] itemWithIdAsJson(List<ItemWithId> devices)
    {
        String[] output = new String[devices.size()];

        for(int i = 0; i<output.length; i++)
        {
            ItemWithId device = devices.get(i);

            String name = device.name();
            if(name!=null&&!name.isEmpty()) {
                name = name.substring(0, 1).toUpperCase() + name.substring(1);
            }
            name = asJsonFormat(name);

            output[i] = setData(new String[]{device.id(),name, asJsonFormat(device.value())});
        }

        return output;
    }

    public static int getCurrentTimeAsSeconds() {
        //long num = Math.abs(number);
        //long num = System.currentTimeMillis();
        long num = getNtpUnixTimeMillis();

        // Count the number of digits
        int digitCount = (int) Math.log10(num) + 1;

        if (digitCount <= 10) {
            return (int) num;
        }

        // Remove extra digits from the right
        long result = num / (long) Math.pow(10, digitCount - 10);
        return (int) result;
    }

    // Alternative method that returns milliseconds
    public static long getNtpUnixTimeMillis() {
        try {
            NTPUDPClient client = new NTPUDPClient();
            client.setDefaultTimeout(10000);
            client.open();

            InetAddress hostAddr = InetAddress.getByName("pool.ntp.org");
            TimeInfo timeInfo = client.getTime(hostAddr);
            timeInfo.computeDetails();

            Long offset = timeInfo.getOffset();
            long ntpTime = System.currentTimeMillis() + (offset != null ? offset : 0);
            client.close();

            return ntpTime;

        } catch (Exception e) {
            onErrorSave("getNtpUnixTimeMillis",e);
            //e.printStackTrace();
            return System.currentTimeMillis(); // Fallback to system time
        }
    }

    public static void kill()
    {
        //android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    public static class StarterPost implements Runnable{
        protected Runnable run = ()->{};
        private final Runnable empty = ()->{};

        public void post(Runnable task){
            run = ()->{
                task.run();
                run = empty;
            };
        }

        @Override
        public void run() {
            run.run();
        }
    }

    public static abstract class StarterBase implements Runnable{
        protected Runnable run;
        private final Runnable empty = ()->{};

        public StarterBase(){
            run = ()->secondLaunches();
        }

        @Override
        public void run() {
            run.run();
        }

        protected abstract void secondLaunches();

        public final void emptySet()
        {
            run = empty;
        }
    }

    public static abstract class Starter extends StarterBase {

        public Starter()
        {
            setup();
        }

        protected abstract void firstLaunch();

        @CallSuper
        public void reset()
        {
            setup();
        }

        private void setup()
        {
            run = ()->{
                firstLaunch();
                run = () -> secondLaunches();
            };
        }

        public void setToSecond()
        {
            run = () -> secondLaunches();
        }
    }

    public static abstract class StarterEmpty extends Starter{
        public StarterEmpty(){
            emptySet();
        }
    }

    public static abstract class StarterWithBoolean implements Callable<Boolean> {

        private final AtomicReference<Callable<Boolean>> run = new AtomicReference<>();

        public StarterWithBoolean() {
            reset();
        }

        @Override
        public Boolean call() throws Exception {
            Callable<Boolean> r = run.get();
            return r.call();
        }

        protected abstract Boolean firstLaunch() throws Exception;
        protected abstract Boolean secondLaunches() throws Exception;

        public final void reset() {
            run.set(() -> {
                Boolean result = firstLaunch();
                run.set(() -> secondLaunches());
                return result;
            });
        }
    }

    public static abstract class TryToComplete implements Callable<Boolean> {

        private Callable<Boolean> run;

        public TryToComplete() {
            reset();
        }

        @Override
        public Boolean call() throws Exception {
            return run.call();
        }

        protected boolean afterMadeReturn()
        {
            return true;
        }

        protected abstract Boolean completion();

        public void reset() {
            run = () -> {
                if(completion())
                {
                    run = () -> afterMadeReturn();
                    return true;
                }
                return false;
            };
        }
    }

    public static class LoadClass
    {
        public static void Load() {

            YoutubeGenerator.updateNewPipe();
        }
    }

    public static class Item
    {
        private String name;
        private String value;

        public Item(String name, String value)
        {
            this.name = name;
            this.value = value;
        }

        public String name()
        {
            return name;
        }
        public String value()
        {
            return value;
        }
    }

    public static class ItemWithId extends Item
    {
        private String id;

        public ItemWithId(String id,String name, String link)
        {
            super(name,link);
            this.id = id;
        }

        public String id() {return id;}
    }

    public static class ActionWait
    {
        protected boolean actionStarted = false;
        protected long savedTime;
        protected Disposable currentAction;

        public final synchronized boolean isActivated()
        {
            return actionStarted && savedTime+ maxWaitMs()>System.currentTimeMillis();
        }

        public final synchronized void resetState()
        {
            actionStarted = false;
        }

        public final synchronized void actionStart(Callable<Boolean> Base, Callable<String> OnError) {

            if (isActivated())
                return;

            activate();
            disposeAndRun(Base,OnError);
        }

        public final synchronized void disposeAndRun(Callable<Boolean> Base, Callable<String> OnError){
            onDispose();

            run(Base,OnError);
        }

        public final boolean actionIsStarted()
        {
            return actionStarted;
        }

        public final synchronized void currentStopAndResetStateAndUIWait() {
            stopCurrent();
            resetStateAndUIWait();
        }
        public final synchronized void currentStopAndResetState() {
            stopCurrent();
            resetState();
        }

        public final synchronized void resetStateAndUIWait()
        {
            resetState();
            Wait.webUIWaitStop();
        }

        protected synchronized long maxWaitMs()
        {
            return 20000;
        }

        @CallSuper
        protected synchronized void onDispose()
        {
            stopCurrent();
        }

        protected synchronized void onSuccessEnd()
        {}

        protected final synchronized void activate()
        {
            actionStarted = true;
            savedTime = System.currentTimeMillis();
        }

        protected synchronized Disposable onThread(Callable<Boolean> Base, Callable<String> OnError)
        {
            return addTask(Base,OnError,forkJoinPool);
        }

        protected final synchronized void run(Callable<Boolean> Base, Callable<String> OnError)
        {
            currentAction = onThread(() -> {
                boolean output = Base.call();
                onSuccessEnd();
                return output;
            }, () -> {
                String output = OnError.call();
                currentStopAndResetStateAndUIWait();
                return output;
            });
        }

        private synchronized void stopCurrent()
        {
            if(currentAction!=null && !currentAction.isDisposed())
                currentAction.dispose();
        }
    }
}
