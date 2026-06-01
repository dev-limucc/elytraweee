package dev.limucc.elytraweee.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Plain data holder for ElytraWEEE settings, persisted to config/elytraweee.json.
 */
public class ElytraWeeeConfig {

    public enum JumpMode {
        SINGLE,
        DOUBLE
    }

    /** Master switch. */
    public boolean enabled = true;
    /** Whether a single jump or a double jump deploys the elytra. */
    public JumpMode jumpMode = JumpMode.SINGLE;
    /**
     * Re-equip the chestplate when the active slot is no longer a firework. This is the primary
     * swap-back behaviour: while you hold a firework the elytra stays on (so you can take off
     * again, e.g. right after landing), and the chestplate is restored once you switch items.
     */
    public boolean swapBackWhenNotHoldingFirework = true;
    /**
     * Optional extra: re-equip the chestplate the instant you land, even if you are still holding
     * a firework. Off by default because {@link #swapBackWhenNotHoldingFirework} already covers
     * the common case.
     */
    public boolean swapBackOnLanding = false;

    /**
     * Grace window for the "jump first, grab a firework a moment later" case. When on, pressing
     * jump arms a short window; if you start holding a firework within {@link #graceWindowTicks}
     * ticks (while still airborne) the elytra is deployed as if you had been holding it on the jump.
     */
    public boolean graceWindowEnabled = true;
    /** Length of the grace window in client ticks (20 ticks = 1 second). Default 10 = 0.5s. */
    public int graceWindowTicks = 10;

    /**
     * Master switch for the fast-swap keybind (instant elytra/chestplate toggle, also mid-air).
     * Independent of {@link #enabled} so PvP players can keep the manual swap while auto-deploy is off.
     */
    public boolean fastSwapEnabled = true;
    /**
     * After a fast-swap, suppress the automatic landing swap-back for this many ticks so a manual
     * swap is not immediately undone on the ground. 20 ticks = 1 second.
     */
    public int fastSwapRevertCooldownTicks = 20;
    /**
     * When you fast-swap back to the elytra while airborne, automatically resume gliding so you do
     * not have to tap jump again to re-enter flight mode. Toggle, on by default.
     */
    public boolean autoReglideOnFastSwap = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("elytraweee.json");

    private static ElytraWeeeConfig instance;

    public static ElytraWeeeConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static ElytraWeeeConfig load() {
        try {
            if (Files.exists(PATH)) {
                try (Reader reader = Files.newBufferedReader(PATH)) {
                    ElytraWeeeConfig loaded = GSON.fromJson(reader, ElytraWeeeConfig.class);
                    if (loaded != null) {
                        loaded.sanitize();
                        instance = loaded;
                        return instance;
                    }
                }
            }
        } catch (Exception e) {
            // Corrupt or unreadable config -> fall back to defaults below.
        }
        instance = new ElytraWeeeConfig();
        instance.save();
        return instance;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            // Non-fatal: keep using the in-memory config.
        }
    }

    private void sanitize() {
        if (jumpMode == null) {
            jumpMode = JumpMode.SINGLE;
        }
        graceWindowTicks = clamp(graceWindowTicks, 0, 40);
        fastSwapRevertCooldownTicks = clamp(fastSwapRevertCooldownTicks, 0, 100);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
