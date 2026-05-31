package dev.limucc.elytraweee;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * All ElytraWEEE keybinds, grouped under their own dedicated category in Options &gt; Controls
 * (instead of being scattered through Movement/Misc). Every binding defaults to unbound so it never
 * clashes with vanilla or other mods — the player assigns keys once under "ElytraWEEE".
 */
public final class KeyBindings {

    /** Dedicated controls category; its label is keyed as {@code key.category.elytraweee.main}. */
    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.of(ElytraWeeeClient.MOD_ID, "main"));

    public static KeyMapping openConfig;
    public static KeyMapping fastSwap;
    public static KeyMapping toggleEnabled;

    private KeyBindings() {
    }

    public static void register() {
        openConfig = register("key.elytraweee.open_config");
        fastSwap = register("key.elytraweee.fast_swap");
        toggleEnabled = register("key.elytraweee.toggle_enabled");
    }

    private static KeyMapping register(String translationKey) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(
                translationKey,
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(), // unbound by default
                CATEGORY));
    }
}
