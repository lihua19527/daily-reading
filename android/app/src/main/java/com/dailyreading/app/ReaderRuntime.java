package com.dailyreading.app;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;

final class ReaderRuntime {
    static final String PORT = "18787";
    static final String ORIGIN = "http://127.0.0.1:" + PORT;
    private static CompletableFuture<String> startup;

    static synchronized CompletableFuture<String> start(Context context) {
        if (startup == null) {
            Context app = context.getApplicationContext();
            startup = new CompletableFuture<>();
            new Thread(() -> launch(app, startup), "reader-startup").start();
        }
        return startup;
    }

    private static void launch(Context context, CompletableFuture<String> result) {
        try {
            File runtimeDir = new File(context.getCacheDir(), "runtime");
            File source = new File(runtimeDir, "reader");
            copyAssets(context.getAssets(), "reader", source);

            File ready = new File(runtimeDir, "ready");
            if (ready.exists() && !ready.delete()) throw new IOException("无法初始化运行目录");
            String session = sessionId();
            System.loadLibrary("dailyreader");
            Thread node = new Thread(() -> {
                try {
                    int code = startNode(new File(source, "launch.js").getAbsolutePath(),
                            new File(context.getNoBackupFilesDir(), "data").getAbsolutePath(),
                            ready.getAbsolutePath(), session, PORT, TimeZone.getDefault().getID());
                    Log.e("DailyReading", "Node exited with code " + code);
                    result.completeExceptionally(new IOException("阅读服务已停止（" + code + "）"));
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                }
            }, "reader-node");
            node.start();

            long deadline = SystemClock.elapsedRealtime() + 25000;
            while (!result.isDone() && SystemClock.elapsedRealtime() < deadline) {
                if (ready.length() > 0) {
                    try (FileInputStream in = new FileInputStream(ready)) {
                        byte[] bytes = new byte[4096];
                        int count = in.read(bytes);
                        if (count > 0) {
                            String status = new String(bytes, 0, count, StandardCharsets.UTF_8);
                            if (!status.equals("ready")) throw new IOException(status);
                            result.complete(session);
                            return;
                        }
                    }
                }
                SystemClock.sleep(100);
            }
            result.completeExceptionally(new IOException("阅读服务启动超时"));
        } catch (Throwable error) {
            result.completeExceptionally(error);
        }
    }

    private static String sessionId() {
        byte[] random = new byte[16];
        new SecureRandom().nextBytes(random);
        StringBuilder key = new StringBuilder(32);
        for (byte value : random) key.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return key.toString();
    }

    private static void copyAssets(AssetManager assets, String path, File target) throws IOException {
        String[] entries = assets.list(path);
        if (entries != null && entries.length > 0) {
            if (!target.isDirectory() && !target.mkdirs()) throw new IOException("无法创建运行目录");
            for (String name : entries) copyAssets(assets, path + "/" + name, new File(target, name));
            return;
        }
        try (InputStream in = assets.open(path); FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) >= 0) out.write(buffer, 0, count);
        }
    }

    private static native int startNode(String... arguments);
}
