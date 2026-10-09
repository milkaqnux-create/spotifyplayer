package pl.mediahud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

public final class MediaHud {
    public static final int W = 150;
    public static final int H = 46;
    private static final String[] CMDS = {"prev", "toggle", "next"};
    private static final int BTN_STEP = 24, BTN_Y = 30, BTN_W = 20, BTN_H = 11;

    private static boolean showCover() {
        return !"off".equals(HudConfig.get().coverMode);
    }

    private static int tx() {
        return showCover() ? 46 : 6;
    }

    public static int width() {
        return Math.round(W * HudConfig.get().scale);
    }

    public static int height() {
        return Math.round(H * HudConfig.get().scale);
    }

    public static boolean inside(double mx, double my) {
        HudConfig c = HudConfig.get();
        return mx >= c.x && mx < c.x + width() && my >= c.y && my < c.y + height();
    }

    /** Zwraca komende przycisku pod kursorem albo null. */
    public static String buttonAt(double mx, double my) {
        HudConfig c = HudConfig.get();
        double lx = (mx - c.x) / c.scale;
        double ly = (my - c.y) / c.scale;
        for (int i = 0; i < 3; i++) {
            int bx = tx() + i * BTN_STEP;
            if (lx >= bx && lx < bx + BTN_W && ly >= BTN_Y && ly < BTN_Y + BTN_H) return CMDS[i];
        }
        return null;
    }

    private static int argb(int rgb, float a) {
        int al = Math.max(0, Math.min(255, (int) (a * 255f)));
        return (al << 24) | rgb;
    }

    /** Tekst rysowany 1 poziom Z nad wypelnieniami, zeby nigdy nie zostal przykryty. Kolor MUSI miec alfa. */
    public static void drawRaw(DrawContext ctx, TextRenderer tr, String s, int x, int y, int argb) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 1);
        ctx.drawText(tr, s, x, y, argb, false);
        ctx.getMatrices().pop();
    }

    private static void text(DrawContext ctx, TextRenderer tr, String s, int x, int y, int rgb, float a) {
        int al = Math.max(0, Math.min(255, (int) (a * 255f)));
        if (al < 4) return;
        drawRaw(ctx, tr, s, x, y, (al << 24) | rgb);
    }

    public static void box(DrawContext ctx, int x, int y, int w, int h, int r, int color, boolean rounded) {
        if (!rounded || r <= 0) {
            ctx.fill(x, y, x + w, y + h, color);
            return;
        }
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            ctx.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
        ctx.fill(x, y + r, x + w, y + h - r, color);
    }

    private static String fit(TextRenderer tr, String s, int maxW) {
        if (tr.getWidth(s) <= maxW) return s;
        return tr.trimToWidth(s, maxW - tr.getWidth("...")) + "...";
    }

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean chat = mc.currentScreen instanceof ChatScreen;
        if (mc.options.hudHidden && !chat) return;
        MediaState s = MediaController.state;
        if (s == null && !chat) return;

        HudConfig cfg = HudConfig.get();
        float op = cfg.opacity;
        TextRenderer tr = mc.textRenderer;
        int tx = tx();

        ctx.getMatrices().push();
        ctx.getMatrices().translate(cfg.x, cfg.y, 0);
        ctx.getMatrices().scale(cfg.scale, cfg.scale, 1f);

        box(ctx, 0, 0, W, H, 5, argb(0x101010, op * 0.8f), cfg.rounded);

        if (showCover()) {
            if (CoverTexture.loaded()) {
                ctx.drawTexture(RenderLayer::getGuiTextured, CoverTexture.ID, 4, 4, 0f, 0f, 38, 38, 128, 128, 128, 128, argb(0xFFFFFF, op));
            } else {
                ctx.fill(4, 4, 42, 42, argb(0x555555, op));
            }
        }

        int textW = W - tx - 4;
        String title = s != null ? s.title : "Nic nie gra";
        String artist = s != null ? s.artist : "Spotify";
        text(ctx, tr, fit(tr, title, textW), tx, 6, 0xFFFFFF, op);
        text(ctx, tr, fit(tr, artist, textW), tx, 17, 0xAAAAAA, op);

        String[] labels = {"|<", (s != null && s.playing) ? "||" : ">", ">|"};
        for (int i = 0; i < 3; i++) {
            int bx = tx + i * BTN_STEP;
            box(ctx, bx, BTN_Y, BTN_W, BTN_H, 2, argb(0x3A3A3A, op * 0.9f), cfg.rounded);
            int lw = tr.getWidth(labels[i]);
            text(ctx, tr, labels[i], bx + (BTN_W - lw) / 2, BTN_Y + 2, 0xFFFFFF, op);
        }

        if (cfg.lyrics && s != null) renderLyrics(ctx, tr, s, cfg, op);

        ctx.getMatrices().pop();
    }

    private static void renderLyrics(DrawContext ctx, TextRenderer tr, MediaState s, HudConfig cfg, float op) {
        String st = LyricsManager.status;
        String cur, next = "";
        if ("ok".equals(st)) {
            List<LyricsManager.Line> ls = LyricsManager.lines;
            int i = LyricsManager.indexAt(ls, s.currentPosMs());
            cur = i >= 0 ? ls.get(i).text() : "";
            if (i + 1 < ls.size()) next = ls.get(i + 1).text();
            if (cur.isBlank()) cur = "...";
        } else if ("loading".equals(st)) {
            cur = "Szukam tekstu...";
        } else {
            cur = "Brak tekstu";
        }

        List<OrderedText> wrapped = tr.wrapLines(Text.literal(cur), W - 12);
        int n = Math.max(1, Math.min(2, wrapped.size()));
        boolean hasNext = !next.isBlank();
        int y0 = H + 3;
        int h = 8 + n * 10 + (hasNext ? 12 : 0);
        box(ctx, 0, y0, W, h, 5, argb(0x101010, op * 0.8f), cfg.rounded);

        int al = Math.max(0, Math.min(255, (int) (op * 255f)));
        if (al >= 4) {
            for (int i = 0; i < n && i < wrapped.size(); i++) {
                ctx.getMatrices().push();
                ctx.getMatrices().translate(0, 0, 1);
                ctx.drawText(tr, wrapped.get(i), 6, y0 + 4 + i * 10, (al << 24) | 0xFFFFFF, false);
                ctx.getMatrices().pop();
            }
        }
        if (hasNext) text(ctx, tr, fit(tr, next, W - 12), 6, y0 + 4 + n * 10 + 2, 0x888888, op);
    }
}
