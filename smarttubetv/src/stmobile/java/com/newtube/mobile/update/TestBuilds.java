package com.newtube.mobile.update;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.smartyoutubetv2.tv.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Test builds (docs/releases/TEST-BUILDS.md): the pre-releases {@code test-<branch>} that
 * .github/workflows/test-build.yml publishes for a labelled pull request. Settings > About > Test
 * builds lists them, downloads one and hands it to Android's installer, exactly as the update sheet
 * does for a release. A test build has the same versionCode as the latest {@code dn-*} release, so it
 * installs over it and the release installs back over it ("Back to the release").
 *
 * <p>Main thread only; the network and the download run on one worker thread.</p>
 */
public final class TestBuilds {
    private static final String TAG = "TestBuilds";
    private static final String API = "https://api.github.com/repos/danielnoam/ReTube/releases?per_page=60";
    private static final String DIR = "test-builds";

    public enum Load { IDLE, LOADING, READY, FAILED }
    public enum Dl { NONE, DOWNLOADING, READY, FAILED }

    /** One pre-release, as its notes' first lines describe it. */
    public static final class Build {
        public final String tag, title, branch, commit, pr, versionName;
        public final int versionCode;
        @Nullable public final String apkUrl;
        public final long apkSize;
        /** When the APK was built (its upload time, ms since the epoch); 0 when GitHub gave none. */
        public final long createdAt;

        Build(String tag, String title, String branch, String commit, String pr, String versionName,
              int versionCode, @Nullable String apkUrl, long apkSize, long createdAt) {
            this.tag = tag;
            this.title = title;
            this.branch = branch;
            this.commit = commit;
            this.pr = pr;
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.apkUrl = apkUrl;
            this.apkSize = apkSize;
            this.createdAt = createdAt;
        }

        public String shortCommit() {
            return commit.length() > 7 ? commit.substring(0, 7) : commit;
        }
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    private static Load sLoad = Load.IDLE;
    private static List<Build> sBuilds = new ArrayList<>();
    @Nullable private static Build sStable;
    private static Dl sDl = Dl.NONE;
    @Nullable private static String sDlTag;
    private static long sBytes;
    @Nullable private static File sFile;
    @Nullable private static Runnable sListener;

    private TestBuilds() {
    }

    /** True when this APK was built by test-build.yml (it carries the branch it came from). */
    public static boolean isTestBuild() {
        return !BuildConfig.TEST_BRANCH.isEmpty();
    }

    public static String installedBranch() {
        return BuildConfig.TEST_BRANCH;
    }

    public static String installedCommit() {
        String c = BuildConfig.TEST_COMMIT;
        return c.length() > 7 ? c.substring(0, 7) : c;
    }

    // ---------------------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------------------

    public static Load loadState() { return sLoad; }
    public static List<Build> builds() { return sBuilds; }
    @Nullable public static Build stable() { return sStable; }
    public static Dl dlState() { return sDl; }
    @Nullable public static String dlTag() { return sDlTag; }
    public static long dlBytes() { return sBytes; }

    /** The Test builds page asks to be rebuilt when something here changes (null to stop). */
    public static void setListener(@Nullable Runnable listener) {
        sListener = listener;
    }

    private static void changed() {
        if (sListener != null) {
            sListener.run();
        }
    }

    // ---------------------------------------------------------------------------------
    // List
    // ---------------------------------------------------------------------------------

    public static void load() {
        if (sLoad == Load.LOADING) {
            return;
        }
        sLoad = Load.LOADING;
        changed();
        WORKER.execute(() -> {
            List<Build> builds = null;
            Build stable = null;
            try {
                String json = httpString(API);
                builds = parseBuilds(json, supportedAbis());
                stable = parseStable(json, supportedAbis());
            } catch (Exception e) {
                Log.e(TAG, "listing test builds failed: " + e);
            }
            List<Build> result = builds;
            Build newest = stable;
            MAIN.post(() -> {
                if (result == null) {
                    sLoad = Load.FAILED;
                } else {
                    sBuilds = result;
                    sStable = newest;
                    sLoad = Load.READY;
                }
                changed();
            });
        });
    }

    private static String[] supportedAbis() {
        return android.os.Build.SUPPORTED_ABIS;
    }

    /** The pre-releases tagged test-*, newest first as GitHub lists them. Pure: tested without Android. */
    static List<Build> parseBuilds(String json, String[] abis) throws Exception {
        List<Build> out = new ArrayList<>();
        JSONArray all = new JSONArray(json);
        for (int i = 0; i < all.length(); i++) {
            JSONObject r = all.getJSONObject(i);
            String tag = r.optString("tag_name");
            if (!r.optBoolean("prerelease") || r.optBoolean("draft") || !tag.startsWith("test-")) {
                continue;
            }
            String[] notes = noteFields(r.optString("body"));
            JSONObject asset = pickAsset(r.optJSONArray("assets"), abis);
            String title = r.optString("name").replaceFirst("^Test: ", "");
            out.add(new Build(tag, title.isEmpty() ? tag : title, notes[2].isEmpty() ? tag.substring(5) : notes[2],
                    notes[3], notes[4], notes[0], parseInt(notes[1]),
                    asset != null ? asset.optString("browser_download_url") : null,
                    asset != null ? asset.optLong("size") : 0, builtAt(asset, r)));
        }
        return out;
    }

