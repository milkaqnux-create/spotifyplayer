package pl.mediahud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class MediaController {
    public static volatile MediaState state;
    /** Sciezka okladki z helpera (tryb auto). */
    public static volatile String autoCover = "";
    private static volatile Process process;
    private static volatile String lastCover = "";
    private static volatile Path cmdFile;
    private static boolean gotFirst = false;

    public static void start() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) {
            MediaHudMod.LOG.warn("Media HUD: ta wersja dziala tylko na Windows.");
            return;
        }
        Thread t = new Thread(MediaController::loop, "mediahud-helper");
        t.setDaemon(true);
        t.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Process p = process;
            if (p != null) p.destroyForcibly();
        }));
    }

    private static void loop() {
        while (true) {
            try {
                runOnce();
            } catch (Exception e) {
                MediaHudMod.LOG.warn("Helper padl", e);
            }
            state = null;
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ie) {
                return;
            }
        }
    }

    private static void runOnce() throws Exception {
        Path dir = Files.createTempDirectory("mediahud");
        Path script = dir.resolve("helper.ps1");
        try (InputStream in = MediaController.class.getResourceAsStream("/assets/mediahud/helper.ps1")) {
            Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
        }
        cmdFile = dir.resolve("cmd.txt");
        ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe", "-MTA", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", script.toString(), "-CoverDir", dir.toString(),
                "-CmdFile", cmdFile.toString(),
                "-ParentPid", String.valueOf(ProcessHandle.current().pid()));
        MediaHudMod.LOG.info("Media HUD: uruchamiam helper ({})", dir);
        Process p = pb.start();
        process = p;
        Thread et = new Thread(() -> {
            try (BufferedReader er = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = er.readLine()) != null) MediaHudMod.LOG.info("Media HUD helper: {}", l);
            } catch (Exception ignored) {
            }
        }, "mediahud-helper-err");
        et.setDaemon(true);
        et.start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                try {
                    handle(line);
                } catch (Exception e) {
                    MediaHudMod.LOG.debug("Zla linia z helpera: {}", line);
                }
            }
        }
        p.waitFor();
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return (e == null || e.isJsonNull()) ? "" : e.getAsString();
    }

    private static long lng(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return (e == null || e.isJsonNull()) ? 0L : e.getAsLong();
    }

    private static void handle(String line) {
        line = line.trim();
        if (line.isEmpty()) return;
        JsonObject o = JsonParser.parseString(line).getAsJsonObject();
        if (o.has("none")) {
            state = null;
            return;
        }
        String cover = str(o, "cover");
        boolean playing = o.has("playing") && o.get("playing").getAsBoolean();
        MediaState s = new MediaState(str(o, "title"), str(o, "artist"), playing, lng(o, "pos"), lng(o, "dur"));
        state = s;
        if (!gotFirst) {
            gotFirst = true;
            MediaHudMod.LOG.info("Media HUD: wykryto utwor: {} - {}", s.artist, s.title);
        }
        if (HudConfig.get().lyrics) LyricsManager.ensure(s);
        if (!cover.equals(lastCover)) {
            lastCover = cover;
            autoCover = cover;
            if ("auto".equals(HudConfig.get().coverMode)) {
                MinecraftClient.getInstance().execute(CoverTexture::apply);
            }
        }
    }

    /** prev | toggle | next */
    public static synchronized void send(String cmd) {
        Path f = cmdFile;
        if (f == null) return;
        try {
            Files.writeString(f, cmd + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }
}
