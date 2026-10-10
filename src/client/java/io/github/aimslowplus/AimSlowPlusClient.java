package io.github.aimslowplus;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.aimslowplus.config.AimSlowConfig;
import io.github.aimslowplus.logic.SlowdownController;
import io.github.aimslowplus.screen.AimSlowConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class AimSlowPlusClient implements ClientModInitializer {
    public static final String MOD_ID = "aimslowplus";

    private static KeyMapping toggleKey;
    private static KeyMapping openConfigKey;

    @Override
    public void onInitializeClient() {
        AimSlowConfig.load();

        // 1.21.9+ requires a KeyMapping.Category (the old String category constructor no longer exists).
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.aimslowplus.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category));
        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.aimslowplus.open_config", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

        ClientTickEvents.END_CLIENT_TICK.register(AimSlowPlusClient::onClientTick);

        // Always start/stop from a clean state: sensitivity is back to normal immediately.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> SlowdownController.reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SlowdownController.reset());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> AimSlowConfig.save());
    }

    private static void onClientTick(Minecraft client) {
        while (toggleKey.consumeClick()) {
            AimSlowConfig cfg = AimSlowConfig.get();
            cfg.enabled = !cfg.enabled;
            SlowdownController.reset(); // restores normal sensitivity right away when turning off
            AimSlowConfig.save();
            showToggleNotification(client, cfg.enabled);
        }
        while (openConfigKey.consumeClick()) {
            client.setScreen(new AimSlowConfigScreen(client.screen));
        }
    }

    /** Small notification above the hotbar (the vanilla "action bar" overlay message). */
    public static void showToggleNotification(Minecraft client, boolean enabled) {
        Component message = Component.translatable(
                enabled ? "aimslowplus.toast.on" : "aimslowplus.toast.off")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED);
        client.gui.setOverlayMessage(message, false);
    }
}
