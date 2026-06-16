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

import io.reactivex.rxjava3.core.Scheduler;
import ssl.SiteLoader;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static app.services.BaseServer.restart;
import static app.tools.DisposableTools.addTask;

public class JsonDownloader {

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Context context;
    private final Scheduler ioScheduler;
    private Disposable jsonLoader;

    public JsonDownloader(Context context,Scheduler ioScheduler) {
        this.context = context;
        this.ioScheduler = ioScheduler;
    }

    private Single<String> downloadSingle(String fullUrl, String directory, String jsonFileName) {
        return Single.create(emitter -> {
            AtomicBoolean cancelled = new AtomicBoolean(false);

            JsonDownloader.DownloadCallback callback = new JsonDownloader.DownloadCallback() {
                @Override
                public void onSuccess(String filePath) {
                    if (cancelled.get() || emitter.isDisposed()) return;
                    emitter.onSuccess(filePath);
                }

                @Override
                public void onError(Throwable throwable) {
                    if (cancelled.get() || emitter.isDisposed()) return;
                    emitter.onError(throwable);
                }
            };

            downloadJsonFile(fullUrl, directory, jsonFileName, callback);

            emitter.setCancellable(() -> cancelled.set(true));
        });
    }

    public void downloadJsonFilesMultipleAndRestartAppRx(List<String> jsonNames, String url, String directory) {
        final int totalDownloads = jsonNames.size();
        final int timeoutSeconds = 6; // tune as needed
        final int maxRetries = 3;
        final int maxConcurrency = Math.min(2, Math.max(1, totalDownloads)); // set 1 for sequential
        final long perDownloadStartDelayMs = 200;

        SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[Starting " + totalDownloads + " downloads]");

        Observable<String> downloads = Observable.fromIterable(jsonNames)
                .flatMap(isoLanguage -> {
                            final String jsonFileName = isoLanguage + ".json";
                            final String fullUrl = url + directory + "/" + jsonFileName;
                            SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[Scheduling: " + fullUrl + "]");

                            return downloadSingle(fullUrl, directory, jsonFileName)
                                    .delaySubscription(perDownloadStartDelayMs, TimeUnit.MILLISECONDS, ioScheduler)
                                    .timeout(timeoutSeconds, TimeUnit.SECONDS)
                                    .retryWhen(errors -> {
                                        final AtomicInteger retryCounter = new AtomicInteger(0);
                                        // 'errors' is a Flowable<Throwable> for Single.retryWhen in RxJava3
                                        return errors.flatMap(err -> {
                                            int attempt = retryCounter.incrementAndGet();
                                            if (attempt > maxRetries) {
                                                return Flowable.error(new RuntimeException("Retries exhausted for " + jsonFileName + ": " + String.valueOf(err.getMessage())));
                                            }
                                            long backoffSec = (long) Math.pow(2.0, attempt); // 2,4,8...
                                            long jitterMs = (long) (Math.random() * 500.0);
                                            long waitMs = backoffSec * 1000L + jitterMs;
                                            SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions)
                                                    + "[Retry " + attempt + " for " + jsonFileName + " after " + waitMs + "ms]");
                                            return Flowable.timer(waitMs, TimeUnit.MILLISECONDS);
                                        });
                                    })
                                    .map(path -> "SUCCESS:" + path)
                                    .onErrorReturn(throwable -> "ERROR:" + jsonFileName + ":" + throwable.getMessage())
                                    .subscribeOn(ioScheduler)
                                    .toObservable();
                        },
                        /* delayErrors */ false,
                        /* maxConcurrency */ maxConcurrency
                );

        disposables.add(
                downloads
                        .toList()
                        .subscribeOn(ioScheduler)
                        .subscribe(results -> {
                            int completed = 0;
                            for (String r : results) {
                                completed++;
                                if (r.startsWith("SUCCESS:")) {
                                    String path = r.substring("SUCCESS:".length());
                                    SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[Download " + completed + "/" + totalDownloads + " completed: " + path + "]");
                                } else {
                                    SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[Download " + completed + "/" + totalDownloads + " failed: " + r + "]");
                                }
                            }

                            SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[All downloads completed (with successes/errors/timeouts), restarting app]");
                            restart();
                        }, throwable -> {
                            SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[Batch aggregator error: " + throwable.getMessage() + "]");
                            restart();
                        })
        );
    }

    public void downloadJsonFile(String url, String directory, String fileName, DownloadCallback callback) {
        SiteLoader loader = new SiteLoader(url, new SiteLoader.Listeners() {
            @Override
            public void onRequestIntercepted(String interceptedUrl, String method) { }

            @Override
            public void onError(String errorMessage) {
                if (callback != null) callback.onError(new IOException(errorMessage));
            }

            @Override
            public void onMainSiteLoaded(String htmlContent) {
                if (jsonLoader != null && !jsonLoader.isDisposed()) jsonLoader.dispose();

                jsonLoader = addTask(() -> {
                    File dir = new File(context.getFilesDir(), directory);
                    try {
                        if (!dir.exists() && !dir.mkdirs()) {
                            throw new IOException("Failed to create directory: " + dir.getAbsolutePath());
                        }

                        File tmpFile = new File(dir, fileName + ".tmp");
                        File outFile = new File(dir, fileName);

                        try (BufferedSink sink = Okio.buffer(Okio.sink(tmpFile))) {
                            sink.writeUtf8(htmlContent);
                            sink.flush();
                        }

                        if (!tmpFile.exists() || tmpFile.length() == 0) {
                            throw new IOException("Downloaded file is empty");
                        }

                        boolean looksLikeJson = htmlContent != null && (htmlContent.trim().startsWith("{") || htmlContent.trim().startsWith("["));
                        if (!looksLikeJson) {
                            throw new IOException("Downloaded content does not appear to be JSON");
                        }

                        if (outFile.exists() && !outFile.delete()) {
                            throw new IOException("Failed to replace existing file: " + outFile.getAbsolutePath());
                        }
                        if (!tmpFile.renameTo(outFile)) {
                            try (BufferedSink sink = Okio.buffer(Okio.sink(outFile));
                                 Source src = Okio.source(tmpFile)) {
                                sink.writeAll(src);
                            }
                            tmpFile.delete();
                        }

                        if (callback != null) {
                            callback.onSuccess(outFile.getAbsolutePath());
                        }
                    } catch (Exception e) {
                        if (callback != null) callback.onError(e);
                    }
                    return true;
                }, () -> "Json-Downloader-Error", ioScheduler);
            }

            @Override
            public void onLog(String logMessage) { }
        });

        try {
            loader.startCapture();
        } catch (Exception e) {
            if (callback != null) callback.onError(e);
        }
    }

    public void dispose() {
        disposables.dispose();
    }

    public interface DownloadCallback {
        void onSuccess(String filePath);
        void onError(Throwable throwable);
    }
}
