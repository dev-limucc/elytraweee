package dev.limucc.elytraweee.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Builds the Cloth Config screen, organised into focused tabs:
 * General, Auto-Deploy, Swap-Back, Fast Swap, and Info.
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

        // ---------------- General ----------------
        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));

        general.addEntry(eb.startBooleanToggle(Component.literal("Enable ElytraWEEE"), cfg.enabled)
                .setDefaultValue(true)
                .setTooltip(
                        Component.literal("Master switch for the automatic deploy. When off, jumping with a firework does nothing."),
                        Component.literal("Can also be toggled in-game with the \"Toggle ElytraWEEE on/off\" keybind."))
                .setSaveConsumer(v -> cfg.enabled = v)
                .build());

        // ---------------- Auto-Deploy ----------------
        ConfigCategory deploy = builder.getOrCreateCategory(Component.literal("Auto-Deploy"));

        deploy.addEntry(eb.startEnumSelector(Component.literal("Jump mode"), ElytraWeeeConfig.JumpMode.class, cfg.jumpMode)
                .setDefaultValue(ElytraWeeeConfig.JumpMode.SINGLE)
                .setEnumNameProvider(e -> Component.literal(e == ElytraWeeeConfig.JumpMode.SINGLE ? "Single jump" : "Double jump"))
                .setTooltip(
                        Component.literal("Single: one jump while holding a firework equips the elytra."),
                        Component.literal("Double: two quick jumps equip the elytra."))
                .setSaveConsumer(v -> cfg.jumpMode = v)
                .build());

        deploy.addEntry(eb.startBooleanToggle(Component.literal("Grace window (jump first, grab firework after)"), cfg.graceWindowEnabled)
                .setDefaultValue(true)
                .setTooltip(
                        Component.literal("If you jump first and grab a firework a split-second later, the elytra still deploys."),
                        Component.literal("Off = the firework must already be in hand on the jump (strict)."))
                .setSaveConsumer(v -> cfg.graceWindowEnabled = v)
                .build());

        deploy.addEntry(eb.startIntSlider(Component.literal("Grace window length (ticks)"), cfg.graceWindowTicks, 0, 40)
                .setDefaultValue(10)
                .setTextGetter(v -> Component.literal(v + " ticks (" + String.format("%.2f", v / 20.0) + "s)"))
                .setTooltip(
                        Component.literal("How long after a jump you may grab a firework and still deploy. 20 ticks = 1 second."),
                        Component.literal("Only used when the grace window is enabled."))
                .setSaveConsumer(v -> cfg.graceWindowTicks = v)
                .build());

        // ---------------- Swap-Back ----------------
        ConfigCategory swapBack = builder.getOrCreateCategory(Component.literal("Swap-Back"));

        swapBack.addEntry(eb.startBooleanToggle(Component.literal("Swap chestplate back after landing (no firework)"), cfg.swapBackWhenNotHoldingFirework)
                .setDefaultValue(true)
                .setTooltip(
                        Component.literal("After you land, restore your chestplate once you are no longer holding a firework."),
                        Component.literal("Never happens mid-air. Keep holding a firework after landing to stay in the elytra and take off again."))
                .setSaveConsumer(v -> cfg.swapBackWhenNotHoldingFirework = v)
                .build());

        swapBack.addEntry(eb.startBooleanToggle(Component.literal("Always swap chestplate back the instant you land"), cfg.swapBackOnLanding)
                .setDefaultValue(false)
                .setTooltip(
                        Component.literal("Restore your chestplate as soon as you land, even if you are still holding a firework."),
                        Component.literal("Off by default — the option above already handles landing for most cases."))
                .setSaveConsumer(v -> cfg.swapBackOnLanding = v)
                .build());

        // ---------------- Fast Swap ----------------
        ConfigCategory fastSwap = builder.getOrCreateCategory(Component.literal("Fast Swap"));

        fastSwap.addEntry(eb.startBooleanToggle(Component.literal("Enable fast-swap keybind"), cfg.fastSwapEnabled)
                .setDefaultValue(true)
                .setTooltip(
                        Component.literal("Instantly toggle between elytra and chestplate with a keybind — works mid-air (built for mace PvP)."),
                        Component.literal("Independent of the master switch, so you can keep this on with auto-deploy off."),
                        Component.literal("Bind the key under Options > Controls > ElytraWEEE."))
                .setSaveConsumer(v -> cfg.fastSwapEnabled = v)
                .build());

        fastSwap.addEntry(eb.startIntSlider(Component.literal("Auto-revert suppression after swap (ticks)"), cfg.fastSwapRevertCooldownTicks, 0, 100)
                .setDefaultValue(20)
                .setTextGetter(v -> Component.literal(v + " ticks (" + String.format("%.2f", v / 20.0) + "s)"))
                .setTooltip(
                        Component.literal("After a fast-swap on the ground, the automatic landing swap-back is paused for this long"),
                        Component.literal("so it does not immediately undo your manual swap. 20 ticks = 1 second."))
                .setSaveConsumer(v -> cfg.fastSwapRevertCooldownTicks = v)
                .build());

        // ---------------- Info ----------------
        ConfigCategory info = builder.getOrCreateCategory(Component.literal("Info"));

        addText(eb, info, "§l§bElytraWEEE§r — automatic elytra deploy for Minecraft 26.1.2");
        addText(eb, info, "Hold any firework rocket and jump — your elytra is equipped automatically. One jump is enough!");
        addText(eb, info, "§7• The elytra is taken from your inventory or hotbar.");
        addText(eb, info, "§7• Your chestplate is swapped out safely — it is never dropped.");
        addText(eb, info, "§7• It is put back on after you land, once you stop holding a firework (configurable).");
        addText(eb, info, "§7• Jump first, then grab a firework within the grace window — it still deploys.");
        addText(eb, info, "§8————————————————");
        addText(eb, info, "§l§bKeybinds§r — set them under §eOptions > Controls > ElytraWEEE§r (all unbound by default):");
        addText(eb, info, "§7• §fFast swap§7 — instantly toggle elytra/chestplate, even mid-air (mace PvP).");
        addText(eb, info, "§7• §fToggle on/off§7 — flip the master switch without opening any menu.");
        addText(eb, info, "§7• §fOpen settings§7 — open this screen in-game.");
        addText(eb, info, "§8————————————————");
        addText(eb, info, "Author: §bdev.limucc§r");
        addText(eb, info, "GitHub: §9github.com/dev-limucc/ElytraWEEE");

        return builder.build();
    }

    private static void addText(ConfigEntryBuilder eb, ConfigCategory category, String text) {
        category.addEntry(eb.startTextDescription(Component.literal(text)).build());
    }
}
