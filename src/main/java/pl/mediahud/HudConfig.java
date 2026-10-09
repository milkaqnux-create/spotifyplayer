package pl.mediahud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public final class HudConfig {
    public int x = 6;
    public int y = 6;
    public float scale = 1.0f;
    public float opacity = 0.85f;
    public boolean rounded = true;
    /** auto | off | custom */
    public String coverMode = "auto";
    public String customCover = "";
    public boolean lyrics = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("mediahud.json");
    private static HudConfig instance = new HudConfig();

    public static HudConfig get() {
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                HudConfig loaded = GSON.fromJson(Files.readString(FILE), HudConfig.class);
                if (loaded != null) instance = loaded;
            }
        } catch (Exception e) {
            MediaHudMod.LOG.warn("Nie udalo sie wczytac configu", e);
        }
        instance.scale = Math.max(0.5f, Math.min(3.0f, instance.scale));
        instance.opacity = Math.max(0.25f, Math.min(1.0f, instance.opacity));
        if (instance.coverMode == null) instance.coverMode = "auto";
        if (instance.customCover == null) instance.customCover = "";
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(instance));
        } catch (Exception e) {
            MediaHudMod.LOG.warn("Nie udalo sie zapisac configu", e);
        }
    }
}
