package dev.limucc.elytraweee.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Builds the Cloth Config screen with two tabs: "Settings" and "Info".
 */
public final class ConfigScreenBuilder {

    private ConfigScreenBuilder() {
    }

    public static Screen build(Screen parent) {
        ElytraWeeeConfig cfg = ElytraWeeeConfig.get();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("ElytraWEEE"))
                .setSavingRunnable(cfg::save);

        ConfigEntryBuilder eb = builder.entryBuilder();

        // ---------------- Settings tab ----------------
        ConfigCategory settings = builder.getOrCreateCategory(Component.literal("Settings"));

        settings.addEntry(eb.startBooleanToggle(Component.literal("Enable ElytraWEEE"), cfg.enabled)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Master switch. When off, jumping with a firework does nothing."))
                .setSaveConsumer(v -> cfg.enabled = v)
                .build());

        settings.addEntry(eb.startEnumSelector(Component.literal("Jump mode"), ElytraWeeeConfig.JumpMode.class, cfg.jumpMode)
                .setDefaultValue(ElytraWeeeConfig.JumpMode.SINGLE)
                .setEnumNameProvider(e -> Component.literal(e == ElytraWeeeConfig.JumpMode.SINGLE ? "Single jump" : "Double jump"))
                .setTooltip(
                        Component.literal("Single: one jump while holding a firework equips the elytra."),
                        Component.literal("Double: two quick jumps equip the elytra."))
                .setSaveConsumer(v -> cfg.jumpMode = v)
                .build());

        settings.addEntry(eb.startBooleanToggle(Component.literal("Swap chestplate back after landing (no firework)"), cfg.swapBackWhenNotHoldingFirework)
                .setDefaultValue(true)
                .setTooltip(
                        Component.literal("After you land, restore your chestplate once you are no longer holding a firework."),
                        Component.literal("Never happens mid-air. Keep holding a firework after landing to stay in the elytra and take off again."))
                .setSaveConsumer(v -> cfg.swapBackWhenNotHoldingFirework = v)
                .build());

        settings.addEntry(eb.startBooleanToggle(Component.literal("Always swap chestplate back the instant you land"), cfg.swapBackOnLanding)
                .setDefaultValue(false)
                .setTooltip(
                        Component.literal("Restore your chestplate as soon as you land, even if you are still holding a firework."),
                        Component.literal("Off by default — the option above already handles landing for most cases."))
                .setSaveConsumer(v -> cfg.swapBackOnLanding = v)
                .build());

        // ---------------- Info tab ----------------
        ConfigCategory info = builder.getOrCreateCategory(Component.literal("Info"));

        addText(eb, info, "§l§bElytraWEEE§r — automatic elytra deploy for Minecraft 26.1.2");
        addText(eb, info, "Hold any firework rocket and jump — your elytra is equipped automatically. One jump is enough!");
        addText(eb, info, "§7• The elytra is taken from your inventory or hotbar.");
        addText(eb, info, "§7• Your chestplate is swapped out safely — it is never dropped.");
        addText(eb, info, "§7• It is put back on after you land, once you stop holding a firework (configurable).");
        addText(eb, info, "§8————————————————");
        addText(eb, info, "Author: §bdev.limucc§r");
        addText(eb, info, "GitHub: §9github.com/dev-limucc/ElytraWEEE");

        return builder.build();
    }

    private static void addText(ConfigEntryBuilder eb, ConfigCategory category, String text) {
        category.addEntry(eb.startTextDescription(Component.literal(text)).build());
    }
}