    /** The newest published dn-* release, for "Back to the release". */
    static Build parseStable(String json, String[] abis) throws Exception {
        JSONArray all = new JSONArray(json);
        for (int i = 0; i < all.length(); i++) {
            JSONObject r = all.getJSONObject(i);
            String tag = r.optString("tag_name");
            if (r.optBoolean("prerelease") || r.optBoolean("draft") || !tag.matches("dn-\\d+\\.\\d+\\.\\d+-\\d+")) {
                continue;
            }
            JSONObject asset = pickAsset(r.optJSONArray("assets"), abis);
            // 11701 * 100 + N, as build.gradle: the code a test build of this release carries.
            int n = parseInt(tag.substring(tag.lastIndexOf('-') + 1));
            String base = tag.substring(3, tag.lastIndexOf('-'));
            String[] v = base.split("\\.");
            int code = (parseInt(v[0]) * 10000 + parseInt(v[1]) * 100 + parseInt(v[2])) * 100 + n;
            return new Build(tag, tag.substring(3), "", "", "", tag.substring(3), code,
                    asset != null ? asset.optString("browser_download_url") : null, asset != null ? asset.optLong("size") : 0,
                    builtAt(asset, r));
        }
        return null;
    }

    /** The APK's own upload time, else the release's publish time, else 0. */
    static long builtAt(@Nullable JSONObject asset, JSONObject release) {
        long t = asset != null ? parseTime(asset.optString("created_at")) : 0;
        return t > 0 ? t : parseTime(release.optString("published_at"));
    }

    /** GitHub's "2026-11-28T09:13:16Z" as ms since the epoch; 0 when it isn't one. */
    static long parseTime(@Nullable String iso) {
        if (iso == null || iso.isEmpty()) {
            return 0;
        }
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return f.parse(iso).getTime();
        } catch (java.text.ParseException e) {
            return 0;
        }
    }

    /** ReTube_<ver>_<abi>.apk for the phone's first supported ABI, else the universal APK. */
    @Nullable
    static JSONObject pickAsset(@Nullable JSONArray assets, String[] abis) {
        if (assets == null) {
            return null;
        }
        List<String> wanted = new ArrayList<>();
        for (String abi : abis) {
            wanted.add("_" + abi + ".apk");
        }
        wanted.add("_universal.apk");
        for (String suffix : wanted) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.optJSONObject(i);
                if (a != null && a.optString("name").startsWith("ReTube_") && a.optString("name").endsWith(suffix)) {
                    return a;
                }
            }
        }
        return null;
    }

    /** version, code, branch, commit, pr from the notes' leading "key: value" lines. */
    static String[] noteFields(String body) {
        String[] f = {"", "", "", "", ""};
        String[] keys = {"version", "code", "branch", "commit", "pr"};
        for (String line : body.split("\r?\n")) {
            if (line.trim().isEmpty()) {
                break;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                break;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            for (int k = 0; k < keys.length; k++) {
                if (keys[k].equals(key)) {
                    f[k] = line.substring(colon + 1).trim();
                }
            }
        }
        return f;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---------------------------------------------------------------------------------
    // Download and install
    // ---------------------------------------------------------------------------------

    /** Downloads {@code build}'s APK (a test build, or the stable release for Back to the release). */
    public static void download(Context context, Build build) {
        if (sDl == Dl.DOWNLOADING || build.apkUrl == null) {
            return;
        }
        Context app = context.getApplicationContext();
        sDl = Dl.DOWNLOADING;
        sDlTag = build.tag;
        sBytes = 0;
        sFile = null;
        changed();
        WORKER.execute(() -> {
            File dir = new File(app.getCacheDir(), DIR);
            File[] old = dir.listFiles();
            if (old != null) {
                for (File f : old) {
                    f.delete();
                }
            }
            dir.mkdirs();
            File part = new File(dir, "build.apk.part");
            File done = new File(dir, "build.apk");
            boolean ok = false;
            try {
                HttpURLConnection c = open(build.apkUrl);
                try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(part)) {
                    byte[] buf = new byte[64 * 1024];
                    long total = 0;
                    long lastPost = 0;
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        total += n;
                        if (total - lastPost > 512 * 1024) {
                            lastPost = total;
                            long soFar = total;
                            MAIN.post(() -> {
                                sBytes = soFar;
                                changed();
                            });
                        }
                    }
                }
                ok = part.length() > 0 && (build.apkSize <= 0 || part.length() == build.apkSize) && part.renameTo(done);
            } catch (Exception e) {
                Log.e(TAG, "downloading " + build.tag + " failed: " + e);
            }
            boolean success = ok;
            MAIN.post(() -> {
                sDl = success ? Dl.READY : Dl.FAILED;
                sFile = success ? done : null;
                changed();
            });
        });
    }

    public static boolean canInstallPackages(Context context) {
        return android.os.Build.VERSION.SDK_INT < 26 || context.getPackageManager().canRequestPackageInstalls();
    }

    /** Opens Android's "install unknown apps" screen for ReTube (the user taps the build again after). */
    public static void openInstallPermission(Activity activity) {
        try {
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())));
        } catch (ActivityNotFoundException ignored) {
            // No such screen: the installer asks in its own way
        }
    }

    /** Hands the downloaded APK to Android's installer, the way AppUpdates.install does. */
    public static boolean install(Activity activity) {
        if (sFile == null || !sFile.isFile()) {
            return false;
        }
        Uri uri = FileHelpers.getFileUri(activity, sFile);
        if (uri == null) {
            return false;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        String installer = AppUpdates.systemInstaller(activity.getPackageManager(), intent);
        if (installer != null) {
            intent.setPackage(installer);
        }
        try {
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.e(TAG, "no installer for the test build: " + e);
            return false;
        }
    }

    // ---------------------------------------------------------------------------------
    // HTTP
    // ---------------------------------------------------------------------------------

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(30_000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setInstanceFollowRedirects(true);
        if (c.getResponseCode() != 200) {
            throw new java.io.IOException("HTTP " + c.getResponseCode() + " for " + url);
        }
        return c;
    }

    private static String httpString(String url) throws Exception {
        try (InputStream in = open(url).getInputStream()) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        }
    }
}
