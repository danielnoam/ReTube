package com.newtube.mobile.stats;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What the Stats page shows, by local calendar day (days since 1970-01-01 in the phone's time
 * zone, see {@link StatsCalendar}). Not thread-safe: {@link LocalStats} guards it.
 */
public final class StatsData {
    /** About 13 months: enough for this month against the same month last year. */
    static final int KEEP_DAYS = 400;
    static final int MAX_CHANNELS = 300;

    static final class Day {
        long watchMs;
        /** Part of watchMs played from a downloaded file. */
        long offlineMs;
        int videos;
        int downloads;
    }

    public static final class Channel {
        public final String key;
        public String name;
        public long watchMs;
        public int videos;

        Channel(String key, String name) {
            this.key = key;
            this.name = name;
        }
    }

    /** Sums over a range of days. */
    public static final class Totals {
        public long watchMs;
        public long offlineMs;
        public int videos;
        public int downloads;

        public long streamedMs() {
            return watchMs - offlineMs;
        }
    }

    final TreeMap<Long, Day> days = new TreeMap<>();
    final Map<String, Channel> channels = new HashMap<>();
    long totalWatchMs;
    long totalOfflineMs;
    int totalVideos;
    int videoDownloads;
    int audioDownloads;
    /** Wall-clock time of the first thing recorded; 0 while nothing is. */
    long sinceMs;

    void addWatch(long day, long nowMs, @Nullable String channelId, @Nullable String channelName,
                  boolean offline, long ms, boolean countVideo) {
        if (ms <= 0 && !countVideo) {
            return;
        }
        touch(nowMs);
        Day d = day(day);
        d.watchMs += ms;
        totalWatchMs += ms;
        if (offline) {
            d.offlineMs += ms;
            totalOfflineMs += ms;
        }
        if (countVideo) {
            d.videos++;
            totalVideos++;
        }
        String key = channelId != null && !channelId.isEmpty() ? channelId : channelName;
        if (key != null && !key.isEmpty()) {
            Channel channel = channels.get(key);
            if (channel == null) {
                channel = new Channel(key, channelName != null ? channelName : key);
                channels.put(key, channel);
            } else if (channelName != null && !channelName.isEmpty()) {
                channel.name = channelName;
            }
            channel.watchMs += ms;
            if (countVideo) {
                channel.videos++;
            }
            trimChannels();
        }
        prune(day);
    }

    void addDownload(long day, long nowMs, boolean audio) {
        touch(nowMs);
        day(day).downloads++;
        if (audio) {
            audioDownloads++;
        } else {
            videoDownloads++;
        }
        prune(day);
    }

    /** {@code from} and {@code to} inclusive. */
    @NonNull
    public Totals sum(long from, long to) {
        Totals totals = new Totals();
        for (Day d : days.subMap(from, true, to, true).values()) {
            totals.watchMs += d.watchMs;
            totals.offlineMs += d.offlineMs;
            totals.videos += d.videos;
            totals.downloads += d.downloads;
        }
        return totals;
    }

    @NonNull
    public Totals allTime() {
        Totals totals = new Totals();
        totals.watchMs = totalWatchMs;
        totals.offlineMs = totalOfflineMs;
        totals.videos = totalVideos;
        totals.downloads = videoDownloads + audioDownloads;
        return totals;
    }

    public int getVideoDownloads() {
        return videoDownloads;
    }

    public int getAudioDownloads() {
        return audioDownloads;
    }

    public long getSinceMs() {
        return sinceMs;
    }

    public boolean isEmpty() {
        return sinceMs == 0;
    }

    /** Most watched first, by time. */
    @NonNull
    public List<Channel> topChannels(int count) {
        List<Channel> list = new ArrayList<>(channels.values());
        Collections.sort(list, (a, b) -> Long.compare(b.watchMs, a.watchMs));
        return list.size() > count ? new ArrayList<>(list.subList(0, count)) : list;
    }

    private void touch(long nowMs) {
        if (sinceMs == 0) {
            sinceMs = nowMs;
        }
    }

    private Day day(long day) {
        Day d = days.get(day);
        if (d == null) {
            d = new Day();
            days.put(day, d);
        }
        return d;
    }

    private void prune(long today) {
        while (!days.isEmpty() && days.firstKey() < today - KEEP_DAYS) {
            days.pollFirstEntry();
        }
    }

    /** Drops the least watched channels: a long tail of one-off videos isn't worth keeping. */
    private void trimChannels() {
        if (channels.size() <= MAX_CHANNELS) {
            return;
        }
        List<Channel> list = new ArrayList<>(channels.values());
        Collections.sort(list, (a, b) -> Long.compare(a.watchMs, b.watchMs));
        for (int i = 0; i < list.size() - MAX_CHANNELS; i++) {
            channels.remove(list.get(i).key);
        }
    }

    @NonNull
    JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("v", 1);
        json.put("since", sinceMs);
        json.put("watch", totalWatchMs);
        json.put("offline", totalOfflineMs);
        json.put("videos", totalVideos);
        json.put("dlVideo", videoDownloads);
        json.put("dlAudio", audioDownloads);
        JSONArray dayArray = new JSONArray();
        for (Map.Entry<Long, Day> entry : days.entrySet()) {
            Day d = entry.getValue();
            dayArray.put(new JSONArray().put(entry.getKey()).put(d.watchMs).put(d.offlineMs).put(d.videos).put(d.downloads));
        }
        json.put("days", dayArray);
        JSONArray channelArray = new JSONArray();
        for (Channel c : channels.values()) {
            channelArray.put(new JSONArray().put(c.key).put(c.name).put(c.watchMs).put(c.videos));
        }
        json.put("channels", channelArray);
        return json;
    }

    @NonNull
    static StatsData fromJson(@NonNull JSONObject json) {
        StatsData data = new StatsData();
        data.sinceMs = json.optLong("since");
        data.totalWatchMs = json.optLong("watch");
        data.totalOfflineMs = json.optLong("offline");
        data.totalVideos = json.optInt("videos");
        data.videoDownloads = json.optInt("dlVideo");
        data.audioDownloads = json.optInt("dlAudio");
        JSONArray dayArray = json.optJSONArray("days");
        if (dayArray != null) {
            for (int i = 0; i < dayArray.length(); i++) {
                JSONArray row = dayArray.optJSONArray(i);
                if (row == null || row.length() < 5) {
                    continue;
                }
                Day d = new Day();
                d.watchMs = row.optLong(1);
                d.offlineMs = row.optLong(2);
                d.videos = row.optInt(3);
                d.downloads = row.optInt(4);
                data.days.put(row.optLong(0), d);
            }
        }
        JSONArray channelArray = json.optJSONArray("channels");
        if (channelArray != null) {
            for (int i = 0; i < channelArray.length(); i++) {
                JSONArray row = channelArray.optJSONArray(i);
                if (row == null || row.length() < 4) {
                    continue;
                }
                Channel c = new Channel(row.optString(0), row.optString(1));
                c.watchMs = row.optLong(2);
                c.videos = row.optInt(3);
                data.channels.put(c.key, c);
            }
        }
        return data;
    }

    @NonNull
    StatsData copy() {
        try {
            return fromJson(toJson());
        } catch (JSONException e) {
            return new StatsData();
        }
    }
}
