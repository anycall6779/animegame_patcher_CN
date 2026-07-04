package com.localify.chinapatch;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.text.InputType;
import android.util.JsonReader;
import android.util.JsonToken;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import com.android.apksig.ApkSigner;
import com.android.apksig.ApkSigner.SignerConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {
    private static final int REQ_APK = 1001;
    private static final int REQ_JS = 1002;
    private static final String CONFIG_URL = "https://www.benghuai.com/download/config";
    private static final String DEFAULT_JS_URL = "https://codeberg.org/rouzzang/animagameGirlZ/raw/commit/ca7d11156f1140271a80e39b7d45af8985af28bc/libfrida-gadget.script_KR.js";
    private static final String OLD_APP = "com.combosdk.openapi.ComboApplication";
    private static final String NEW_APP = "com.combosdk.openapi.ComboAppProxy";
    private static final String CLASSES5_PATH = "classes5.dex";
    private static final String LIB_PATH = "lib/arm64-v8a/libanimegame_native_localify.so";
    private static final String BLOB_PATH = "assets/animegame_translation_blob.bin";
    private static final Set<String> LAUNCHER_ICON_ENTRIES = new HashSet<>();

    static {
        Collections.addAll(LAUNCHER_ICON_ENTRIES, "res/Ut.png", "res/W5.png", "res/YA.png", "res/l4.png", "res/Iw.png", "res/oj.png");
    }

    private final ArrayList<ServerItem> servers = new ArrayList<>();
    private Spinner serverSpinner;
    private TextView apkFileText;
    private TextView jsFileText;
    private TextView logView;
    private TextView progressText;
    private EditText jsUrlEdit;
    private ProgressBar progressBar;
    private Button runButton;
    private Button installButton;
    private RadioGroup apkSourceGroup;
    private RadioGroup titleGroup;
    private RadioGroup iconGroup;
    private Uri manualApkUri;
    private Uri manualJsUri;
    private File lastSignedApk;
    private boolean darkMode;
    private boolean patchRunning;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        requestNotificationPermission();
        loadServerList();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3001);
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad + statusBarHeight(), pad, pad);
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(getResources().getIdentifier("patch_icon_launcher", "drawable", getPackageName()));
        header.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = text("붕괴학원패치", 24, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1);
        titleLp.leftMargin = dp(12);
        header.addView(title, titleLp);
        Button themeButton = button("라이트/다크");
        header.addView(themeButton);
        root.addView(header);

        root.addView(section("APK"));
        apkSourceGroup = radioGroup(new String[]{"공식 서버 다운로드", "수동 APK 선택"}, 0);
        root.addView(apkSourceGroup);
        serverSpinner = new Spinner(this);
        root.addView(serverSpinner, full());
        Button selectApk = button("APK 선택");
        apkFileText = text("선택된 APK 없음", 13, false);
        root.addView(selectApk, full());
        root.addView(apkFileText, full());

        root.addView(section("번역 JS"));
        jsUrlEdit = new EditText(this);
        jsUrlEdit.setSingleLine(true);
        jsUrlEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        jsUrlEdit.setHint("번역 JS URL 직접 입력 (비우면 기본값 사용)");
        root.addView(jsUrlEdit, full());
        Button selectJs = button("JS 선택");
        jsFileText = text("URL 다운로드 사용", 13, false);
        root.addView(selectJs, full());
        root.addView(jsFileText, full());

        root.addView(section("결과 APK"));
        root.addView(section("어플 이름 선택"));
        titleGroup = radioGroup(new String[]{"붕괴학원2", "崩坏学园2"}, 0);
        root.addView(titleGroup);
        root.addView(section("아이콘 선택"));
        root.addView(iconPreviewRow());
        iconGroup = radioGroup(new String[]{"ic_launcher.png (카린)", "app_icon.png (키아나)"}, 0);
        root.addView(iconGroup);

        runButton = button("다운로드 / 패치 / 서명");
        root.addView(runButton, full());
        installButton = button("결과 APK 설치");
        installButton.setEnabled(false);
        root.addView(installButton, full());

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);
        root.addView(progressBar, full());
        progressText = text("대기 중", 13, false);
        root.addView(progressText, full());

        logView = text("", 13, false);
        logView.setMinLines(10);
        logView.setMovementMethod(new ScrollingMovementMethod());
        HorizontalScrollView logScroll = new HorizontalScrollView(this);
        logScroll.addView(logView);
        root.addView(logScroll, full());

        setContentView(scroll);
        applyThemeColors();

        themeButton.setOnClickListener(v -> {
            darkMode = !darkMode;
            applyThemeColors();
        });
        selectApk.setOnClickListener(v -> openPicker(REQ_APK, "application/vnd.android.package-archive"));
        selectJs.setOnClickListener(v -> openPicker(REQ_JS, "*/*"));
        runButton.setOnClickListener(v -> runPatchFlow());
        installButton.setOnClickListener(v -> openInstallIntent());
    }

    private void loadServerList() {
        appendLog("서버 목록 조회: " + CONFIG_URL);
        new Thread(() -> {
            try {
                JSONArray array = new JSONArray(httpGetText(CONFIG_URL));
                servers.clear();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject o = array.getJSONObject(i);
                    String url = o.optString("android_download_url", "");
                    if (!url.isEmpty()) {
                        servers.add(new ServerItem(o.optString("id"), o.optString("name"), o.optString("title"), o.optString("update"), url));
                    }
                }
                runOnUiThread(() -> {
                    ArrayAdapter<ServerItem> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, servers);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    serverSpinner.setAdapter(adapter);
                    appendLog("서버 목록 완료: " + servers.size() + "개");
                });
            } catch (Exception e) {
                appendLog("서버 목록 실패: " + e.getMessage());
            }
        }).start();
    }

    private void runPatchFlow() {
        runButton.setEnabled(false);
        installButton.setEnabled(false);
        patchRunning = true;
        lastSignedApk = null;
        PatchForegroundService.start(this);
        progress(0, "시작");
        new Thread(() -> {
            try {
                File work = new File(getExternalFilesDir(null), "patched");
                if (!work.exists() && !work.mkdirs()) throw new IOException("work dir create failed");

                File original = new File(work, "source.apk");
                File unsigned = new File(work, "patched-unsigned.apk");
                File signed = new File(work, "patched-signed.apk");
                File js = new File(work, "libfrida-gadget.script_KR.js");
                File blob = new File(work, "animegame_translation_blob.bin");

                boolean manualApk = checkedIndex(apkSourceGroup) == 1;
                if (manualApk) {
                    if (manualApkUri == null) throw new IOException("수동 APK가 선택되지 않았습니다.");
                    appendLog("수동 APK 복사");
                    copyUriToFile(manualApkUri, original, 0, 250);
                } else {
                    ServerItem item = (ServerItem) serverSpinner.getSelectedItem();
                    if (item == null) throw new IOException("서버가 선택되지 않았습니다.");
                    if (useCachedFileIfWanted(original, "APK", item.title, item.androidUrl)) {
                        progress(450, "APK 캐시 사용");
                    } else {
                        appendLog("APK 다운로드: " + item.title);
                        downloadFileWithRetry(item.androidUrl, original, 0, 450, "APK 다운로드");
                        writeCacheKey(original, item.androidUrl);
                    }
                }
                appendLog("APK 준비 완료: " + formatBytes(original.length()));

                if (manualJsUri != null) {
                    appendLog("수동 JS 복사");
                    copyUriToFile(manualJsUri, js, 450, 580);
                } else {
                    String jsUrl = getJsUrl();
                    if (useCachedFileIfWanted(js, "번역 JS", jsUrl, jsUrl)) {
                        progress(650, "JS 캐시 사용");
                    } else {
                        appendLog("JS 다운로드: " + jsUrl);
                        downloadFileWithRetry(jsUrl, js, 450, 650, "JS 다운로드");
                        writeCacheKey(js, jsUrl);
                    }
                }
                appendLog("JS 준비 완료: " + formatBytes(js.length()));
                appendLog("번역 blob 생성");
                progress(650, "번역 blob 생성 중");
                makeTranslationBlob(js, blob, this);
                appendLog("번역 blob 생성 완료: " + formatBytes(blob.length()));
                progress(700, "패치 중");

                PatchOptions options = new PatchOptions();
                options.appTitle = selectedText(titleGroup);
                options.iconAsset = checkedIndex(iconGroup) == 0 ? "payload/ic_launcher.png" : "payload/app_icon.png";
                ApkPatcher.patch(this, original, unsigned, blob, options);
                appendLog("패치 완료: " + formatBytes(unsigned.length()));

                progress(850, "서명 중");
                ApkPatcher.sign(this, unsigned, signed);
                appendLog("서명 완료: " + formatBytes(signed.length()));
                lastSignedApk = signed;
                progress(1000, "완료");
                appendLog("결과: " + signed.getAbsolutePath());
                runOnUiThread(() -> installButton.setEnabled(true));
            } catch (Exception e) {
                progress(0, "실패");
                appendLog("실패: " + e);
            } finally {
                patchRunning = false;
                PatchForegroundService.stop(this);
                runOnUiThread(() -> runButton.setEnabled(true));
            }
        }).start();
    }

    private String selectedText(RadioGroup group) {
        RadioButton rb = group.findViewById(group.getCheckedRadioButtonId());
        return rb == null ? "" : rb.getText().toString();
    }

    private int checkedIndex(RadioGroup group) {
        int checked = group.getCheckedRadioButtonId();
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i).getId() == checked) return i;
        }
        return 0;
    }

    private void openPicker(int requestCode, String mime) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        if (requestCode == REQ_APK) {
            manualApkUri = uri;
            apkFileText.setText(uri.toString());
            ((RadioButton) apkSourceGroup.getChildAt(1)).setChecked(true);
        } else if (requestCode == REQ_JS) {
            manualJsUri = uri;
            jsFileText.setText(uri.toString());
        }
    }

    private String getJsUrl() {
        final String[] result = {DEFAULT_JS_URL};
        final CountDownLatch latch = new CountDownLatch(1);
        runOnUiThread(() -> {
            String text = jsUrlEdit == null ? "" : jsUrlEdit.getText().toString().trim();
            result[0] = text.isEmpty() ? DEFAULT_JS_URL : text;
            latch.countDown();
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0];
    }

    private boolean useCachedFileIfWanted(File file, String kind, String detail, String cacheKey) throws IOException {
        if (!file.exists() || file.length() <= 0) return false;
        if (!cacheKeyMatches(file, cacheKey)) {
            appendLog(kind + " 캐시 URL이 달라 다시 다운로드합니다.");
            if (!file.delete()) appendLog(kind + " 기존 캐시 삭제 실패, 덮어쓰기 시도");
            return false;
        }
        appendLog(kind + " 캐시 발견: " + formatBytes(file.length()));
        final boolean[] useCache = {true};
        final CountDownLatch latch = new CountDownLatch(1);
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle(kind + " 캐시 사용")
                .setMessage("이미 받은 파일이 있습니다.\n" + detail + "\n\n캐시를 사용하시겠습니까?")
                .setPositiveButton("캐시 사용", (dialog, which) -> {
                    useCache[0] = true;
                    latch.countDown();
                })
                .setNegativeButton("다시 다운로드", (dialog, which) -> {
                    useCache[0] = false;
                    latch.countDown();
                })
                .setOnCancelListener(dialog -> {
                    useCache[0] = true;
                    latch.countDown();
                })
                .show());
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("cache dialog interrupted", e);
        }
        if (useCache[0]) {
            appendLog(kind + " 캐시 사용");
            return true;
        }
        if (!file.delete()) appendLog(kind + " 기존 캐시 삭제 실패, 덮어쓰기 시도");
        return false;
    }

    private static boolean cacheKeyMatches(File file, String cacheKey) {
        File keyFile = cacheKeyFile(file);
        if (!keyFile.exists()) return true;
        try (FileInputStream in = new FileInputStream(keyFile)) {
            String old = new String(readAll(in), StandardCharsets.UTF_8);
            return old.equals(cacheKey == null ? "" : cacheKey);
        } catch (IOException e) {
            return false;
        }
    }

    private static void writeCacheKey(File file, String cacheKey) {
        try (FileOutputStream out = new FileOutputStream(cacheKeyFile(file))) {
            out.write((cacheKey == null ? "" : cacheKey).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    private static File cacheKeyFile(File file) {
        return new File(file.getParentFile(), file.getName() + ".url");
    }

    private void openInstallIntent() {
        if (lastSignedApk == null || !lastSignedApk.exists()) {
            appendLog(patchRunning ? "아직 패치/서명 처리 중입니다. 완료 후 설치 버튼을 눌러주세요." : "결과 APK가 없습니다.");
            return;
        }
        if (!getPackageManager().canRequestPackageInstalls()) {
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            appendLog("알 수 없는 앱 설치 권한을 허용한 뒤 다시 누르세요.");
            return;
        }
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", lastSignedApk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void appendLog(String text) {
        runOnUiThread(() -> logView.append(text + "\n"));
    }

    private void progress(int value, String text) {
        runOnUiThread(() -> {
            progressBar.setProgress(value);
            progressText.setText(text + " " + (value / 10) + "%");
        });
        PatchForegroundService.update(this, value / 10, text);
    }

    private void applyThemeColors() {
        int bg = darkMode ? 0xff15171a : 0xfff7f9fb;
        int fg = darkMode ? 0xffeef2f6 : 0xff1f2933;
        root.setBackgroundColor(bg);
        applyTextColor(root, fg);
    }

    private void applyTextColor(View view, int color) {
        if (view instanceof TextView) ((TextView) view).setTextColor(color);
        if (view instanceof LinearLayout) {
            LinearLayout layout = (LinearLayout) view;
            for (int i = 0; i < layout.getChildCount(); i++) applyTextColor(layout.getChildAt(i), color);
        }
    }

    private LinearLayout iconPreviewRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(iconPreview("카린", getResources().getIdentifier("patch_icon_launcher", "drawable", getPackageName())), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(iconPreview("키아나", getResources().getIdentifier("patch_icon_app", "drawable", getPackageName())), new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private LinearLayout iconPreview(String label, int resId) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        ImageView image = new ImageView(this);
        image.setImageResource(resId);
        box.addView(image, new LinearLayout.LayoutParams(dp(72), dp(72)));
        TextView text = text(label, 13, true);
        text.setGravity(Gravity.CENTER);
        box.addView(text);
        return box;
    }

    private TextView section(String text) {
        TextView view = text(text, 16, true);
        view.setPadding(0, dp(18), 0, dp(6));
        return view;
    }

    private TextView text(String text, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return v;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        return b;
    }

    private RadioGroup radioGroup(String[] labels, int checkedIndex) {
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(labels[i]);
            group.addView(rb);
        }
        ((RadioButton) group.getChildAt(checkedIndex)).setChecked(true);
        return group;
    }

    private LinearLayout.LayoutParams full() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(6);
        return lp;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    private static String httpGetText(String url) throws IOException {
        HttpURLConnection conn = open(url);
        try (InputStream in = conn.getInputStream()) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }

    private void downloadFileWithRetry(String url, File out, int start, int end, String label) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            if (out.exists() && !out.delete()) appendLog("기존 파일 삭제 실패, 덮어쓰기 시도");
            try {
                appendLog(label + " 시도 " + attempt + "/3");
                downloadFileOnce(url, out, start, end, label);
                return;
            } catch (IOException e) {
                last = e;
                appendLog(label + " 실패 " + attempt + "/3: " + e.getMessage());
                SystemClock.sleep(1200L * attempt);
            }
        }
        throw last == null ? new IOException(label + " 실패") : last;
    }

    private void downloadFileOnce(String url, File out, int start, int end, String label) throws IOException {
        RangeInfo rangeInfo = probeRange(url);
        if (rangeInfo.supported && rangeInfo.total > 8L * 1024L * 1024L) {
            appendLog(label + " 병렬 분할 다운로드: " + rangeInfo.parts + "조각 / " + formatBytes(rangeInfo.total));
            downloadFileParallel(url, out, rangeInfo, start, end, label);
            return;
        }

        HttpURLConnection conn = open(url);
        int total = conn.getContentLength();
        try (InputStream in = conn.getInputStream(); FileOutputStream fos = new FileOutputStream(out)) {
            long copied = copyWithProgress(in, fos, total, start, end, this, label);
            if (total > 0 && copied < total) {
                throw new IOException("stream ended early: " + copied + "/" + total);
            }
        } finally {
            conn.disconnect();
        }
    }

    private void downloadFileParallel(String url, File out, RangeInfo info, int start, int end, String label) throws IOException {
        File partsDir = new File(out.getParentFile(), out.getName() + ".parts");
        if (!partsDir.exists() && !partsDir.mkdirs()) throw new IOException("parts dir create failed");

        File state = new File(partsDir, "download.state");
        String expected = url + "\n" + info.total + "\n" + info.parts + "\n";
        if (state.exists()) {
            String actual;
            try (FileInputStream in = new FileInputStream(state)) {
                actual = new String(readAll(in), StandardCharsets.UTF_8);
            }
            if (!expected.equals(actual)) deleteDir(partsDir);
        }
        if (!partsDir.exists() && !partsDir.mkdirs()) throw new IOException("parts dir recreate failed");
        try (FileOutputStream fos = new FileOutputStream(state)) {
            fos.write(expected.getBytes(StandardCharsets.UTF_8));
        }

        AtomicLong done = new AtomicLong(existingPartBytes(partsDir, info));
        CountDownLatch latch = new CountDownLatch(info.parts);
        ExecutorService pool = Executors.newFixedThreadPool(info.parts);
        ArrayList<IOException> errors = new ArrayList<>();
        long begun = SystemClock.elapsedRealtime();
        long[] lastUi = {0};

        for (int i = 0; i < info.parts; i++) {
            final int part = i;
            pool.execute(() -> {
                try {
                    downloadPart(url, partsDir, info, part, done, start, end, label, begun, lastUi);
                } catch (IOException e) {
                    synchronized (errors) {
                        errors.add(e);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("download interrupted", e);
        } finally {
            pool.shutdownNow();
        }

        if (!errors.isEmpty()) throw errors.get(0);
        mergeParts(partsDir, info, out, start, end, label, begun);
        deleteDir(partsDir);
    }

    private void downloadPart(String url, File partsDir, RangeInfo info, int part, AtomicLong done, int start, int end, String label, long begun, long[] lastUi) throws IOException {
        long partStart = info.partStart(part);
        long partEnd = info.partEnd(part);
        File partFile = new File(partsDir, "part-" + part);
        long have = partFile.exists() ? partFile.length() : 0;
        long need = partEnd - partStart + 1;
        if (have > need) {
            if (!partFile.delete()) throw new IOException("invalid part delete failed: " + part);
            have = 0;
        }
        if (have == need) return;

        HttpURLConnection conn = open(url);
        conn.setRequestProperty("Range", "bytes=" + (partStart + have) + "-" + partEnd);
        int code = conn.getResponseCode();
        if (code != HttpURLConnection.HTTP_PARTIAL) throw new IOException("Range response failed: " + code);

        try (InputStream in = conn.getInputStream(); FileOutputStream fos = new FileOutputStream(partFile, true)) {
            byte[] buf = new byte[1024 * 128];
            int n;
            while ((n = in.read(buf)) >= 0) {
                fos.write(buf, 0, n);
                long totalDone = done.addAndGet(n);
                long now = SystemClock.elapsedRealtime();
                synchronized (lastUi) {
                    if (now - lastUi[0] >= 350 || totalDone >= info.total) {
                        lastUi[0] = now;
                        int p = start + (int) ((end - start) * totalDone / info.total);
                        progress(Math.min(end, p), progressLine(label + " 병렬", totalDone, info.total, begun, now));
                    }
                }
            }
        } finally {
            conn.disconnect();
        }
        if (partFile.length() != need) throw new IOException("part ended early: " + part + " " + partFile.length() + "/" + need);
    }

    private void mergeParts(File partsDir, RangeInfo info, File out, int start, int end, String label, long begun) throws IOException {
        progress(end - Math.max(1, (end - start) / 20), label + " 조각 병합 중");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[1024 * 256];
            long merged = 0;
            for (int i = 0; i < info.parts; i++) {
                File partFile = new File(partsDir, "part-" + i);
                try (FileInputStream in = new FileInputStream(partFile)) {
                    int n;
                    while ((n = in.read(buf)) >= 0) {
                        fos.write(buf, 0, n);
                        merged += n;
                    }
                }
            }
            if (merged != info.total) throw new IOException("merge size mismatch: " + merged + "/" + info.total);
        }
        progress(end, progressLine(label + " 완료", info.total, info.total, begun, SystemClock.elapsedRealtime()));
    }

    private static long existingPartBytes(File partsDir, RangeInfo info) {
        long done = 0;
        for (int i = 0; i < info.parts; i++) {
            File part = new File(partsDir, "part-" + i);
            long need = info.partEnd(i) - info.partStart(i) + 1;
            if (part.exists()) done += Math.min(part.length(), need);
        }
        return done;
    }

    private RangeInfo probeRange(String url) {
        HttpURLConnection conn = null;
        try {
            conn = open(url);
            conn.setRequestProperty("Range", "bytes=0-0");
            int code = conn.getResponseCode();
            String contentRange = conn.getHeaderField("Content-Range");
            long total = parseContentRangeTotal(contentRange);
            if (code == HttpURLConnection.HTTP_PARTIAL && total > 0) {
                return new RangeInfo(true, total, choosePartCount(total));
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return new RangeInfo(false, -1, 1);
    }

    private static int choosePartCount(long total) {
        if (total >= 512L * 1024L * 1024L) return 8;
        if (total >= 128L * 1024L * 1024L) return 6;
        if (total >= 32L * 1024L * 1024L) return 4;
        return 3;
    }

    private static long parseContentRangeTotal(String value) {
        if (value == null) return -1;
        int slash = value.lastIndexOf('/');
        if (slash < 0 || slash + 1 >= value.length()) return -1;
        try {
            return Long.parseLong(value.substring(slash + 1).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void copyUriToFile(Uri uri, File out, int start, int end) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri); FileOutputStream fos = new FileOutputStream(out)) {
            if (in == null) throw new IOException("openInputStream failed");
            copyWithProgress(in, fos, -1, start, end, this, "파일 복사");
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(120000);
        conn.setRequestProperty("User-Agent", "ChinaPatchAndroid/2.0");
        conn.setRequestProperty("Connection", "close");
        conn.setRequestProperty("Accept-Encoding", "identity");
        return conn;
    }

    private static long copyWithProgress(InputStream in, FileOutputStream fos, int total, int start, int end, MainActivity activity, String label) throws IOException {
        byte[] buf = new byte[1024 * 128];
        long done = 0;
        long begun = SystemClock.elapsedRealtime();
        long lastUi = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            fos.write(buf, 0, n);
            done += n;
            if (activity != null) {
                long now = SystemClock.elapsedRealtime();
                if (now - lastUi >= 350 || (total > 0 && done >= total)) {
                    lastUi = now;
                    int p = total > 0 ? start + (int) ((end - start) * done / total) : start;
                    activity.progress(Math.min(end, p), activity.progressLine(label, done, total, begun, now));
                }
            }
        }
        return done;
    }

    private String progressLine(String label, long done, long total, long begun, long now) {
        double seconds = Math.max(0.001, (now - begun) / 1000.0);
        double speed = done / seconds;
        String base = label + " " + formatBytes(done);
        if (total > 0) {
            long remaining = Math.max(0, total - done);
            long eta = speed > 1 ? (long) Math.ceil(remaining / speed) : -1;
            base += " / " + formatBytes(total) + " | " + formatBytes((long) speed) + "/s | 남은시간 " + formatEta(eta);
        } else {
            base += " | " + formatBytes((long) speed) + "/s | 남은시간 계산중";
        }
        return base;
    }

    private static String formatBytes(long bytes) {
        double v = bytes;
        String[] units = {"B", "KB", "MB", "GB"};
        int i = 0;
        while (v >= 1024 && i < units.length - 1) {
            v /= 1024.0;
            i++;
        }
        return i == 0 ? ((long) v) + units[i] : String.format(java.util.Locale.US, "%.1f%s", v, units[i]);
    }

    private static String formatEta(long seconds) {
        if (seconds < 0) return "계산중";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(java.util.Locale.US, "%d:%02d", m, s);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static void makeTranslationBlob(File jsFile, File out, MainActivity activity) throws Exception {
        TreeMap<String, String> map = new TreeMap<>(MainActivity::compareTranslationKeys);

        long objectOffset = findJsonObjectOffset(jsFile);
        try (FileInputStream fis = new FileInputStream(jsFile)) {
            long skipped = 0;
            while (skipped < objectOffset) {
                long n = fis.skip(objectOffset - skipped);
                if (n <= 0) throw new IOException("JSON object skip failed");
                skipped += n;
            }
            try (JsonReader reader = new JsonReader(new java.io.InputStreamReader(new BufferedInputStream(fis), StandardCharsets.UTF_8))) {
                reader.setLenient(true);
                reader.beginObject();
                int count = 0;
                long lastUi = 0;
                while (reader.hasNext()) {
                    String k = reader.nextName();
                    String v;
                    JsonToken token = reader.peek();
                    if (token == JsonToken.NULL) {
                        reader.nextNull();
                        v = "";
                    } else {
                        v = reader.nextString();
                    }
                    map.put(k, sanitize(v));
                    count++;
                    long now = SystemClock.elapsedRealtime();
                    if (activity != null && now - lastUi >= 500) {
                        lastUi = now;
                        activity.progress(650 + Math.min(35, count / 2500), "번역 blob 읽는 중 " + count + "개");
                    }
                }
                reader.endObject();
            }
        }

        if (activity != null) activity.progress(690, "번역 blob 쓰는 중 " + map.size() + "개");
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {
            dos.write(new byte[]{'H', 'S', 'O', 'N', 'T', 'R', '1', 0});
            writeLe32(dos, map.size());
            for (String k : map.keySet()) {
                byte[] kb = k.getBytes(StandardCharsets.UTF_8);
                byte[] vb = map.get(k).getBytes(StandardCharsets.UTF_8);
                writeLe32(dos, kb.length);
                writeLe32(dos, vb.length);
                dos.write(kb);
                dos.write(vb);
            }
        }
    }

    private static String sanitize(String s) {
        return s.replace("\r\n", "#n").replace("\r", "#r").replace("\n", "#n").replace("\t", "#t").replace("\u0000", "");
    }

    private static int compareTranslationKeys(String a, String b) {
        boolean ad = isDigits(a);
        boolean bd = isDigits(b);
        if (ad && bd) {
            int len = Integer.compare(stripLeadingZeros(a), stripLeadingZeros(b));
            if (len != 0) return len;
            int cmp = a.compareTo(b);
            return cmp != 0 ? cmp : Integer.compare(a.length(), b.length());
        }
        if (ad) return -1;
        if (bd) return 1;
        return a.compareTo(b);
    }

    private static int stripLeadingZeros(String s) {
        int i = 0;
        while (i < s.length() - 1 && s.charAt(i) == '0') i++;
        return s.length() - i;
    }

    private static boolean isDigits(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    private static long findJsonObjectOffset(File source) throws IOException {
        byte[] marker = "translation_default".getBytes(StandardCharsets.US_ASCII);
        int markerPos = 0;
        boolean sawMarker = false;
        long firstBrace = -1;
        long offset = 0;
        try (InputStream in = new BufferedInputStream(new FileInputStream(source))) {
            int b;
            while ((b = in.read()) >= 0) {
                if (firstBrace < 0 && b == '{') firstBrace = offset;
                if (!sawMarker) {
                    if (b == (marker[markerPos] & 0xff)) {
                        markerPos++;
                        if (markerPos == marker.length) sawMarker = true;
                    } else {
                        markerPos = b == (marker[0] & 0xff) ? 1 : 0;
                    }
                } else if (b == '{') {
                    return offset;
                }
                offset++;
            }
        }
        if (firstBrace >= 0) return firstBrace;
        throw new IllegalArgumentException("JSON object not found");
    }

    private static void writeLe32(DataOutputStream dos, int v) throws IOException {
        dos.writeByte(v & 0xff);
        dos.writeByte((v >>> 8) & 0xff);
        dos.writeByte((v >>> 16) & 0xff);
        dos.writeByte((v >>> 24) & 0xff);
    }

    private static void deleteDir(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteDir(child);
            }
        }
        file.delete();
    }

    static final class ServerItem {
        final String id;
        final String name;
        final String title;
        final String update;
        final String androidUrl;

        ServerItem(String id, String name, String title, String update, String androidUrl) {
            this.id = id;
            this.name = name;
            this.title = title;
            this.update = update;
            this.androidUrl = androidUrl;
        }

        @Override
        public String toString() {
            return title + " / " + update + " / " + name;
        }
    }

    static final class PatchOptions {
        String appTitle;
        String iconAsset;
    }

    static final class RangeInfo {
        final boolean supported;
        final long total;
        final int parts;

        RangeInfo(boolean supported, long total, int parts) {
            this.supported = supported;
            this.total = total;
            this.parts = Math.max(1, parts);
        }

        long partStart(int part) {
            return total * part / parts;
        }

        long partEnd(int part) {
            return part == parts - 1 ? total - 1 : (total * (part + 1) / parts) - 1;
        }
    }

    static final class ApkPatcher {
        static void patch(Activity activity, File original, File output, File blob, PatchOptions options) throws Exception {
            byte[] classes5 = readAsset(activity, "payload/classes5.dex");
            byte[] nativeLib = readAsset(activity, "payload/libanimegame_native_localify.so");
            byte[] icon = readAsset(activity, options.iconAsset);
            Set<String> skip = new HashSet<>();
            Collections.addAll(skip, "AndroidManifest.xml", CLASSES5_PATH, LIB_PATH, BLOB_PATH);
            File tempDir = new File(output.getParentFile(), "zip-stream-work");
            deleteDir(tempDir);
            if (!tempDir.mkdirs()) throw new IOException("zip temp dir create failed");

            try (ZipInputStream zin = new ZipInputStream(new FileInputStream(original));
                 AlignedZipWriter zout = new AlignedZipWriter(output)) {
                ZipEntry e;
                int entryCount = 0;
                while ((e = zin.getNextEntry()) != null) {
                    String name = e.getName();
                    if (name.startsWith("META-INF/") || skip.contains(name)) continue;
                    int method = e.getMethod() == ZipEntry.STORED ? ZipEntry.STORED : ZipEntry.DEFLATED;
                    if (name.equals("resources.arsc")) {
                        zout.add(name, patchTitle(readAll(zin), options.appTitle), method);
                    } else if (isIconEntry(name)) {
                        zout.add(name, icon, method);
                    } else {
                        zout.addStream(name, zin, method, tempDir);
                    }
                    entryCount++;
                    if (entryCount % 25 == 0 && activity instanceof MainActivity) {
                        ((MainActivity) activity).progress(700 + Math.min(120, entryCount / 3), "APK 항목 패치 중 " + entryCount + "개");
                    }
                }
                byte[] manifest;
                try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(original)) {
                    manifest = patchManifest(readAll(zf.getInputStream(zf.getEntry("AndroidManifest.xml"))));
                }
                zout.add("AndroidManifest.xml", manifest, ZipEntry.DEFLATED);
                zout.add(CLASSES5_PATH, classes5, ZipEntry.DEFLATED);
                zout.add(LIB_PATH, nativeLib, ZipEntry.STORED);
                zout.addFile(BLOB_PATH, blob, ZipEntry.STORED, tempDir);
            } finally {
                deleteDir(tempDir);
            }
        }

        private static boolean isIconEntry(String name) {
            String low = name.toLowerCase();
            return LAUNCHER_ICON_ENTRIES.contains(name)
                    || low.endsWith("/ic_launcher.png")
                    || low.endsWith("/app_icon.png")
                    || low.endsWith("/icon.png");
        }

        private static byte[] patchTitle(byte[] data, String title) {
            byte[] out = data.clone();
            replaceSameLength(out, "崩坏学园2".getBytes(StandardCharsets.UTF_16LE), title.getBytes(StandardCharsets.UTF_16LE));
            replaceSameLength(out, "崩坏学园2".getBytes(StandardCharsets.UTF_8), title.getBytes(StandardCharsets.UTF_8));
            return out;
        }

        private static void replaceSameLength(byte[] data, byte[] oldBytes, byte[] newBytes) {
            if (oldBytes.length != newBytes.length) return;
            int pos = indexOf(data, oldBytes);
            while (pos >= 0) {
                System.arraycopy(newBytes, 0, data, pos, newBytes.length);
                pos = indexOf(data, oldBytes, pos + oldBytes.length);
            }
        }

        static void sign(Activity activity, File unsigned, File signed) throws Exception {
            byte[] keyBytes = readAsset(activity, "signing/app_signing.pk8.der");
            byte[] certBytes = readAsset(activity, "signing/app_signing.x509.der");
            PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            X509Certificate cert;
            try (InputStream in = new java.io.ByteArrayInputStream(certBytes)) {
                cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
            }
            SignerConfig signer = new SignerConfig.Builder("app_signing", key, Collections.singletonList(cert)).build();
            new ApkSigner.Builder(Collections.singletonList(signer))
                    .setInputApk(unsigned)
                    .setOutputApk(signed)
                    .setMinSdkVersion(26)
                    .setV1SigningEnabled(false)
                    .setV2SigningEnabled(true)
                    .setV3SigningEnabled(true)
                    .build()
                    .sign();
        }

        private static byte[] readAsset(Activity activity, String name) throws IOException {
            try (InputStream in = activity.getAssets().open(name)) {
                return readAll(in);
            }
        }

        private static byte[] patchManifest(byte[] data) {
            byte[] oldBytes = utf16le(OLD_APP);
            byte[] newBytes = utf16le(NEW_APP);
            int off = indexOf(data, oldBytes);
            if (off < 0) {
                if (indexOf(data, newBytes) >= 0) return data;
                throw new IllegalStateException("AndroidManifest.xml에서 " + OLD_APP + "를 찾지 못했습니다.");
            }
            byte[] out = data.clone();
            int oldLen = (out[off - 2] & 0xff) | ((out[off - 1] & 0xff) << 8);
            if (oldLen != OLD_APP.length()) throw new IllegalStateException("Manifest string length mismatch: " + oldLen);
            out[off - 2] = (byte) (NEW_APP.length() & 0xff);
            out[off - 1] = (byte) ((NEW_APP.length() >>> 8) & 0xff);
            System.arraycopy(newBytes, 0, out, off, newBytes.length);
            for (int i = off + newBytes.length; i < off + oldBytes.length + 2; i++) out[i] = 0;
            return out;
        }

        private static byte[] utf16le(String s) {
            return s.getBytes(StandardCharsets.UTF_16LE);
        }

        private static int indexOf(byte[] haystack, byte[] needle) {
            return indexOf(haystack, needle, 0);
        }

        private static int indexOf(byte[] haystack, byte[] needle, int start) {
            outer:
            for (int i = start; i <= haystack.length - needle.length; i++) {
                for (int j = 0; j < needle.length; j++) {
                    if (haystack[i + j] != needle[j]) continue outer;
                }
                return i;
            }
            return -1;
        }
    }

    static final class AlignedZipWriter implements Closeable {
        private final DataOutputStream out;
        private final ArrayList<CentralEntry> central = new ArrayList<>();
        private long offset = 0;

        AlignedZipWriter(File file) throws IOException {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            out = new DataOutputStream(new FileOutputStream(file));
        }

        void add(String name, byte[] data, int method) throws IOException {
            byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
            CRC32 crc32 = new CRC32();
            crc32.update(data);
            long crc = crc32.getValue();
            byte[] payload = method == ZipEntry.STORED ? data : deflate(data);
            int alignment = name.equals("resources.arsc") || name.endsWith(".so") || name.equals(BLOB_PATH) ? 4 : 1;
            byte[] extra = paddingExtra(offset, nameBytes.length, alignment);
            long localOffset = offset;
            writeInt(0x04034b50);
            writeShort(20);
            writeShort(0x0800);
            writeShort(method == ZipEntry.STORED ? 0 : 8);
            writeShort(0);
            writeShort(0);
            writeInt((int) crc);
            writeInt(payload.length);
            writeInt(data.length);
            writeShort(nameBytes.length);
            writeShort(extra.length);
            write(nameBytes);
            write(extra);
            write(payload);
            central.add(new CentralEntry(nameBytes, extra, method, crc, payload.length, data.length, localOffset));
        }

        void addFile(String name, File file, int method, File tempDir) throws IOException {
            try (InputStream in = new FileInputStream(file)) {
                addStream(name, in, method, tempDir);
            }
        }

        void addStream(String name, InputStream data, int method, File tempDir) throws IOException {
            byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
            File payloadFile = File.createTempFile("payload-", ".bin", tempDir);
            CRC32 crc32 = new CRC32();
            long size = 0;
            long compressedSize;

            if (method == ZipEntry.STORED) {
                try (FileOutputStream fos = new FileOutputStream(payloadFile)) {
                    size = copyForZip(data, fos, crc32);
                }
                compressedSize = size;
            } else {
                Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
                try (FileOutputStream fos = new FileOutputStream(payloadFile);
                     DeflaterOutputStream dos = new DeflaterOutputStream(fos, deflater, 1024 * 128)) {
                    size = copyForZip(data, dos, crc32);
                } finally {
                    deflater.end();
                }
                compressedSize = payloadFile.length();
            }

            int alignment = name.equals("resources.arsc") || name.endsWith(".so") || name.equals(BLOB_PATH) ? 4 : 1;
            byte[] extra = paddingExtra(offset, nameBytes.length, alignment);
            long localOffset = offset;
            writeInt(0x04034b50);
            writeShort(20);
            writeShort(0x0800);
            writeShort(method == ZipEntry.STORED ? 0 : 8);
            writeShort(0);
            writeShort(0);
            writeInt((int) crc32.getValue());
            writeInt((int) compressedSize);
            writeInt((int) size);
            writeShort(nameBytes.length);
            writeShort(extra.length);
            write(nameBytes);
            write(extra);
            try (InputStream in = new FileInputStream(payloadFile)) {
                copyToOutput(in);
            } finally {
                payloadFile.delete();
            }
            central.add(new CentralEntry(nameBytes, extra, method, crc32.getValue(), (int) compressedSize, (int) size, localOffset));
        }

        @Override
        public void close() throws IOException {
            long centralOffset = offset;
            for (CentralEntry e : central) {
                writeInt(0x02014b50);
                writeShort(20);
                writeShort(20);
                writeShort(0x0800);
                writeShort(e.method == ZipEntry.STORED ? 0 : 8);
                writeShort(0);
                writeShort(0);
                writeInt((int) e.crc);
                writeInt(e.compressedSize);
                writeInt(e.size);
                writeShort(e.name.length);
                writeShort(e.extra.length);
                writeShort(0);
                writeShort(0);
                writeShort(0);
                writeInt(0);
                writeInt((int) e.localOffset);
                write(e.name);
                write(e.extra);
            }
            long centralSize = offset - centralOffset;
            writeInt(0x06054b50);
            writeShort(0);
            writeShort(0);
            writeShort(central.size());
            writeShort(central.size());
            writeInt((int) centralSize);
            writeInt((int) centralOffset);
            writeShort(0);
            out.close();
        }

        private static byte[] deflate(byte[] data) throws IOException {
            Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            while (!deflater.finished()) {
                int n = deflater.deflate(buf);
                bos.write(buf, 0, n);
            }
            deflater.end();
            return bos.toByteArray();
        }

        private static long copyForZip(InputStream in, java.io.OutputStream out, CRC32 crc32) throws IOException {
            byte[] buf = new byte[1024 * 128];
            long size = 0;
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
                crc32.update(buf, 0, n);
                size += n;
            }
            return size;
        }

        private static byte[] paddingExtra(long localOffset, int nameLen, int alignment) {
            if (alignment <= 1) return new byte[0];
            long dataStart = localOffset + 30L + nameLen;
            int pad = (int) ((alignment - (dataStart % alignment)) % alignment);
            if (pad != 0 && pad < 4) pad += alignment;
            if (pad == 0) return new byte[0];
            byte[] extra = new byte[pad];
            extra[0] = 0x35;
            extra[1] = (byte) 0xd9;
            int payloadLen = pad - 4;
            extra[2] = (byte) (payloadLen & 0xff);
            extra[3] = (byte) ((payloadLen >>> 8) & 0xff);
            return extra;
        }

        private void write(byte[] bytes) throws IOException {
            out.write(bytes);
            offset += bytes.length;
        }

        private void copyToOutput(InputStream in) throws IOException {
            byte[] buf = new byte[1024 * 128];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
                offset += n;
            }
        }

        private void writeShort(int value) throws IOException {
            out.writeByte(value & 0xff);
            out.writeByte((value >>> 8) & 0xff);
            offset += 2;
        }

        private void writeInt(int value) throws IOException {
            out.writeByte(value & 0xff);
            out.writeByte((value >>> 8) & 0xff);
            out.writeByte((value >>> 16) & 0xff);
            out.writeByte((value >>> 24) & 0xff);
            offset += 4;
        }

        static final class CentralEntry {
            final byte[] name;
            final byte[] extra;
            final int method;
            final long crc;
            final int compressedSize;
            final int size;
            final long localOffset;

            CentralEntry(byte[] name, byte[] extra, int method, long crc, int compressedSize, int size, long localOffset) {
                this.name = name;
                this.extra = extra;
                this.method = method;
                this.crc = crc;
                this.compressedSize = compressedSize;
                this.size = size;
                this.localOffset = localOffset;
            }
        }
    }
}
