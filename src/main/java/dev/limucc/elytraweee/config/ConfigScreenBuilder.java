package dev.limucc.elytraweee.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Builds the Cloth Config screen, organised into focused tabs:
 * General, Auto-Deploy, Swap-Back, Fast Swap, and Info.
 *
 * <p>Tooltips are split into short lines (one {@link Component} per line) so the hover text stays
 * compact and readable next to each option, rather than one long wrapping sentence.
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
                        Component.literal("Master switch for auto-deploy."),
                        Component.literal("When off, jumping with a"),
                        Component.literal("firework does nothing."),
                        Component.literal("Also toggleable via keybind."))
                .setSaveConsumer(v -> cfg.enabled = v)
                .build());

        // ---------------- Auto-Deploy ----------------
        ConfigCategory deploy = builder.getOrCreateCategory(Component.literal("Auto-Deploy"));

        deploy.addEntry(eb.startEnumSelector(Component.literal("Jump mode"), ElytraWeeeConfig.JumpMode.class, cfg.jumpMode)
                .setDefaultValue(ElytraWeeeConfig.JumpMode.SINGLE)
                .setEnumNameProvider(e -> Component.literal(e == ElytraWeeeConfig.JumpMode.SINGLE ? "Single jump" : "Double jump"))
                .setTooltip(
                        Component.literal("Single: one jump (with a"),
                        Component.literal("firework) equips the elytra."),
                        Component.literal("Double: two quick jumps."),
                        Component.literal("Either way you start gliding"),
                        Component.literal("immediately — no extra tap."))
                .setSaveConsumer(v -> cfg.jumpMode = v)
                .build());

        deploy.addEntry(eb.startBooleanToggle(Component.literal("Grace window (jump first, grab firework after)"), cfg.graceWindowEnabled)
                .setDefaultValue(true)
                .setTooltip(
                        Component.literal("Jump first, then grab a"),
                        Component.literal("firework a split-second later"),
                        Component.literal("— the elytra still deploys."),
                        Component.literal("Off = firework must be in"),
                        Component.literal("hand on the jump (strict)."))
                .setSaveConsumer(v -> cfg.graceWindowEnabled = v)
                .build());

        deploy.addEntry(eb.startIntSlider(Component.literal("Grace window length (ticks)"), cfg.graceWindowTicks, 0, 40)
                .setDefaultValue(10)
                .setTextGetter(v -> Component.literal(v + " ticks (" + String.format("%.2f", v / 20.0) + "s)"))
                .setTooltip(
                        Component.literal("How long after a jump you may"),
                        Component.literal("grab a firework and still fly."),
                        Component.literal("20 ticks = 1 second."),
                        Component.literal("Used only if grace is enabled."))
                .setSaveConsumer(v -> cfg.graceWindowTicks = v)
                .build());

        // ---------------- Swap-Back ----------------
        ConfigCategory swapBack = builder.getOrCreateCategory(Component.literal("Swap-Back"));

        swapBack.addEntry(eb.startBooleanToggle(Component.literal("Swap chestplate back after landing (no firework)"), cfg.swapBackWhenNotHoldingFirework)
                .setDefaultValue(true)
                .setTooltip(
                        Component.literal("After landing, restore your"),
                        Component.literal("chestplate once you stop"),
                        Component.literal("holding a firework."),
                        Component.literal("Never happens mid-air."),
                        Component.literal("Keep holding a firework to"),
                        Component.literal("stay in the elytra."))
                .setSaveConsumer(v -> cfg.swapBackWhenNotHoldingFirework = v)
                .build());

        swapBack.addEntry(eb.startBooleanToggle(Component.literal("Always swap chestplate back the instant you land"), cfg.swapBackOnLanding)
                .setDefaultValue(false)
                .setTooltip(
                        Component.literal("Restore your chestplate as"),
                        Component.literal("soon as you land, even while"),
                        Component.literal("still holding a firework."),
                        Component.literal("Off by default."))
                .setSaveConsumer(v -> cfg.swapBackOnLanding = v)
                .build());

        // ---------------- Fast Swap ----------------
        ConfigCategory fastSwap = builder.getOrCreateCategory(Component.literal("Fast Swap"));

        fastSwap.addEntry(eb.startBooleanToggle(Component.literal("Enable fast-swap keybind"), cfg.fastSwapEnabled)
                .setDefaultValue(true)
                .setTooltip(
                        Component.literal("Toggle elytra/chestplate"),
                        Component.literal("instantly with a keybind."),
                        Component.literal("Works mid-air (mace PvP)."),
                        Component.literal("Works even with auto-deploy"),
                        Component.literal("off. Bind it under"),
                        Component.literal("Controls > ElytraWEEE."))
                .setSaveConsumer(v -> cfg.fastSwapEnabled = v)
                .build());

        fastSwap.addEntry(eb.startBooleanToggle(Component.literal("Auto-resume gliding after swapping back"), cfg.autoReglideOnFastSwap)
                .setDefaultValue(true)
                .setTooltip(
                        Component.literal("Fast-swap back to the elytra"),
                        Component.literal("in the air and you keep"),
                        Component.literal("gliding automatically —"),
                        Component.literal("no need to tap jump twice."),
                        Component.literal("On by default."))
                .setSaveConsumer(v -> cfg.autoReglideOnFastSwap = v)
                .build());

        fastSwap.addEntry(eb.startIntSlider(Component.literal("Auto-revert suppression after swap (ticks)"), cfg.fastSwapRevertCooldownTicks, 0, 100)
                .setDefaultValue(20)
                .setTextGetter(v -> Component.literal(v + " ticks (" + String.format("%.2f", v / 20.0) + "s)"))
                .setTooltip(
                        Component.literal("After a ground fast-swap, the"),
                        Component.literal("auto landing swap-back is"),
                        Component.literal("paused this long so it does"),
                        Component.literal("not undo your manual swap."),
                        Component.literal("20 ticks = 1 second."))
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
        addText(eb, info, "§7• You start gliding the instant the elytra goes on — no extra jump needed.");
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
