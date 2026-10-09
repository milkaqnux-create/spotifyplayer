package pl.mediahud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CoverTexture {
    public static final Identifier ID = Identifier.of("mediahud", "cover");
    private static boolean loaded = false;

    public static boolean loaded() {
        return loaded;
    }

    private static void clear() {
        if (loaded) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(ID);
            loaded = false;
        }
    }

    /** Ustawia teksture wg trybu z configu. Wywolywac na watku klienta. */
    public static void apply() {
        HudConfig c = HudConfig.get();
        clear();
        try {
            if ("off".equals(c.coverMode)) return;
            if ("custom".equals(c.coverMode)) {
                if (!c.customCover.isEmpty()) register(readAnyImage(Path.of(c.customCover)));
                return;
            }
            String auto = MediaController.autoCover;
            if (auto == null || auto.isEmpty()) return;
            try (InputStream in = Files.newInputStream(Path.of(auto))) {
                register(NativeImage.read(in));
            }
        } catch (Exception e) {
            MediaHudMod.LOG.warn("Media HUD: nie udalo sie wczytac okladki", e);
        }
    }

    private static void register(NativeImage img) {
        MinecraftClient.getInstance().getTextureManager().registerTexture(ID, new NativeImageBackedTexture(img));
        loaded = true;
        MediaHudMod.LOG.info("Media HUD: okladka wczytana ({}x{})", img.getWidth(), img.getHeight());
    }

    /** Dowolny obraz (png/jpg/bmp/gif) -> kwadrat 128x128 -> NativeImage. */
    private static NativeImage readAnyImage(Path p) throws Exception {
        BufferedImage src = ImageIO.read(p.toFile());
        if (src == null) throw new IllegalArgumentException("nieobslugiwany format obrazu");
        int side = Math.min(src.getWidth(), src.getHeight());
        BufferedImage crop = src.getSubimage((src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
        BufferedImage dst = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(crop, 0, 0, 128, 128, null);
        g.dispose();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ImageIO.write(dst, "png", bo);
        return NativeImage.read(new ByteArrayInputStream(bo.toByteArray()));
    }
}
