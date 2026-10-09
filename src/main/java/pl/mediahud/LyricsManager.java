package pl.mediahud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pobiera zsynchronizowane teksty z lrclib.net (darmowe, bez klucza). */
public final class LyricsManager {
    public record Line(long ms, String text) {}

    private static final String BASE = "https://lrclib.net/api";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static final Pattern TS = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");

    public static volatile List<Line> lines = List.of();
    /** "" | loading | ok | none */
    public static volatile String status = "";
    private static volatile String key = "";

    public static void ensure(MediaState s) {
        if (s == null) return;
        String k = s.artist + "|" + s.title;
        if (k.equals(key)) return;
        key = k;
        lines = List.of();
        status = "loading";
        long durSec = s.durMs / 1000;
        Thread t = new Thread(() -> fetch(k, s.artist, s.title, durSec), "mediahud-lyrics");
        t.setDaemon(true);
        t.start();
    }

    private static void fetch(String k, String artist, String title, long durSec) {
        List<Line> res = List.of();
        try {
            String b = get(BASE + "/get?artist_name=" + enc(artist) + "&track_name=" + enc(title)
                    + (durSec > 0 ? "&duration=" + durSec : ""));
            if (b != null) res = parseLrc(str(JsonParser.parseString(b).getAsJsonObject(), "syncedLyrics"));
            if (res.isEmpty()) res = search(artist, title);
            if (res.isEmpty()) {
                String clean = title.replaceAll("(?i)\\s*[-(\\[].*(remaster|live|version|edit|mono|stereo|mix).*$", "").trim();
                if (!clean.isEmpty() && !clean.equals(title)) res = search(artist, clean);
            }
        } catch (Exception e) {
            MediaHudMod.LOG.warn("Media HUD: blad pobierania tekstu: {}", e.toString());
        }
        if (!k.equals(key)) return;
        lines = res;
        status = res.isEmpty() ? "none" : "ok";
        MediaHudMod.LOG.info("Media HUD: tekst piosenki: {} ({} linii)", status, res.size());
    }

    private static List<Line> search(String artist, String title) throws Exception {
        String b = get(BASE + "/search?track_name=" + enc(title) + "&artist_name=" + enc(artist));
        if (b == null) return List.of();
        JsonArray arr = JsonParser.parseString(b).getAsJsonArray();
        for (JsonElement e : arr) {
            List<Line> l = parseLrc(str(e.getAsJsonObject(), "syncedLyrics"));
            if (!l.isEmpty()) return l;
        }
        return List.of();
    }

    private static String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "MediaHud-Minecraft-Mod/1.0")
                .GET().build();
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return r.statusCode() == 200 ? r.body() : null;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return (e == null || e.isJsonNull()) ? "" : e.getAsString();
    }

    private static List<Line> parseLrc(String lrc) {
        List<Line> out = new ArrayList<>();
        if (lrc == null || lrc.isBlank()) return out;
        for (String raw : lrc.split("\\r?\\n")) {
            Matcher m = TS.matcher(raw);
            List<Long> stamps = new ArrayList<>();
            int end = 0;
            while (m.find()) {
                long ms = Long.parseLong(m.group(1)) * 60_000L + Long.parseLong(m.group(2)) * 1000L;
                String f = m.group(3);
                if (f != null) {
                    long frac = Long.parseLong(f);
                    ms += f.length() == 1 ? frac * 100 : f.length() == 2 ? frac * 10 : frac;
                }
                stamps.add(ms);
                end = m.end();
            }
            if (stamps.isEmpty()) continue;
            String text = raw.substring(end).trim();
            for (long ms : stamps) out.add(new Line(ms, text));
        }
        out.sort(Comparator.comparingLong(Line::ms));
        return out;
    }

    /** Indeks ostatniej linii z czasem <= pos, albo -1. */
    public static int indexAt(List<Line> ls, long posMs) {
        int lo = 0, hi = ls.size() - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (ls.get(mid).ms() <= posMs) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }
}
