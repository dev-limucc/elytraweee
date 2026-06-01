package dev.limucc.elytraweee;

import dev.limucc.elytraweee.config.ElytraWeeeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Core ElytraWEEE behaviour, ticked every client tick.
 *
 * <p>All inventory mutations go through {@link net.minecraft.client.multiplayer.MultiPlayerGameMode#handleContainerInput}
 * on the always-open player inventory menu (container id 0), so the swaps are server-authoritative and never drop items.
 *
 * <p>Player inventory menu slot layout: 5 = helmet, 6 = chest, 7 = legs, 8 = boots,
 * 9-35 = main inventory, 36-44 = hotbar, 45 = offhand.
 */
public class ElytraSwapHandler {

    private static final int CHEST_SLOT = 6;
    private static final int FIRST_STORAGE_SLOT = 9; // start of inventory/hotbar/offhand in the menu
    private static final int DOUBLE_JUMP_WINDOW_TICKS = 8;
    private static final int LAND_REVERT_DELAY_TICKS = 5;

    private enum State {
        IDLE,
        EQUIPPED
    }

    private State state = State.IDLE;
    private boolean chestWasOccupied = false;
    private int groundedTicks = 0;

    private boolean prevJumpDown = false;
    private int firstJumpTick = 0;
    private long tickCounter = 0;

    /** Tick of a jump that is "armed" and waiting for a firework (grace window). 0 = none. */
    private int pendingJumpTick = 0;
    /** Until this tick, the automatic landing swap-back is suppressed (so fast-swap is not undone). */
    private long suppressAutoRevertUntilTick = 0;

    public void tick(Minecraft client) {
        tickCounter++;

        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null) {
            reset();
            prevJumpDown = false;
            return;
        }

        ElytraWeeeConfig cfg = ElytraWeeeConfig.get();
        boolean jumpDown = client.options.keyJump.isDown();

        if (!cfg.enabled) {
            reset();
            prevJumpDown = jumpDown;
            return;
        }

        AbstractContainerMenu menu = player.containerMenu;
        // Only act when the player's own inventory menu is active (no chest/GUI open) and the cursor is empty.
        boolean canClick = menu == player.inventoryMenu
                && client.screen == null
                && menu.getCarried().isEmpty()
                && menu.slots.size() > CHEST_SLOT;

        boolean wearingElytra = player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
        boolean holdingFirework = isFirework(player.getMainHandItem()) || isFirework(player.getOffhandItem());

        // Track how long we have been continuously on the ground while wearing an elytra.
        if (wearingElytra && player.onGround()) {
            groundedTicks++;
        } else {
            groundedTicks = 0;
        }

        // --- Jump detection (rising edge of the jump key) ---
        boolean jumpEdge = jumpDown && !prevJumpDown && client.screen == null;
        prevJumpDown = jumpDown;

        boolean triggerEquip = false;
        // A qualifying jump (per jump mode) either equips right away (firework already held) or arms
        // a grace window so that grabbing a firework a moment later still deploys the elytra.
        if (jumpEdge && !wearingElytra && state == State.IDLE && jumpModeSatisfied(cfg)) {
            if (holdingFirework) {
                triggerEquip = true;
                pendingJumpTick = 0;
            } else if (cfg.graceWindowEnabled) {
                pendingJumpTick = (int) tickCounter;
            }
        }

        // --- Grace window: deploy if a firework is grabbed shortly after a jump, while airborne ---
        if (pendingJumpTick > 0) {
            if (tickCounter - pendingJumpTick > cfg.graceWindowTicks) {
                pendingJumpTick = 0; // expired
            } else if (wearingElytra || state != State.IDLE) {
                pendingJumpTick = 0; // no longer applicable
            } else if (player.onGround() && tickCounter > pendingJumpTick + 1) {
                // Back on the ground without ever leaving (the +1 skips the take-off tick where
                // onGround() can still read true): the jump did not lead to flight, so drop it.
                pendingJumpTick = 0;
            } else if (holdingFirework) {
                triggerEquip = true;
                pendingJumpTick = 0;
            }
        }

        if (triggerEquip && canClick) {
            int elytraSlot = findElytraSlot(menu);
            if (elytraSlot >= 0) {
                boolean chestOccupied = menu.getSlot(CHEST_SLOT).hasItem();
                equipElytra(client, player, menu, elytraSlot, chestOccupied);
                state = State.EQUIPPED;
                chestWasOccupied = chestOccupied;
                groundedTicks = 0;
                pendingJumpTick = 0;
            }
        }

        // --- Swap back to chestplate ---
        // Works for ANY worn elytra (even one the mod did not equip this session, e.g. after a
        // restart), as long as a chestplate is available to swap in. Only ever happens on the
        // ground — never mid-air, which would drop the player.
        if (!wearingElytra) {
            // Not wearing an elytra: make sure we are not stuck in a stale EQUIPPED state.
            if (state == State.EQUIPPED) {
                reset();
            }
        } else if (!triggerEquip && canClick && tickCounter >= suppressAutoRevertUntilTick) {
            boolean landed = player.onGround() && !player.isFallFlying()
                    && groundedTicks >= LAND_REVERT_DELAY_TICKS;
            boolean revert = false;
            if (landed) {
                // Optional: swap back the instant you land, even while still holding a firework.
                if (cfg.swapBackOnLanding) {
                    revert = true;
                }
                // Default: once landed and no longer holding a firework, swap back. Keep holding a
                // firework after landing to stay in the elytra and take off again.
                if (cfg.swapBackWhenNotHoldingFirework && !holdingFirework) {
                    revert = true;
                }
            }
            if (revert && swapBackToChestplate(client, player, menu)) {
                reset();
            }
        }
    }

    /**
     * Whether the current jump satisfies the configured jump mode. SINGLE: always. DOUBLE: only on
     * the second jump within {@link #DOUBLE_JUMP_WINDOW_TICKS}, tracking the first jump's tick.
     * Only call on a real jump edge.
     */
    private boolean jumpModeSatisfied(ElytraWeeeConfig cfg) {
        if (cfg.jumpMode == ElytraWeeeConfig.JumpMode.SINGLE) {
            return true;
        }
        if (firstJumpTick > 0 && (tickCounter - firstJumpTick) <= DOUBLE_JUMP_WINDOW_TICKS) {
            firstJumpTick = 0;
            return true;
        }
        firstJumpTick = (int) tickCounter;
        return false;
    }

    /**
     * Instantly toggle between the elytra and a chestplate, including mid-air (unlike the automatic
     * swap-back, which only runs on the ground). Bound to the Fast Swap keybind — built for mace PvP
     * where you need to switch in the air. Reuses the same no-drop inventory routines.
     */
    public void fastSwap(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null) {
            return;
        }
        ElytraWeeeConfig cfg = ElytraWeeeConfig.get();
        // Independent of the master switch so PvP players can keep the manual swap with auto-deploy off.
        if (!cfg.fastSwapEnabled) {
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        boolean canClick = menu == player.inventoryMenu
                && client.screen == null
                && menu.getCarried().isEmpty()
                && menu.slots.size() > CHEST_SLOT;
        if (!canClick) {
            return;
        }

        boolean wearingElytra = player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
        if (wearingElytra) {
            // Take the elytra off. A no-op when there is no chestplate to swap in (keeps the elytra on).
            if (swapBackToChestplate(client, player, menu)) {
                reset();
                suppressAutoRevertUntilTick = tickCounter + cfg.fastSwapRevertCooldownTicks;
            }
            return;
        }

        int elytraSlot = findElytraSlot(menu);
        if (elytraSlot < 0) {
            return;
        }
        boolean chestOccupied = menu.getSlot(CHEST_SLOT).hasItem();
        equipElytra(client, player, menu, elytraSlot, chestOccupied);
        state = State.EQUIPPED;
        chestWasOccupied = chestOccupied;
        groundedTicks = 0;
        pendingJumpTick = 0;
        suppressAutoRevertUntilTick = tickCounter + cfg.fastSwapRevertCooldownTicks;
    }

    // ---- Equip / revert routines (cursor-balanced; never drop items) ----

    private void equipElytra(Minecraft client, Player player, AbstractContainerMenu menu, int elytraSlot, boolean chestOccupied) {
        int id = menu.containerId;
        if (chestOccupied) {
            // Swap: elytra -> chest, chestplate -> elytra's old slot.
            pickup(client, player, id, elytraSlot);
            pickup(client, player, id, CHEST_SLOT);
            pickup(client, player, id, elytraSlot);
        } else {
            // Chest empty: just move the elytra in.
            pickup(client, player, id, elytraSlot);
            pickup(client, player, id, CHEST_SLOT);
        }
    }

    /**
     * Put a chestplate back on, moving the worn elytra into the inventory. Works regardless of
     * whether the mod equipped the elytra this session. Returns false (leaving the elytra on) only
     * when there is no chestplate to swap to and we did not equip from a known-empty chest.
     */
    private boolean swapBackToChestplate(Minecraft client, Player player, AbstractContainerMenu menu) {
        int id = menu.containerId;
        int chestplateSlot = findChestplateSlot(menu);
        if (chestplateSlot >= 0) {
            // Swap: chestplate -> chest, elytra -> the chestplate's old slot.
            pickup(client, player, id, CHEST_SLOT);
            pickup(client, player, id, chestplateSlot);
            pickup(client, player, id, CHEST_SLOT);
            return true;
        }
        if (state == State.EQUIPPED && !chestWasOccupied) {
            // We equipped from an empty chest and there is no chestplate: shift the elytra back out.
            quickMove(client, player, id, CHEST_SLOT);
            return true;
        }
        return false; // No chestplate available: keep the elytra on rather than leave the chest bare.
    }

    // ---- Helpers ----

    private int findElytraSlot(AbstractContainerMenu menu) {
        for (int i = FIRST_STORAGE_SLOT; i < menu.slots.size(); i++) {
            if (menu.getSlot(i).getItem().getItem() == Items.ELYTRA) {
                return i;
            }
        }
        return -1;
    }

    private int findChestplateSlot(AbstractContainerMenu menu) {
        for (int i = FIRST_STORAGE_SLOT; i < menu.slots.size(); i++) {
            if (isChestplate(menu.getSlot(i).getItem())) {
                return i;
            }
        }
        return -1;
    }

    /** True for any item worn in the chest equipment slot, excluding the elytra itself. */
    private static boolean isChestplate(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() == Items.ELYTRA) {
            return false;
        }
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
    }

    private static boolean isFirework(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.FIREWORK_ROCKET;
    }

    private void pickup(Minecraft client, Player player, int containerId, int slot) {
        client.gameMode.handleContainerInput(containerId, slot, 0, ContainerInput.PICKUP, player);
    }

    private void quickMove(Minecraft client, Player player, int containerId, int slot) {
        client.gameMode.handleContainerInput(containerId, slot, 0, ContainerInput.QUICK_MOVE, player);
    }

    private void reset() {
        state = State.IDLE;
        chestWasOccupied = false;
        groundedTicks = 0;
        firstJumpTick = 0;
        pendingJumpTick = 0;
        // Note: suppressAutoRevertUntilTick is intentionally NOT reset here — it must outlive a swap.
    }
}
