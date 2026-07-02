package com.wintopic.codexpanel;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebChromeClient.FileChooserParams;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PREFS = "codexpanel";
    private static final String KEY_RELAY = "relay_url";
    private static final String KEY_PATH = "remote_path";
    private static final String KEY_SECRET = "remote_key";
    private static final int FILE_CHOOSER_REQUEST = 1201;
    private static final int WEB_PERMISSION_REQUEST = 1202;

    private SharedPreferences prefs;
    private WebView webView;
    private TextView statusText;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> fileChooserCallback;
    private PermissionRequest pendingPermissionRequest;
    private EditText relayInput;
    private EditText pathInput;
    private EditText keyInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        configureWindow();
        handleIncomingUrl(getIntent());
        if (hasConfig()) {
            showBrowser();
        } else {
            showSetup();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (handleIncomingUrl(intent)) {
            showBrowser();
        }
    }

    private void configureWindow() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.rgb(248, 251, 255));
            window.setNavigationBarColor(Color.rgb(248, 251, 255));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private boolean hasConfig() {
        return !pref(KEY_RELAY).isEmpty() && !pref(KEY_PATH).isEmpty() && !pref(KEY_SECRET).isEmpty();
    }

    private String pref(String key) {
        return prefs.getString(key, "").trim();
    }

    private boolean handleIncomingUrl(Intent intent) {
        if (intent == null || intent.getData() == null) return false;
        return saveRemoteUrl(intent.getData().toString());
    }

    private void showSetup() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(248, 251, 255));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(22));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("CodexPanel");
        title.setTextColor(Color.rgb(7, 11, 24));
        title.setTextSize(26);
        title.setTypeface(null, 1);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("连接 Cloudflare 远控入口，配置会保存在本机 App 私有数据中。");
        subtitle.setTextColor(Color.rgb(96, 112, 133));
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(8), 0, dp(18));
        root.addView(subtitle, matchWrap());

        relayInput = input("Cloudflare 服务地址或完整远控入口");
        relayInput.setText(pref(KEY_RELAY));
        root.addView(label("Cloudflare 服务地址"), matchWrap());
        root.addView(relayInput, fieldParams());

        pathInput = input("例如 /remote/win/");
        pathInput.setText(pref(KEY_PATH).isEmpty() ? "/remote/win/" : pref(KEY_PATH));
        root.addView(label("设备路径"), matchWrap());
        root.addView(pathInput, fieldParams());

        keyInput = input("远控密钥");
        keyInput.setText(pref(KEY_SECRET));
        root.addView(label("远控密钥"), matchWrap());
        root.addView(keyInput, fieldParams());

        Button parseButton = actionButton("从剪贴板读取完整远控入口");
        parseButton.setOnClickListener(v -> pasteRemoteUrl());
        root.addView(parseButton, buttonParams());

        Button saveButton = primaryButton("保存并打开");
        saveButton.setOnClickListener(v -> {
            if (saveFields()) showBrowser();
        });
        root.addView(saveButton, buttonParams());

        TextView hint = new TextView(this);
        hint.setText("示例：https://codexpanel-wan.pages.dev/remote/win/?token=******");
        hint.setTextColor(Color.rgb(101, 112, 133));
        hint.setTextSize(12);
        hint.setPadding(0, dp(12), 0, 0);
        root.addView(hint, matchWrap());

        setContentView(scroll);
    }

    private void showBrowser() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(248, 251, 255));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(10), dp(8), dp(10), dp(8));
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView title = new TextView(this);
        title.setText("CodexPanel");
        title.setTextColor(Color.rgb(7, 11, 24));
        title.setTextSize(18);
        title.setTypeface(null, 1);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        statusText = new TextView(this);
        statusText.setText("准备加载");
        statusText.setTextColor(Color.rgb(101, 112, 133));
        statusText.setTextSize(12);
        statusText.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        toolbar.addView(statusText, new LinearLayout.LayoutParams(0, -1, 1));

        Button refresh = miniButton("刷新");
        refresh.setOnClickListener(v -> {
            if (webView != null) webView.reload();
        });
        toolbar.addView(refresh, new LinearLayout.LayoutParams(dp(64), dp(36)));

        Button settings = miniButton("设置");
        settings.setOnClickListener(v -> showSetup());
        toolbar.addView(settings, new LinearLayout.LayoutParams(dp(64), dp(36)));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        root.addView(progressBar, new LinearLayout.LayoutParams(-1, dp(3)));

        webView = new WebView(this);
        configureWebView(webView);
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        webView.loadUrl(buildRemoteUrl());
    }

    private void configureWebView(WebView view) {
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUserAgentString(settings.getUserAgentString() + " CodexPanelAndroid/1.0.0");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            CookieManager.getInstance().setAcceptThirdPartyCookies(view, true);
        }
        CookieManager.getInstance().setAcceptCookie(true);

        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                return handleUrl(view, uri);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(view, Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                statusText.setText(Uri.parse(url).getHost());
            }
        });

        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                progressBar.setProgress(progress);
                progressBar.setVisibility(progress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = callback;
                Intent intent = params.createIntent();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                } catch (Exception error) {
                    fileChooserCallback = null;
                    toast("无法打开文件选择器");
                    return false;
                }
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                pendingPermissionRequest = request;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, WEB_PERMISSION_REQUEST);
                } else {
                    request.grant(request.getResources());
                }
            }
        });
    }

    private boolean handleUrl(WebView view, Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ("http".equals(scheme) || "https".equals(scheme)) {
            view.loadUrl(uri.toString());
            return true;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ignored) {
            toast("无法打开链接");
        }
        return true;
    }

    private boolean saveFields() {
        String relay = cleanUrl(relayInput.getText().toString());
        String path = normalizePath(pathInput.getText().toString());
        String key = keyInput.getText().toString().trim();
        if (relay.contains("/remote/") || relay.contains("?token=")) {
            RemoteConfig parsed = parseRemoteUrl(relay);
            if (parsed != null) {
                relay = parsed.relay;
                path = parsed.path;
                if (!parsed.key.isEmpty()) key = parsed.key;
            }
        }
        if (relay.isEmpty() || path.isEmpty() || key.isEmpty()) {
            toast("请填写 Cloudflare 服务地址、设备路径和远控密钥");
            return false;
        }
        prefs.edit()
                .putString(KEY_RELAY, relay)
                .putString(KEY_PATH, path)
                .putString(KEY_SECRET, key)
                .apply();
        return true;
    }

    private boolean saveRemoteUrl(String value) {
        RemoteConfig parsed = parseRemoteUrl(value);
        if (parsed == null || parsed.relay.isEmpty() || parsed.path.isEmpty() || parsed.key.isEmpty()) return false;
        prefs.edit()
                .putString(KEY_RELAY, parsed.relay)
                .putString(KEY_PATH, parsed.path)
                .putString(KEY_SECRET, parsed.key)
                .apply();
        return true;
    }

    private void pasteRemoteUrl() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            toast("剪贴板为空");
            return;
        }
        ClipData data = clipboard.getPrimaryClip();
        CharSequence text = data != null && data.getItemCount() > 0 ? data.getItemAt(0).coerceToText(this) : "";
        RemoteConfig parsed = parseRemoteUrl(String.valueOf(text));
        if (parsed == null) {
            toast("剪贴板里没有可识别的远控入口");
            return;
        }
        relayInput.setText(parsed.relay);
        pathInput.setText(parsed.path);
        keyInput.setText(parsed.key);
    }

    private String buildRemoteUrl() {
        String relay = pref(KEY_RELAY).replaceAll("/+$", "");
        String path = normalizePath(pref(KEY_PATH));
        String key = pref(KEY_SECRET);
        return relay + path + "?token=" + urlEncode(key);
    }

    private RemoteConfig parseRemoteUrl(String value) {
        try {
            Uri uri = Uri.parse(String.valueOf(value).trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) return null;
            String path = uri.getEncodedPath();
            String key = uri.getQueryParameter("token");
            if (path == null || !path.contains("/remote/")) return null;
            String relay = scheme + "://" + host;
            if (uri.getPort() > 0) relay += ":" + uri.getPort();
            return new RemoteConfig(cleanUrl(relay), normalizePath(path), key == null ? "" : key);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizePath(String value) {
        String path = String.valueOf(value == null ? "" : value).trim();
        if (path.isEmpty()) return "/remote/win/";
        if (!path.startsWith("/")) path = "/" + path;
        if (!path.endsWith("/")) path = path + "/";
        return path;
    }

    private String cleanUrl(String value) {
        return String.valueOf(value == null ? "" : value).trim().replaceAll("/+$", "");
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception ignored) {
            return value;
        }
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setTextSize(15);
        input.setPadding(dp(12), 0, dp(12), 0);
        return input;
    }

    private TextView label(String value) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextColor(Color.rgb(7, 11, 24));
        label.setTextSize(13);
        label.setTypeface(null, 1);
        label.setPadding(0, dp(10), 0, dp(4));
        return label;
    }

    private Button primaryButton(String text) {
        Button button = actionButton(text);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.rgb(36, 91, 255));
        return button;
    }

    private Button actionButton(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(14);
        return button;
    }

    private Button miniButton(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(12);
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.setMargins(0, 0, 0, dp(4));
        return params;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.setMargins(0, dp(10), 0, 0);
        return params;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || fileChooserCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                ClipData clipData = data.getClipData();
                ArrayList<Uri> uris = new ArrayList<>();
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    uris.add(clipData.getItemAt(i).getUri());
                }
                result = uris.toArray(new Uri[0]);
            } else if (data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
        }
        fileChooserCallback.onReceiveValue(result);
        fileChooserCallback = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != WEB_PERMISSION_REQUEST || pendingPermissionRequest == null) return;
        boolean granted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) granted = false;
        }
        if (granted) {
            pendingPermissionRequest.grant(pendingPermissionRequest.getResources());
        } else {
            pendingPermissionRequest.deny();
        }
        pendingPermissionRequest = null;
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private static class RemoteConfig {
        final String relay;
        final String path;
        final String key;

        RemoteConfig(String relay, String path, String key) {
            this.relay = relay;
            this.path = path;
            this.key = key;
        }
    }
}
