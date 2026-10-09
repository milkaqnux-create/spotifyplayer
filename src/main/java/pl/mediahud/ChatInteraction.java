package pl.mediahud;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * W czacie: LPM + przeciaganie = przesuwanie, PPM na panelu = menu ustawien,
 * LPM na przyciskach panelu = zmiana utworu. Panel jest rysowany tutaj, nad czatem.
 */
public final class ChatInteraction {
    private static boolean dragging, popup;
    private static int dragDX, dragDY, popX, popY;
    private static int slider; // 0 brak, 1 skala, 2 przezroczystosc
    private static int lastSw = 400, lastSh = 300;
    private static String msg = "";
    private static long msgUntil = 0;

    private static final int PW = 140, PH = 118, BAR_X = 6, BAR_W = 128;
    private static final int BTN0 = 47, BTN_STEP = 14, BTN_H = 11;

    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, sw, sh) -> {
            if (!(screen instanceof ChatScreen)) return;
            ScreenMouseEvents.allowMouseClick(screen).register((scr, mx, my, btn) -> onClick(mx, my, btn));
            ScreenMouseEvents.beforeMouseRelease(screen).register((scr, mx, my, btn) -> onRelease());
            ScreenEvents.afterRender(screen).register((scr, ctx, mx, my, delta) -> onRender(ctx, mx, my, scr.width, scr.height));
            ScreenEvents.remove(screen).register(scr -> {
                if (dragging || slider != 0) HudConfig.save();
                dragging = false;
                popup = false;
                slider = 0;
            });
        });
    }

    private static void say(String m) {
        msg = m;
        msgUntil = System.currentTimeMillis() + 3500;
    }

    private static boolean onClick(double mx, double my, int btn) {
        HudConfig c = HudConfig.get();
        if (popup) {
            if (mx >= popX && mx <= popX + PW && my >= popY && my <= popY + PH) {
                if (btn == 0) {
                    if (my >= popY + BTN0) {
                        int row = (int) ((my - popY - BTN0) / BTN_STEP);
                        if (((my - popY - BTN0) % BTN_STEP) < BTN_H) onButton(row);
                    } else {
                        slider = my < popY + 25 ? 1 : 2;
                        applySlider(mx);
                    }
                }
                return false;
            }
            popup = false;
        }
        if (MediaHud.inside(mx, my)) {
            if (btn == 1) {
                popup = true;
                popX = (int) mx;
                popY = (int) my;
                return false;
            }
            if (btn == 0) {
                String cmd = MediaHud.buttonAt(mx, my);
                if (cmd != null) {
                    MediaController.send(cmd);
                    return false;
                }
                dragging = true;
                dragDX = (int) mx - c.x;
                dragDY = (int) my - c.y;
                return false;
            }
        }
        return true;
    }

    private static void onButton(int row) {
        HudConfig c = HudConfig.get();
        switch (row) {
            case 0 -> c.rounded = !c.rounded;
            case 1 -> {
                switch (c.coverMode) {
                    case "auto" -> c.coverMode = "off";
                    case "off" -> {
                        if (c.customCover.isEmpty()) {
                            c.coverMode = "auto";
                            say("Brak własnego obrazu - wklej ścieżkę");
                        } else {
                            c.coverMode = "custom";
                        }
                    }
                    default -> c.coverMode = "auto";
                }
                CoverTexture.apply();
            }
            case 2 -> pastePath();
            case 3 -> {
                c.lyrics = !c.lyrics;
                if (c.lyrics) LyricsManager.ensure(MediaController.state);
            }
            default -> {
                return;
            }
        }
        HudConfig.save();
    }

    private static void pastePath() {
        HudConfig c = HudConfig.get();
        String clip = MinecraftClient.getInstance().keyboard.getClipboard();
        if (clip == null) clip = "";
        clip = clip.trim();
        if (clip.length() >= 2 && clip.startsWith("\"") && clip.endsWith("\"")) clip = clip.substring(1, clip.length() - 1);
        try {
            Path p = Path.of(clip);
            if (!Files.isRegularFile(p)) {
                say("Nie znaleziono pliku (skopiuj ścieżkę)");
                return;
            }
            c.customCover = p.toString();
            c.coverMode = "custom";
            CoverTexture.apply();
            say(CoverTexture.loaded() ? "Ustawiono własną okładkę" : "Nie da się odczytać obrazu");
        } catch (Exception e) {
            say("Zła ścieżka w schowku");
        }
    }

    private static void onRelease() {
        if (dragging || slider != 0) HudConfig.save();
        dragging = false;
        slider = 0;
    }

    private static void applySlider(double mx) {
        HudConfig c = HudConfig.get();
        float f = (float) Math.max(0, Math.min(1, (mx - (popX + BAR_X)) / BAR_W));
        if (slider == 1) {
            c.scale = Math.round((0.5f + f * 2.5f) * 20f) / 20f;
            clampPos(c);
        } else if (slider == 2) {
            c.opacity = Math.round((0.25f + f * 0.75f) * 100f) / 100f;
        }
    }

    private static void clampPos(HudConfig c) {
        c.x = Math.max(0, Math.min(c.x, Math.max(0, lastSw - MediaHud.width())));
        c.y = Math.max(0, Math.min(c.y, Math.max(0, lastSh - MediaHud.height())));
    }

    private static void onRender(DrawContext ctx, int mx, int my, int sw, int sh) {
        lastSw = sw;
        lastSh = sh;
        HudConfig c = HudConfig.get();

        if (dragging) {
            c.x = mx - dragDX;
            c.y = my - dragDY;
            clampPos(c);
        }
        if (slider != 0) applySlider(mx);

        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 400);

        // panel rysujemy tutaj, zeby w czacie zawsze byl na wierzchu
        MediaHud.render(ctx);

        boolean hover = MediaHud.inside(mx, my);
        if (hover || dragging) {
            ctx.drawBorder(c.x - 1, c.y - 1, MediaHud.width() + 2, MediaHud.height() + 2, 0xAAFFFFFF);
        }

        if (popup) {
            popX = Math.max(0, Math.min(popX, sw - PW));
            popY = Math.max(0, Math.min(popY, sh - PH));
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            ctx.getMatrices().translate(0, 0, 100);
            ctx.fill(popX, popY, popX + PW, popY + PH, 0xEE141414);
            ctx.drawBorder(popX, popY, PW, PH, 0xFF555555);

            MediaHud.drawRaw(ctx, tr, String.format("Skala: %.2fx", c.scale), popX + 6, popY + 4, 0xFFFFFFFF);
            drawBar(ctx, popX + BAR_X, popY + 15, (c.scale - 0.5f) / 2.5f);

            MediaHud.drawRaw(ctx, tr, "Przezroczystość: " + Math.round(c.opacity * 100) + "%", popX + 6, popY + 27, 0xFFFFFFFF);
            drawBar(ctx, popX + BAR_X, popY + 38, (c.opacity - 0.25f) / 0.75f);

            String cover = switch (c.coverMode) {
                case "off" -> "Okładka: wyłączona";
                case "custom" -> "Okładka: własny obraz";
                default -> "Okładka: automatyczna";
            };
            drawButton(ctx, tr, 0, c.rounded ? "Rogi: okrągłe" : "Rogi: ostre");
            drawButton(ctx, tr, 1, cover);
            drawButton(ctx, tr, 2, "Wklej ścieżkę obrazu");
            drawButton(ctx, tr, 3, c.lyrics ? "Tekst piosenki: włączony" : "Tekst piosenki: wyłączony");

            if (System.currentTimeMillis() < msgUntil) {
                MediaHud.drawRaw(ctx, tr, tr.trimToWidth(msg, PW - 10), popX + 6, popY + 105, 0xFFFFD966);
            }
        }

        ctx.getMatrices().pop();
    }

    private static void drawButton(DrawContext ctx, TextRenderer tr, int row, String label) {
        int y = popY + BTN0 + row * BTN_STEP;
        ctx.fill(popX + BAR_X, y, popX + BAR_X + BAR_W, y + BTN_H, 0xFF3A3A3A);
        MediaHud.drawRaw(ctx, tr, label, popX + BAR_X + (BAR_W - tr.getWidth(label)) / 2, y + 2, 0xFFFFFFFF);
    }

    private static void drawBar(DrawContext ctx, int x, int y, float f) {
        f = Math.max(0, Math.min(1, f));
        ctx.fill(x, y, x + BAR_W, y + 6, 0xFF333333);
        ctx.fill(x, y, x + (int) (BAR_W * f), y + 6, 0xFF1DB954);
        int hx = x + (int) (BAR_W * f);
        ctx.fill(hx - 1, y - 2, hx + 1, y + 8, 0xFFFFFFFF);
    }
}
