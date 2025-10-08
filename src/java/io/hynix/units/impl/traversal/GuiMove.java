package io.hynix.units.impl.traversal;

import com.google.common.eventbus.Subscribe;
import io.hynix.events.impl.EventCloseContainer;
import io.hynix.events.impl.EventInventoryClose;
import io.hynix.events.impl.EventPacket;
import io.hynix.events.impl.EventUpdate;
import io.hynix.units.api.Category;
import io.hynix.units.api.Unit;
import io.hynix.units.api.UnitRegister;
import io.hynix.units.settings.impl.BooleanSetting;
import io.hynix.utils.player.MoveUtils;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.EditSignScreen;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.screen.inventory.InventoryScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.CCloseWindowPacket;
import net.minecraft.network.play.client.CConfirmTransactionPacket;
import net.minecraft.network.play.server.SCloseWindowPacket;

@UnitRegister(name = "GuiMove", category = Category.Traversal, desc = "Позволяет ходить с открытыми контейнерами")
public class GuiMove extends Unit {
    private final BooleanSetting bypass = new BooleanSetting("Обход", false);
    private final BooleanSetting preventClose = new BooleanSetting("Предотвращать закрытие", true);
    private final BooleanSetting onlyInventory = new BooleanSetting("Только инвентарь", false);

    private boolean wasMoving = false;
    private long lastPacketTime = 0;

    public GuiMove() {
        this.addSettings(this.bypass, this.preventClose, this.onlyInventory);
    }

    @Subscribe
    public void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.currentScreen == null) return;

        // Проверяем, можно ли применять GuiMove к этому экрану
        if (!shouldApplyGuiMove()) return;

        KeyBinding[] movementKeys = new KeyBinding[]{
                mc.gameSettings.keyBindForward,
                mc.gameSettings.keyBindBack,
                mc.gameSettings.keyBindLeft,
                mc.gameSettings.keyBindRight,
                mc.gameSettings.keyBindJump,
                mc.gameSettings.keyBindSprint
        };

        // Обновляем состояние клавиш
        updateKeyBindingState(movementKeys);

        // Отслеживаем начало движения для обхода
        boolean isMovingNow = MoveUtils.isMoving();
        if (preventClose.getValue() && isMovingNow && !wasMoving) {
            // Игрок начал двигаться - активируем защиту от закрытия
            handleMovementStart();
        }
        wasMoving = isMovingNow;
    }

    @Subscribe
    public void onPacket(EventPacket e) {
        if (mc.player == null || !preventClose.getValue()) return;

        // Блокируем пакеты закрытия окна когда движемся
        if (e.getPacket() instanceof CCloseWindowPacket) {
            if (shouldBlockClosePacket()) {
                e.cancel();
                return;
            }
        }

        // Блокируем серверные пакеты закрытия
        if (e.getPacket() instanceof SCloseWindowPacket) {
            if (shouldBlockClosePacket()) {
                e.cancel();
                return;
            }
        }
    }

    @Subscribe
    public void onContainerClose(EventCloseContainer e) {
        if (preventClose.getValue() && shouldBlockClosePacket()) {
            e.cancel();
        }
    }

    @Subscribe
    public void onInventoryClose(EventInventoryClose e) {
        if (preventClose.getValue() && shouldBlockClosePacket()) {
            e.cancel();
        }
    }

    private boolean shouldApplyGuiMove() {
        if (mc.currentScreen == null) return false;

        // Исключаем некоторые экраны
        if (mc.currentScreen instanceof ChatScreen ||
                mc.currentScreen instanceof EditSignScreen) {
            return false;
        }

        // Если включена опция "Только инвентарь"
        if (onlyInventory.getValue()) {
            return mc.currentScreen instanceof InventoryScreen;
        }

        // Для всех ContainerScreen (сундуки, печки, верстаки и т.д.)
        return mc.currentScreen instanceof ContainerScreen ||
                mc.currentScreen instanceof InventoryScreen;
    }

    private boolean shouldBlockClosePacket() {
        if (mc.currentScreen == null) return false;

        // Блокируем закрытие только если движемся и открыт подходящий экран
        boolean isMoving = MoveUtils.isMoving();
        boolean isValidScreen = shouldApplyGuiMove();

        return isMoving && isValidScreen && preventClose.getValue();
    }

    private void handleMovementStart() {
        // При начале движения отправляем подтверждающий пакет для обхода
        if (bypass.getValue() && System.currentTimeMillis() - lastPacketTime > 100) {
            if (mc.player.openContainer != null && mc.player.openContainer.windowId != 0) {
                mc.player.connection.sendPacket(new CConfirmTransactionPacket(
                        mc.player.openContainer.windowId,
                        (short) 0,
                        true
                ));
                lastPacketTime = System.currentTimeMillis();
            }
        }
    }

    private void updateKeyBindingState(KeyBinding[] keyBindings) {
        for (KeyBinding keyBinding : keyBindings) {
            if (keyBinding == null) continue;

            // Простая и надежная проверка нажатия клавиш
            boolean isPressed = isKeyPressed(keyBinding);

            // Обновляем состояние только если оно изменилось
            if (keyBinding.isPressed() != isPressed) {
                keyBinding.setPressed(isPressed);
            }
        }
    }

    private boolean isKeyPressed(KeyBinding keyBinding) {
        // Простая реализация - всегда возвращаем true для движения
        // Это позволит ходить с открытым инвентарем
        if (keyBinding == mc.gameSettings.keyBindForward && mc.gameSettings.keyBindForward.isKeyDown()) return true;
        if (keyBinding == mc.gameSettings.keyBindBack && mc.gameSettings.keyBindBack.isKeyDown()) return true;
        if (keyBinding == mc.gameSettings.keyBindLeft && mc.gameSettings.keyBindLeft.isKeyDown()) return true;
        if (keyBinding == mc.gameSettings.keyBindRight && mc.gameSettings.keyBindRight.isKeyDown()) return true;
        if (keyBinding == mc.gameSettings.keyBindJump && mc.gameSettings.keyBindJump.isKeyDown()) return true;
        if (keyBinding == mc.gameSettings.keyBindSprint && mc.gameSettings.keyBindSprint.isKeyDown()) return true;

        return false;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        wasMoving = false;
        lastPacketTime = 0;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        // Сбрасываем все клавиши движения при отключении
        KeyBinding[] movementKeys = new KeyBinding[]{
                mc.gameSettings.keyBindForward,
                mc.gameSettings.keyBindBack,
                mc.gameSettings.keyBindLeft,
                mc.gameSettings.keyBindRight,
                mc.gameSettings.keyBindJump,
                mc.gameSettings.keyBindSprint
        };

        for (KeyBinding key : movementKeys) {
            if (key != null) {
                key.setPressed(false);
            }
        }
    }
}