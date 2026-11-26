package com.dailyreading.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PICK_IMAGES = 1;
    private WebView web;
    private ValueCallback<Uri[]> pickedImages;
    private final ExecutorService ocrWorker = Executors.newSingleThreadExecutor();
    private ScreenshotRecognition ocr;
    private ReaderSpeech speech;
    private TextView startupMessage;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.rgb(250, 248, 242));
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
            frame.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                Insets bars = windowInsets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                Insets ime = windowInsets.getInsets(WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
                return WindowInsets.CONSUMED;
            });
        }
        setContentView(frame);

        startupMessage = new TextView(this);
        startupMessage.setGravity(Gravity.CENTER);
        startupMessage.setPadding(32, 24, 32, 24);
        startupMessage.setText("正在启动每日阅读…");
        frame.addView(startupMessage, new FrameLayout.LayoutParams(-1, -1));
        ReaderRuntime.start(this).whenComplete((session, error) -> runOnUiThread(() -> {
            if (isDestroyed() || isFinishing()) return;
            if (error != null) {
                Log.e("DailyReading", "Could not start reader", error);
                startupMessage.setText("无法启动每日阅读\n" + error.getMessage());
                return;
            }
            openReader(frame, session);
        }));
    }

    private void openReader(FrameLayout frame, String session) {
        try {
            web = new WebView(this);
        } catch (RuntimeException error) {
            startupMessage.setText("请启用或更新 Android System WebView 后重试");
            return;
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            startupMessage.setText("请更新 Android System WebView 后重新打开每日阅读");
            return;
        }

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (isReader(url)) return false;
                if (request.isForMainFrame() && request.hasGesture() && "https".equals(url.getScheme())) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, url));
                    } catch (ActivityNotFoundException error) {
                        Toast.makeText(MainActivity.this, "找不到浏览器", Toast.LENGTH_SHORT).show();
                    }
                }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pickedImages != null) pickedImages.onReceiveValue(null);
                pickedImages = callback;
                Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("image/*")
                        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(picker, PICK_IMAGES);
                } catch (ActivityNotFoundException error) {
                    finishPicking(null);
                    Toast.makeText(MainActivity.this, "找不到图片选择器", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
        speech = new ReaderSpeech(this);
        WebViewCompat.addWebMessageListener(web, "DailyReadingNative", Collections.singleton(ReaderRuntime.ORIGIN),
                (view, message, source, isMainFrame, reply) -> {
                    if (isMainFrame && isReader(source)) receive(message.getData(), reply);
                });

        CookieManager.getInstance().setCookie(ReaderRuntime.ORIGIN,
                "drSession=" + session + "; Path=/; HttpOnly; SameSite=Strict", accepted -> {
                    if (isDestroyed()) return;
                    if (!accepted) {
                        startupMessage.setText("无法打开每日阅读，请重启应用后再试");
                        return;
                    }
                    startupMessage.setVisibility(View.GONE);
                    frame.addView(web, new FrameLayout.LayoutParams(-1, -1));
                    web.loadUrl(ReaderRuntime.ORIGIN + "/");
                });
    }

    private static boolean isReader(Uri url) {
        return url != null && "http".equals(url.getScheme()) && "127.0.0.1".equals(url.getHost())
                && url.getPort() == Integer.parseInt(ReaderRuntime.PORT);
    }

    private void receive(String text, JavaScriptReplyProxy reply) {
        if (text == null) return;
        try {
            JSONObject message = new JSONObject(text);
            int id = message.getInt("id");
            switch (message.getString("action")) {
                case "ocr":
                    String name = message.getString("name");
                    String dataUrl = message.getString("dataUrl");
                    ocrWorker.execute(() -> {
                        try {
                            if (ocr == null) ocr = new ScreenshotRecognition();
                            JSONObject value = ocr.recognize(name, dataUrl);
                            runOnUiThread(() -> respond(reply, id, "end", value, null));
                        } catch (Exception error) {
                            Log.w("DailyReading", "Screenshot recognition failed", error);
                            runOnUiThread(() -> respond(reply, id, "error", null,
                                    error instanceof java.io.IOException ? error.getMessage() : "图片识别失败，请试试 PNG 或 JPG 截图"));
                        }
                    });
                    break;
                case "speak":
                    speech.speak(message.getString("text"), message.optBoolean("queue"), message.optDouble("rate", 0.92),
                            (event, error) -> runOnUiThread(() -> respond(reply, id, event, null, error)));
                    break;
                case "stopSpeech":
                    speech.stop();
                    break;
                default:
                    respond(reply, id, "error", null, "不支持的操作");
            }
        } catch (JSONException error) {
            Log.w("DailyReading", "Invalid app message", error);
        }
    }

    private void respond(JavaScriptReplyProxy reply, int id, String event, JSONObject value, String error) {
        if (isDestroyed()) return;
        try {
            reply.postMessage(new JSONObject().put("id", id).put("event", event).put("value", value)
                    .put("error", error).toString());
        } catch (JSONException | IllegalStateException ignored) {
            // The caller may have navigated to a different page.
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_IMAGES) return;
        if (result != RESULT_OK || data == null) {
            finishPicking(null);
            return;
        }
        ClipData selection = data.getClipData();
        if (selection == null) {
            Uri image = data.getData();
            finishPicking(image == null ? null : new Uri[]{image});
            return;
        }
        int count = Math.min(12, selection.getItemCount());
        Uri[] images = new Uri[count];
        for (int i = 0; i < count; i++) images[i] = selection.getItemAt(i).getUri();
        finishPicking(images);
        if (count < selection.getItemCount()) {
            Toast.makeText(this, "已选入前 12 张截图", Toast.LENGTH_SHORT).show();
        }
    }

    private void finishPicking(Uri[] images) {
        if (pickedImages == null) return;
        ValueCallback<Uri[]> callback = pickedImages;
        pickedImages = null;
        callback.onReceiveValue(images);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        finishPicking(null);
        if (speech != null) speech.close();
        ocrWorker.execute(() -> {
            if (ocr != null) ocr.close();
        });
        ocrWorker.shutdown();
        if (web != null) {
            if (web.getParent() instanceof FrameLayout) ((FrameLayout) web.getParent()).removeView(web);
            web.destroy();
        }
        super.onDestroy();
    }
}
