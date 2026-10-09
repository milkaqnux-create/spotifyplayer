package pl.mediahud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MediaHudMod implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("mediahud");

    private static KeyBinding prevKey, toggleKey, nextKey;

    @Override
    public void onInitializeClient() {
        HudConfig.load();

        // Zmienisz je w: Opcje > Sterowanie > Media HUD
        prevKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mediahud.prev", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, "key.categories.mediahud"));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mediahud.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_BACKSLASH, "key.categories.mediahud"));
        nextKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mediahud.next", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, "key.categories.mediahud"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (prevKey.wasPressed()) MediaController.send("prev");
            while (toggleKey.wasPressed()) MediaController.send("toggle");
            while (nextKey.wasPressed()) MediaController.send("next");
        });

        // W czacie panel rysuje ChatInteraction (zeby byl na wierzchu).
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            if (MinecraftClient.getInstance().currentScreen instanceof ChatScreen) return;
            MediaHud.render(ctx);
        });

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> CoverTexture.apply());
        ChatInteraction.init();
        MediaController.start();
    }
}
