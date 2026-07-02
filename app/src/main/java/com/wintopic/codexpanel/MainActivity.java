package com.wintopic.codexpanel;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PREFS = "codexpanel";
    private static final String KEY_RELAY = "relay_url";
    private static final String KEY_PATH = "remote_path";
    private static final String KEY_SECRET = "remote_key";
    private static final int PICK_IMAGES_REQUEST = 2101;

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Attachment> attachments = new ArrayList<>();
    private final List<ThreadItem> threads = new ArrayList<>();

    private SharedPreferences prefs;
    private EditText relayInput;
    private EditText pathInput;
    private EditText keyInput;
    private TextView connectionText;
    private TextView threadText;
    private TextView detailText;
    private TextView attachmentText;
    private LinearLayout messageList;
    private ScrollView messageScroll;
    private TextView activeAssistantBubble;
    private EditText composerInput;
    private Button sendButton;
    private Button guideButton;
    private Button stopButton;
    private ProgressBar progressBar;

    private String selectedThreadId = "";
    private JSONObject activeWatch;
    private Runnable pollRunnable;
    private boolean busy;
    private boolean activeRun;
    private boolean keyboardOpen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        configureWindow();
        handleIncomingUrl(getIntent());
        if (hasConfig()) showPanel();
        else showSetup();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (handleIncomingUrl(intent)) showPanel();
    }

    private void configureWindow() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.rgb(247, 249, 250));
            window.setNavigationBarColor(Color.rgb(247, 249, 250));
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
        stopPolling();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(247, 249, 250));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(20));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = text("CodexPanel", 28, Color.rgb(14, 18, 22), true);
        root.addView(title, matchWrap());

        TextView desc = text("原生远控客户端。配置只保存在本机 App 私有数据中。", 14, Color.rgb(93, 101, 111), false);
        desc.setPadding(0, dp(8), 0, dp(18));
        root.addView(desc, matchWrap());

        relayInput = input("https://codexpanel-wan.pages.dev 或完整远控入口");
        relayInput.setText(pref(KEY_RELAY));
        root.addView(label("Cloudflare 服务地址"), matchWrap());
        root.addView(relayInput, fieldParams());

        pathInput = input("/remote/win/");
        pathInput.setText(pref(KEY_PATH).isEmpty() ? "/remote/win/" : pref(KEY_PATH));
        root.addView(label("设备路径"), matchWrap());
        root.addView(pathInput, fieldParams());

        keyInput = input("远控密钥");
        keyInput.setText(pref(KEY_SECRET));
        root.addView(label("远控密钥"), matchWrap());
        root.addView(keyInput, fieldParams());

        Button paste = secondaryButton("从剪贴板读取完整入口");
        paste.setOnClickListener(v -> pasteRemoteUrl());
        root.addView(paste, buttonParams());

        Button save = primaryButton("保存并连接");
        save.setOnClickListener(v -> {
            if (saveFields()) showPanel();
        });
        root.addView(save, buttonParams());

        TextView hint = text("示例：https://codexpanel-wan.pages.dev/remote/win/?token=******", 12, Color.rgb(104, 112, 122), false);
        hint.setPadding(0, dp(12), 0, 0);
        root.addView(hint, matchWrap());

        setContentView(scroll);
    }

    private void showPanel() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 249, 250));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(8), dp(12), dp(8));
        top.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(58)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        top.addView(titleBox, new LinearLayout.LayoutParams(0, -1, 1));
        titleBox.addView(text("CodexPanel", 18, Color.rgb(12, 16, 20), true), new LinearLayout.LayoutParams(-1, 0, 1));
        connectionText = text("连接中...", 11, Color.rgb(94, 104, 115), false);
        titleBox.addView(connectionText, new LinearLayout.LayoutParams(-1, 0, 1));

        top.addView(toolbarButton("线程", v -> loadThreads(true)), new LinearLayout.LayoutParams(dp(56), dp(38)));
        top.addView(toolbarButton("新建", v -> createNewThread()), new LinearLayout.LayoutParams(dp(56), dp(38)));
        top.addView(toolbarButton("诊断", v -> showDiagnostics()), new LinearLayout.LayoutParams(dp(56), dp(38)));
        top.addView(toolbarButton("设置", v -> showSetup()), new LinearLayout.LayoutParams(dp(56), dp(38)));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, new LinearLayout.LayoutParams(-1, dp(3)));

        LinearLayout state = card();
        state.setPadding(dp(12), dp(10), dp(12), dp(10));
        threadText = text("未选择线程", 14, Color.rgb(18, 23, 28), true);
        detailText = text("准备同步远端状态", 12, Color.rgb(93, 101, 111), false);
        state.addView(threadText, matchWrap());
        state.addView(detailText, matchWrap());
        root.addView(state, insetParams(12, 8, 12, 6));

        messageScroll = new ScrollView(this);
        messageScroll.setFillViewport(true);
        messageList = new LinearLayout(this);
        messageList.setOrientation(LinearLayout.VERTICAL);
        messageList.setPadding(dp(12), dp(8), dp(12), dp(8));
        messageScroll.addView(messageList, new ScrollView.LayoutParams(-1, -2));
        root.addView(messageScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        root.addView(quickActions(), new LinearLayout.LayoutParams(-1, dp(46)));
        root.addView(composer(), new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        installKeyboardBottomFix(root);
        renderEmpty();
        checkHealth();
        loadConfig();
        loadThreads(false);
    }

    private View quickActions() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(4), dp(10), dp(4));
        scroll.addView(row, new HorizontalScrollView.LayoutParams(-2, -1));
        row.addView(chip("计划", v -> sendSlash("/plan")));
        row.addView(chip("评审", v -> sendSlash("/review")));
        row.addView(chip("目标", v -> sendSlash("/goal")));
        row.addView(chip("压缩", v -> sendText("/compact", true, "queue")));
        row.addView(chip("模型", v -> chooseModel()));
        row.addView(chip("推理", v -> chooseReasoning()));
        row.addView(chip("线程操作", v -> chooseThreadAction()));
        row.addView(chip("App命令", v -> chooseAppCommand()));
        return scroll;
    }

    private View composer() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(6), dp(10), dp(10));
        box.setBackgroundColor(Color.rgb(247, 249, 250));

        attachmentText = text("", 12, Color.rgb(93, 101, 111), false);
        attachmentText.setVisibility(View.GONE);
        box.addView(attachmentText, matchWrap());

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.BOTTOM);
        row.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(row, matchWrap());

        Button attach = toolbarButton("+", v -> pickImages());
        row.addView(attach, new LinearLayout.LayoutParams(dp(42), dp(46)));

        composerInput = new EditText(this);
        composerInput.setHint("询问 Codex");
        composerInput.setMinLines(1);
        composerInput.setMaxLines(5);
        composerInput.setTextSize(15);
        composerInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        composerInput.setSingleLine(false);
        composerInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        composerInput.setBackground(round(Color.WHITE, Color.rgb(218, 224, 231), 18));
        composerInput.setPadding(dp(12), dp(8), dp(12), dp(8));
        composerInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendComposerQueue();
                return true;
            }
            return false;
        });
        composerInput.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_ENTER || event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (event.isCtrlPressed()) {
                sendComposerGuide();
                return true;
            }
            if (!event.isShiftPressed()) {
                sendComposerQueue();
                return true;
            }
            return false;
        });
        composerInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) main.postDelayed(this::scrollBottom, 120);
        });
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, -2, 1);
        inputParams.setMargins(dp(8), 0, dp(8), 0);
        row.addView(composerInput, inputParams);

        stopButton = dangerButton("停");
        stopButton.setOnClickListener(v -> stopCodex());
        row.addView(stopButton, new LinearLayout.LayoutParams(dp(46), dp(46)));

        guideButton = secondaryButton("导");
        guideButton.setOnClickListener(v -> sendComposerGuide());
        row.addView(guideButton, new LinearLayout.LayoutParams(dp(46), dp(46)));

        sendButton = primaryButton("发");
        sendButton.setOnClickListener(v -> sendComposerQueue());
        row.addView(sendButton, new LinearLayout.LayoutParams(dp(46), dp(46)));
        return box;
    }

    private void loadConfig() {
        request("GET", "/codex/config", null, 8000, new JsonCallback() {
            public void done(JSONObject data) {
                JSONArray models = data.optJSONArray("modelOptions");
                if (models != null && models.length() > 0) prefs.edit().putString("model_options", models.toString()).apply();
            }
        });
    }

    private void checkHealth() {
        request("GET", "/codex/health", null, 8000, new JsonCallback() {
            public void done(JSONObject data) {
                connectionText.setText("已连接 " + data.optString("host", hostLabel()));
            }
        });
    }

    private void loadThreads(boolean showPicker) {
        request("GET", "/codex/threads?limit=120", null, 12000, new JsonCallback() {
            public void done(JSONObject data) {
                threads.clear();
                JSONArray rows = data.optJSONArray("threads");
                if (rows != null) {
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject item = rows.optJSONObject(i);
                        if (item != null) threads.add(ThreadItem.from(item));
                    }
                }
                if (threads.isEmpty()) {
                    selectedThreadId = "";
                    renderEmpty();
                    if (showPicker) toast("没有读取到线程");
                    return;
                }
                if (selectedThreadId.isEmpty()) selectedThreadId = threads.get(0).id;
                ThreadItem selected = findThread(selectedThreadId);
                if (selected != null) renderThreadHeader(selected);
                if (showPicker) showThreadPicker();
                else loadHistory();
            }
        });
    }

    private void showThreadPicker() {
        String[] names = new String[threads.size()];
        for (int i = 0; i < threads.size(); i++) names[i] = threads.get(i).display();
        new AlertDialog.Builder(this)
                .setTitle("选择线程")
                .setItems(names, (d, which) -> selectThread(threads.get(which)))
                .show();
    }

    private void selectThread(ThreadItem item) {
        selectedThreadId = item.id;
        renderThreadHeader(item);
        JSONObject body = new JSONObject();
        put(body, "threadId", selectedThreadId);
        request("POST", "/codex/select", body, 18000, new JsonCallback() {
            public void done(JSONObject data) {
                loadHistory();
            }
        });
    }

    private void loadHistory() {
        if (selectedThreadId.isEmpty()) return;
        request("GET", "/codex/history?thread=" + enc(selectedThreadId) + "&limit=80", null, 30000, new JsonCallback() {
            public void done(JSONObject data) {
                renderMessages(data.optJSONArray("messages"));
                refreshStatus(false);
            }
        });
    }

    private void refreshStatus(boolean fromPoll) {
        String path = "/codex/status?thread=" + enc(selectedThreadId);
        if (activeWatch != null) {
            path += "&since=" + enc(activeWatch.optString("since"));
            path += "&session=" + enc(activeWatch.optString("sessionFile"));
            path += "&expectNewThread=" + (activeWatch.optBoolean("expectNewThread") ? "1" : "0");
            path += "&excludeThread=" + enc(activeWatch.optString("excludeThreadId"));
            path += "&cwd=" + enc(activeWatch.optString("cwd"));
        }
        request("GET", path, null, 20000, new JsonCallback() {
            public void done(JSONObject data) {
                String status = data.optString("status", "idle");
                JSONObject model = data.optJSONObject("model");
                JSONObject reasoning = data.optJSONObject("reasoningMode");
                JSONObject context = data.optJSONObject("context");
                StringBuilder detail = new StringBuilder();
                if (model != null) detail.append("模型 ").append(model.optString("label", model.optString("id", "-")));
                if (reasoning != null) detail.append(detail.length() > 0 ? " · " : "").append("推理 ").append(reasoning.optString("label", "-"));
                if (context != null && context.optInt("percent") > 0) detail.append(detail.length() > 0 ? " · " : "").append("上下文 ").append(context.optInt("percent")).append("%");
                if (detail.length() == 0) detail.append(statusText(status));
                detailText.setText(detail.toString());

                if (fromPoll) upsertAssistant(data);
                boolean active = data.optBoolean("active") || "running".equals(status) || "waiting".equals(status);
                activeRun = active;
                if (active) schedulePoll();
                else {
                    stopPolling();
                    if (fromPoll) loadHistory();
                }
            }
        });
    }

    private void sendComposerQueue() {
        sendText(composerInput.getText().toString(), false, "queue");
    }

    private void sendComposerGuide() {
        sendText(composerInput.getText().toString(), false, "guide");
    }

    private void sendText(String text, boolean keepInput, String submitMode) {
        String value = String.valueOf(text == null ? "" : text);
        if (value.trim().isEmpty() && attachments.isEmpty()) return;
        hideKeyboard();
        boolean guide = "guide".equals(submitMode);
        boolean queuedBehindActive = activeRun && !guide;
        JSONObject body = new JSONObject();
        put(body, "clientRequestId", "android-" + System.currentTimeMillis() + "-" + UUID.randomUUID());
        put(body, "text", value);
        put(body, "target", "codex");
        put(body, "threadId", selectedThreadId);
        put(body, "assumeThreadSynced", true);
        put(body, "submitMode", guide ? "guide" : "queue");
        JSONArray files = new JSONArray();
        for (Attachment attachment : attachments) files.put(attachment.toJson());
        put(body, "attachments", files);

        appendMessage("user", (guide ? "你 · 引导" : activeRun ? "你 · 排队" : "你") + (attachments.isEmpty() ? "" : " · " + attachments.size() + " 张图片"), value.trim().isEmpty() ? " " : value);
        if (guide) appendMessage("assistant", "Codex · 引导中", "正在作为当前回复的引导发送...");
        else {
            TextView bubble = appendMessage("assistant", activeRun ? "Codex · 已排队" : "Codex · 等待中", activeRun ? "已加入队列，Codex 会在当前回复后继续处理。" : "已发送，等待 Codex 回复...");
            if (!queuedBehindActive) activeAssistantBubble = bubble;
        }
        if (!keepInput) composerInput.setText("");
        attachments.clear();
        renderAttachments();

        request("POST", "/send", body, 60000, new JsonCallback() {
            public void done(JSONObject data) {
                toast(data.optString("message", "已发送"));
                if (queuedBehindActive) {
                    main.postDelayed(() -> refreshStatus(false), 900);
                    return;
                }
                if (!guide) activeWatch = data.optJSONObject("watch");
                if (guide) main.postDelayed(() -> refreshStatus(true), 800);
                else schedulePoll();
            }
        });
    }

    private void sendSlash(String command) {
        EditText arg = input("可选参数");
        new AlertDialog.Builder(this)
                .setTitle(command)
                .setView(arg)
                .setNegativeButton("取消", null)
                .setPositiveButton("发送", (d, w) -> {
                    JSONObject body = new JSONObject();
                    put(body, "threadId", selectedThreadId);
                    put(body, "command", command);
                    put(body, "argument", arg.getText().toString());
                    request("POST", "/codex/slash-command", body, 20000, okToast());
                })
                .show();
    }

    private void stopCodex() {
        JSONObject body = new JSONObject();
        put(body, "threadId", selectedThreadId);
        request("POST", "/codex/stop", body, 15000, new JsonCallback() {
            public void done(JSONObject data) {
                stopPolling();
                toast(data.optString("message", "已停止"));
                loadHistory();
            }
        });
    }

    private void createNewThread() {
        JSONObject body = new JSONObject();
        request("POST", "/codex/new-thread", body, 22000, new JsonCallback() {
            public void done(JSONObject data) {
                selectedThreadId = "";
                activeWatch = null;
                activeAssistantBubble = null;
                messageList.removeAllViews();
                renderThreadHeader(null);
                toast(data.optString("message", "已打开新线程"));
            }
        });
    }

    private void chooseModel() {
        JSONArray options = new JSONArray();
        try { options = new JSONArray(prefs.getString("model_options", "[]")); } catch (Exception ignored) {}
        if (options.length() == 0) {
            put(options, model("gpt-5.5", "GPT-5.5"));
            put(options, model("gpt-5.4", "GPT-5.4"));
            put(options, model("gpt-5.4-mini", "GPT-5.4-Mini"));
            put(options, model("gpt-5.3-codex", "GPT-5.3-Codex"));
            put(options, model("gpt-5.2", "GPT-5.2"));
        }
        String[] labels = new String[options.length()];
        String[] keys = new String[options.length()];
        for (int i = 0; i < options.length(); i++) {
            JSONObject item = options.optJSONObject(i);
            labels[i] = item == null ? "模型" : item.optString("displayName", item.optString("label", item.optString("id")));
            keys[i] = item == null ? "" : item.optString("key", item.optString("id"));
        }
        new AlertDialog.Builder(this)
                .setTitle("切换模型")
                .setItems(labels, (d, which) -> postTarget("/codex/model-switch", keys[which]))
                .show();
    }

    private void chooseReasoning() {
        String[] labels = {"低", "中", "高", "超高"};
        String[] keys = {"low", "medium", "high", "xhigh"};
        new AlertDialog.Builder(this)
                .setTitle("推理模式")
                .setItems(labels, (d, which) -> postTarget("/codex/reasoning-mode", keys[which]))
                .show();
    }

    private void chooseThreadAction() {
        if (selectedThreadId.isEmpty()) {
            toast("请先选择线程");
            return;
        }
        String[] labels = {"置顶", "取消置顶", "重命名", "归档"};
        new AlertDialog.Builder(this)
                .setTitle("线程操作")
                .setItems(labels, (d, which) -> {
                    if (which == 2) renameThread();
                    else postThreadAction(which == 0 ? "pin" : which == 1 ? "unpin" : "archive", "");
                })
                .show();
    }

    private void renameThread() {
        EditText name = input("新名称");
        new AlertDialog.Builder(this)
                .setTitle("重命名线程")
                .setView(name)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> postThreadAction("rename", name.getText().toString()))
                .show();
    }

    private void chooseAppCommand() {
        String[] labels = {
                "复制 Codex 链接", "复制 Session ID", "复制工作目录", "命令面板", "搜索对话",
                "搜索文件", "当前对话查找", "快捷键", "设置", "切换侧边栏", "切换终端", "打开模型选择器"
        };
        String[] commands = {
                "copyDeeplink", "copySessionId", "copyWorkingDirectory", "openCommandMenu", "searchChats",
                "searchFiles", "findInThread", "showKeyboardShortcuts", "settings", "toggleSidebar", "toggleTerminal", "composer.openModelPicker"
        };
        new AlertDialog.Builder(this)
                .setTitle("Codex App 命令")
                .setItems(labels, (d, which) -> {
                    JSONObject body = new JSONObject();
                    put(body, "threadId", selectedThreadId);
                    put(body, "command", commands[which]);
                    request("POST", "/codex/app-command", body, 18000, okToast());
                })
                .show();
    }

    private void postTarget(String path, String target) {
        JSONObject body = new JSONObject();
        put(body, "threadId", selectedThreadId);
        put(body, "target", target);
        request("POST", path, body, 24000, new JsonCallback() {
            public void done(JSONObject data) {
                toast(data.optString("message", "已切换"));
                refreshStatus(false);
            }
        });
    }

    private void postThreadAction(String action, String name) {
        JSONObject body = new JSONObject();
        put(body, "threadId", selectedThreadId);
        put(body, "action", action);
        if (!name.isEmpty()) put(body, "name", name);
        request("POST", "/codex/thread-action", body, 22000, new JsonCallback() {
            public void done(JSONObject data) {
                toast(data.optString("message", "已完成"));
                selectedThreadId = data.optString("nextThreadId", selectedThreadId);
                loadThreads(false);
            }
        });
    }

    private void showDiagnostics() {
        request("GET", "/codex/service-check", null, 12000, new JsonCallback() {
            public void done(JSONObject data) {
                JSONArray checks = data.optJSONArray("checks");
                StringBuilder text = new StringBuilder();
                text.append(data.optInt("passed")).append("/").append(data.optInt("total")).append(" 通过\n\n");
                if (checks != null) {
                    for (int i = 0; i < checks.length(); i++) {
                        JSONObject item = checks.optJSONObject(i);
                        if (item == null) continue;
                        text.append(item.optBoolean("ok") ? "✓ " : "× ")
                                .append(item.optString("label"))
                                .append(" · ")
                                .append(item.optString("status"))
                                .append("\n")
                                .append(item.optString("detail"))
                                .append("\n\n");
                    }
                }
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("服务诊断")
                        .setMessage(text.toString().trim())
                        .setPositiveButton("好", null)
                        .show();
            }
        });
    }

    private void schedulePoll() {
        stopPolling();
        pollRunnable = () -> refreshStatus(true);
        main.postDelayed(pollRunnable, 1600);
    }

    private void stopPolling() {
        if (pollRunnable != null) main.removeCallbacks(pollRunnable);
        pollRunnable = null;
    }

    private void pickImages() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, PICK_IMAGES_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_IMAGES_REQUEST || resultCode != RESULT_OK || data == null) return;
        ArrayList<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        executor.execute(() -> {
            for (Uri uri : uris) {
                try {
                    attachments.add(readAttachment(uri));
                } catch (Exception error) {
                    main.post(() -> toast("读取图片失败：" + error.getMessage()));
                }
            }
            main.post(this::renderAttachments);
        });
    }

    private Attachment readAttachment(Uri uri) throws Exception {
        String type = getContentResolver().getType(uri);
        if (type == null || !type.startsWith("image/")) type = "image/*";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("无法打开文件");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
        }
        byte[] bytes = out.toByteArray();
        if (bytes.length > 8 * 1024 * 1024) throw new IllegalStateException("单张图片超过 8MB");
        String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(type);
        String name = "image-" + System.currentTimeMillis() + (ext == null ? ".img" : "." + ext);
        String dataUrl = "data:" + type + ";base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);
        return new Attachment(name, type, dataUrl);
    }

    private void request(String method, String path, JSONObject body, int timeoutMs, JsonCallback callback) {
        setBusy(true);
        executor.execute(() -> {
            try {
                JSONObject data = http(method, path, body, timeoutMs);
                main.post(() -> {
                    setBusy(false);
                    callback.done(data);
                });
            } catch (Exception error) {
                main.post(() -> {
                    setBusy(false);
                    toast(error.getMessage() == null ? "请求失败" : error.getMessage());
                });
            }
        });
    }

    private JSONObject http(String method, String path, JSONObject body, int timeoutMs) throws Exception {
        String urlText = apiBase() + path;
        if ("GET".equals(method)) {
            urlText += (urlText.contains("?") ? "&" : "?") + "token=" + enc(pref(KEY_SECRET)) + "&t=" + System.currentTimeMillis();
        }
        HttpURLConnection conn = (HttpURLConnection) new URL(urlText).openConnection();
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestMethod(method);
        conn.setRequestProperty("accept", "application/json");
        conn.setRequestProperty("x-mobile-typer-token", pref(KEY_SECRET));
        if (body != null) {
            byte[] bytes = body.toString().getBytes("UTF-8");
            conn.setDoOutput(true);
            conn.setRequestProperty("content-type", "application/json; charset=utf-8");
            conn.setRequestProperty("content-length", String.valueOf(bytes.length));
            try (OutputStream out = conn.getOutputStream()) {
                out.write(bytes);
            }
        }
        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
        String text = readText(stream);
        JSONObject data = text.isEmpty() ? new JSONObject() : new JSONObject(text);
        if (code < 200 || code >= 300 || !data.optBoolean("ok", false)) {
            throw new IllegalStateException(data.optString("message", "请求失败：" + code));
        }
        return data;
    }

    private String readText(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
        return out.toString("UTF-8");
    }

    private String apiBase() {
        String relay = pref(KEY_RELAY).replaceAll("/+$", "");
        String path = normalizePath(pref(KEY_PATH)).replaceAll("/+$", "");
        return relay + ("/".equals(path) ? "" : path);
    }

    private void renderMessages(JSONArray messages) {
        messageList.removeAllViews();
        activeAssistantBubble = null;
        if (messages == null || messages.length() == 0) {
            renderEmpty();
            return;
        }
        for (int i = 0; i < messages.length(); i++) {
            JSONObject item = messages.optJSONObject(i);
            if (item == null) continue;
            appendMessage(item.optString("role"), item.optString("label", item.optString("role")), item.optString("text"));
        }
        scrollBottom();
    }

    private void upsertAssistant(JSONObject status) {
        String text = status.optString("final", status.optString("preview", ""));
        if (text.isEmpty()) text = status.optString("preview", "Codex 正在处理...");
        TextView last = activeAssistantBubble != null ? activeAssistantBubble : lastMessageBubble();
        if (last != null) last.setText(text);
    }

    private TextView lastMessageBubble() {
        if (messageList.getChildCount() == 0) return null;
        View row = messageList.getChildAt(messageList.getChildCount() - 1);
        if (!(row instanceof LinearLayout)) return null;
        LinearLayout group = (LinearLayout) row;
        if (group.getChildCount() < 2 || !(group.getChildAt(1) instanceof TextView)) return null;
        return (TextView) group.getChildAt(1);
    }

    private TextView appendMessage(String role, String label, String body) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setGravity("user".equals(role) ? Gravity.RIGHT : Gravity.LEFT);
        group.setPadding(0, dp(5), 0, dp(5));

        TextView meta = text(label, 11, Color.rgb(105, 113, 122), false);
        meta.setGravity("user".equals(role) ? Gravity.RIGHT : Gravity.LEFT);
        group.addView(meta, matchWrap());

        TextView bubble = text(body == null || body.isEmpty() ? " " : body, 14, Color.rgb(20, 25, 30), false);
        bubble.setTextIsSelectable(true);
        bubble.setPadding(dp(12), dp(9), dp(12), dp(9));
        bubble.setBackground(round("user".equals(role) ? Color.rgb(231, 239, 255) : Color.WHITE, Color.rgb(220, 225, 231), 14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMargins("user".equals(role) ? dp(42) : 0, dp(3), "user".equals(role) ? 0 : dp(42), 0);
        group.addView(bubble, params);
        messageList.addView(group, matchWrap());
        scrollBottom();
        return bubble;
    }

    private void renderEmpty() {
        messageList.removeAllViews();
        appendMessage("assistant", "CodexPanel", "已准备连接远端控制接口。选择线程或新建线程后即可开始。");
    }

    private void renderThreadHeader(ThreadItem item) {
        if (item == null) {
            threadText.setText("新线程");
            detailText.setText("输入第一条消息后会创建会话");
            return;
        }
        threadText.setText(item.name.isEmpty() ? item.id : item.name);
        detailText.setText((item.projectName.isEmpty() ? "对话" : item.projectName) + " · " + statusText(item.runtimeStatus));
    }

    private void renderAttachments() {
        if (attachments.isEmpty()) {
            attachmentText.setVisibility(View.GONE);
            attachmentText.setText("");
            return;
        }
        attachmentText.setVisibility(View.VISIBLE);
        attachmentText.setText("已选择 " + attachments.size() + " 张图片，长按可清空");
        attachmentText.setOnLongClickListener(v -> {
            attachments.clear();
            renderAttachments();
            return true;
        });
    }

    private void setBusy(boolean value) {
        busy = value;
        if (progressBar != null) progressBar.setVisibility(value ? View.VISIBLE : View.GONE);
    }

    private ThreadItem findThread(String id) {
        for (ThreadItem item : threads) if (item.id.equals(id)) return item;
        return null;
    }

    private JsonCallback okToast() {
        return data -> toast(data.optString("message", "已完成"));
    }

    private JSONObject model(String key, String name) {
        JSONObject item = new JSONObject();
        put(item, "key", key);
        put(item, "id", key);
        put(item, "displayName", name);
        return item;
    }

    private void put(JSONObject object, String key, Object value) {
        try { object.put(key, value); } catch (Exception ignored) {}
    }

    private void put(JSONArray array, Object value) {
        array.put(value);
    }

    private String statusText(String status) {
        if ("running".equals(status)) return "运行中";
        if ("waiting".equals(status)) return "等待中";
        if ("complete".equals(status)) return "已完成";
        if ("error".equals(status)) return "异常";
        return "空闲";
    }

    private String hostLabel() {
        try { return new URL(pref(KEY_RELAY)).getHost(); } catch (Exception ignored) { return "远端"; }
    }

    private boolean saveFields() {
        String relay = cleanUrl(relayInput.getText().toString());
        String path = normalizePath(pathInput.getText().toString());
        String key = keyInput.getText().toString().trim();
        RemoteConfig parsed = parseRemoteUrl(relay);
        if (parsed != null) {
            relay = parsed.relay;
            path = parsed.path;
            if (!parsed.key.isEmpty()) key = parsed.key;
        }
        if (relay.isEmpty() || path.isEmpty() || key.isEmpty()) {
            toast("请填写 Cloudflare 服务地址、设备路径和远控密钥");
            return false;
        }
        prefs.edit().putString(KEY_RELAY, relay).putString(KEY_PATH, path).putString(KEY_SECRET, key).apply();
        return true;
    }

    private boolean saveRemoteUrl(String value) {
        RemoteConfig parsed = parseRemoteUrl(value);
        if (parsed == null || parsed.relay.isEmpty() || parsed.path.isEmpty() || parsed.key.isEmpty()) return false;
        prefs.edit().putString(KEY_RELAY, parsed.relay).putString(KEY_PATH, parsed.path).putString(KEY_SECRET, parsed.key).apply();
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

    private RemoteConfig parseRemoteUrl(String value) {
        try {
            Uri uri = Uri.parse(String.valueOf(value).trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) return null;
            String path = uri.getEncodedPath();
            String key = uri.getQueryParameter("token");
            if (path == null || !path.contains("/remote/")) return null;
            path = path.replaceAll("/control\\.html$", "/").replaceAll("/index\\.html$", "/");
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

    private String enc(String value) {
        try { return URLEncoder.encode(String.valueOf(value == null ? "" : value), "UTF-8"); }
        catch (Exception ignored) { return ""; }
    }

    private void hideKeyboard() {
        try {
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(composerInput.getWindowToken(), 0);
        } catch (Exception ignored) {}
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        text.setIncludeFontPadding(true);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private TextView label(String value) {
        TextView label = text(value, 13, Color.rgb(24, 29, 34), true);
        label.setPadding(0, dp(10), 0, dp(4));
        return label;
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setTextSize(15);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackground(round(Color.WHITE, Color.rgb(212, 219, 226), 12));
        return input;
    }

    private Button primaryButton(String value) {
        Button button = button(value);
        button.setTextColor(Color.WHITE);
        button.setBackground(round(Color.rgb(32, 92, 255), Color.rgb(32, 92, 255), 14));
        return button;
    }

    private Button secondaryButton(String value) {
        Button button = button(value);
        button.setTextColor(Color.rgb(20, 25, 30));
        button.setBackground(round(Color.WHITE, Color.rgb(212, 219, 226), 14));
        return button;
    }

    private Button dangerButton(String value) {
        Button button = button(value);
        button.setTextColor(Color.WHITE);
        button.setBackground(round(Color.rgb(206, 58, 58), Color.rgb(206, 58, 58), 14));
        return button;
    }

    private Button toolbarButton(String value, View.OnClickListener listener) {
        Button button = secondaryButton(value);
        button.setTextSize(12);
        button.setOnClickListener(listener);
        return button;
    }

    private Button chip(String value, View.OnClickListener listener) {
        Button button = secondaryButton(value);
        button.setTextSize(12);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(76), dp(36));
        params.setMargins(0, 0, dp(8), 0);
        button.setLayoutParams(params);
        return button;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(value);
        button.setTextSize(14);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(Color.WHITE, Color.rgb(226, 231, 236), 18));
        return card;
    }

    private GradientDrawable round(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(50));
        params.setMargins(0, 0, 0, dp(4));
        return params;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(50));
        params.setMargins(0, dp(10), 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams insetParams(int l, int t, int r, int b) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(l), dp(t), dp(r), dp(b));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void installKeyboardBottomFix(View root) {
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect visible = new Rect();
            root.getWindowVisibleDisplayFrame(visible);
            int height = Math.max(1, root.getRootView().getHeight());
            int hidden = height - visible.bottom;
            boolean open = hidden > height * 0.15f;
            if (keyboardOpen && !open) {
                main.postDelayed(this::scrollBottom, 60);
                main.postDelayed(this::scrollBottom, 180);
                main.postDelayed(this::scrollBottom, 320);
            } else if (open && composerInput != null && composerInput.hasFocus()) {
                main.postDelayed(this::scrollBottom, 80);
            }
            keyboardOpen = open;
        });
    }

    private void scrollBottom() {
        if (messageScroll != null) messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        stopPolling();
        executor.shutdownNow();
        super.onDestroy();
    }

    private interface JsonCallback {
        void done(JSONObject data);
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

    private static class ThreadItem {
        final String id;
        final String name;
        final String projectName;
        final String runtimeStatus;
        final boolean pinned;

        ThreadItem(String id, String name, String projectName, String runtimeStatus, boolean pinned) {
            this.id = id;
            this.name = name;
            this.projectName = projectName;
            this.runtimeStatus = runtimeStatus;
            this.pinned = pinned;
        }

        static ThreadItem from(JSONObject item) {
            return new ThreadItem(
                    item.optString("id"),
                    item.optString("name", "未命名线程"),
                    item.optString("projectName", "对话"),
                    item.optString("runtimeStatus", "idle"),
                    item.optBoolean("pinned")
            );
        }

        String display() {
            return (pinned ? "★ " : "") + (TextUtils.isEmpty(name) ? id : name) + " · " + projectName;
        }
    }

    private static class Attachment {
        final String name;
        final String type;
        final String dataUrl;

        Attachment(String name, String type, String dataUrl) {
            this.name = name;
            this.type = type;
            this.dataUrl = dataUrl;
        }

        JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("name", name);
                object.put("type", type);
                object.put("dataUrl", dataUrl);
            } catch (Exception ignored) {}
            return object;
        }
    }
}
