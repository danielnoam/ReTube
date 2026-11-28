package com.newtube.mobile.stats;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.googlecommon.common.helpers.DefaultHeaders;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * YouTube's own category of a video ("Gaming", "Education"...), for Settings > Stats. The phone's
 * /player clients don't carry it (signed in, the WEB enrichment that would is skipped), so this is
 * one small extra request per watched video, the way NewPipe reads metadata: WEB /player asking
 * only for {@code microformat}. It goes out signed out (the plain shared client, no InnerTube
 * interceptors: no account, no visitor id) and only while stats are being kept. One request per
 * video per app run; a failure is just a video without a category.
 */
final class VideoCategories {
    private static final String TAG = VideoCategories.class.getSimpleName();
    private static final String URL = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
            + "&$fields=microformat.playerMicroformatRenderer.category";
    private static final int MAX_CACHED = 200;
    /**
     * The library's WEB client is internal to it. Any recent WEB version answers a metadata-only
     * request; if YouTube ever refuses this one, the cost is no categories, nothing else.
     */
    private static final String WEB_VERSION = "2.20260708.00.00";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "stats-category");
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** Video id to category ("" = asked, none). Main thread only. */
    private static final Map<String, String> CACHE = new LinkedHashMap<String, String>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_CACHED;
        }
    };
    /** Lookups on their way, with everyone waiting for each. */
    private static final Map<String, List<Consumer<String>>> PENDING = new HashMap<>();

    private VideoCategories() {
    }

    /** Known: the category, or "" for none. Not asked yet: null. */
    @Nullable
    static String cached(@NonNull String videoId) {
        return CACHE.get(videoId);
    }

    /** Asks once; {@code onResult} runs on the main thread with the category or "". Main thread only. */
    static void fetch(@NonNull String videoId, @NonNull Consumer<String> onResult) {
        String known = CACHE.get(videoId);
        if (known != null) {
            onResult.accept(known);
            return;
        }
        List<Consumer<String>> waiting = PENDING.get(videoId);
        if (waiting != null) {
            waiting.add(onResult);
            return;
        }
        waiting = new ArrayList<>();
        waiting.add(onResult);
        PENDING.put(videoId, waiting);
        EXECUTOR.execute(() -> {
            String category = load(videoId);
            MAIN.post(() -> {
                CACHE.put(videoId, category);
                List<Consumer<String>> callbacks = PENDING.remove(videoId);
                if (callbacks != null) {
                    for (Consumer<String> callback : callbacks) {
                        callback.accept(category);
                    }
                }
            });
        });
    }

    @NonNull
    private static String load(String videoId) {
        String body = "{\"context\":{\"client\":{\"clientName\":\"WEB\",\"clientVersion\":\""
                + WEB_VERSION + "\",\"hl\":\"" + Locale.getDefault().getLanguage() + "\"}},\"videoId\":\""
                + videoId.replace("\"", "") + "\",\"contentCheckOk\":true,\"racyCheckOk\":true}";
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", DefaultHeaders.USER_AGENT_WEB);
        headers.put("Origin", "https://www.youtube.com");
        headers.put("X-Youtube-Client-Name", "1");
        headers.put("X-Youtube-Client-Version", WEB_VERSION);
        try (okhttp3.Response response = OkHttpManager.instance().doPostRequest(URL, headers, body, "application/json")) {
            if (response == null || !response.isSuccessful() || response.body() == null) {
                return "";
            }
            JSONObject json = new JSONObject(response.body().string());
            JSONObject renderer = json.optJSONObject("microformat") != null
                    ? json.optJSONObject("microformat").optJSONObject("playerMicroformatRenderer") : null;
            return renderer != null ? renderer.optString("category", "") : "";
        } catch (Exception e) {
            Log.d(TAG, "No category for %s: %s", videoId, e.getMessage());
            return "";
        }
    }
}
