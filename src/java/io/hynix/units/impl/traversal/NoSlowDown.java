package io.hynix.units.impl.traversal;

import com.google.common.eventbus.Subscribe;
import io.hynix.events.impl.EventNoSlow;
import io.hynix.events.impl.EventPacket;
import io.hynix.events.impl.EventUpdate;
import io.hynix.units.api.Category;
import io.hynix.units.api.Unit;
import io.hynix.units.api.UnitRegister;
import io.hynix.units.settings.impl.*;
import io.hynix.utils.player.MoveUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.play.client.CHeldItemChangePacket;
import net.minecraft.network.play.client.CPlayerPacket;
import net.minecraft.potion.Effects;

import java.util.Random;

/**
 * @author L1r9ije
 */
@UnitRegister(name = "NoSlowDown", category = Category.Traversal, desc = "Обновленный анти-замедление для современных античитов")
public class NoSlowDown extends Unit {

    public final ModeSetting mode = new ModeSetting("Режим", "Matrix",
            "Matrix", "Funtime", "ReallyWorld", "HolyWorld");

    // Основные настройки
    private final SliderSetting speed = new SliderSetting("Скорость", 0.85F, 0.7F, 0.95F, 0.01F);
    private final BooleanSetting onlyMoving = new BooleanSetting("Только при движении", true);
    private final BooleanSetting onlyGround = new BooleanSetting("Только на земле", true);
    private final BooleanSetting checkItems = new BooleanSetting("Проверять предметы", true);

    // Настройки для режимов
    private final SliderSetting chance = new SliderSetting("Шанс", 70, 50, 90, 1).setVisible(() -> mode.is("Funtime"));
    private final SliderSetting delay = new SliderSetting("Задержка", 3, 2, 5, 1).setVisible(() -> mode.is("ReallyWorld"));
    private final SliderSetting packetLimit = new SliderSetting("Лимит пакетов", 15, 5, 30, 1).setVisible(() -> mode.is("Matrix"));

    // Защитные настройки
    private final BooleanSetting randomizeTiming = new BooleanSetting("Случайные тайминги", true);
    private final BooleanSetting limitPackets = new BooleanSetting("Ограничить пакеты", true);
    private final BooleanSetting useEffects = new BooleanSetting("Использовать эффекты", false).setVisible(() -> mode.is("HolyWorld"));
    private final BooleanSetting strictMode = new BooleanSetting("Строгий режим", true).setVisible(() -> mode.is("ReallyWorld"));
    private final BooleanSetting noSprint = new BooleanSetting("Сохранять спринт", false);

    private long lastTime = 0;
    private long lastPacketTime = 0;
    private int usageTicks = 0;
    private boolean wasUsing = false;
    private int packetCounter = 0;
    private int matrixCounter = 0;
    private int funtimeTicks = 0;
    private int randomDelay = 0;
    private final Random random = new Random();

    public NoSlowDown() {
        addSettings(mode, speed, chance, delay, packetLimit, checkItems, onlyMoving, onlyGround,
                randomizeTiming, limitPackets, useEffects, strictMode, noSprint);
    }

    @Subscribe
    public void onUpdate(EventUpdate event) {
        if (mc.player == null) return;

        boolean isUsing = mc.player.isHandActive();
        boolean canActivate = shouldActivate();

        // Современная защита от детекта
        if (usageTicks > 100) {
            if (wasUsing) {
                usageTicks = 0;
                wasUsing = false;
                return;
            }
        }

        if (isUsing && canActivate) {
            usageTicks++;
            wasUsing = true;

            if (!onlyMoving.getValue() || MoveUtils.isMoving()) {
                handleNoSlow();
            }
        } else {
            if (wasUsing) {
                if (usageTicks > 0) {
                    usageTicks -= 2;
                    if (canActivate && usageTicks > 0) {
                        handleNoSlow();
                    }
                } else {
                    wasUsing = false;
                    matrixCounter = 0;
                    funtimeTicks = 0;
                }
            }
        }

        // Восстановление спринта
        if (noSprint.getValue() && !mc.player.isSprinting() && MoveUtils.isMoving() &&
                mc.player.getFoodStats().getFoodLevel() > 6 && canActivate && usageTicks > 0) {
            if (mc.player.moveForward > 0) {
                mc.player.setSprinting(true);
            }
        }
    }

    @Subscribe
    public void onNoSlow(EventNoSlow event) {
        if (mc.player == null || !isEnabled() || !shouldActivate()) return;

        if (mc.player.isHandActive() && (!onlyMoving.getValue() || MoveUtils.isMoving())) {
            if (randomizeTiming.getValue() && randomDelay == 0) {
                randomDelay = 1 + random.nextInt(3);
            }

            switch (mode.getValue()) {
                case "Matrix":
                    handleMatrixNoSlow(event);
                    break;
                case "Funtime":
                    handleFuntimeNoSlow(event);
                    break;
                case "ReallyWorld":
                    handleReallyWorldNoSlow(event);
                    break;
                case "HolyWorld":
                    handleHolyWorldNoSlow(event);
                    break;
            }
        }
    }

    @Subscribe
    public void onPacket(EventPacket event) {
        if (mc.player == null || !isEnabled() || !mc.player.isHandActive() || !shouldActivate()) return;

        if (limitPackets.getValue() && packetCounter > packetLimit.getValue()) {
            return;
        }

        if (event.getPacket() instanceof CPlayerPacket) {
            packetCounter++;

            if (System.currentTimeMillis() - lastPacketTime > 150 + random.nextInt(100)) {
                switch (mode.getValue()) {
                    case "Funtime":
                        if (packetCounter % 5 == 0 && random.nextInt(100) < chance.getValue()) {
                            sendSmartPackets();
                        }
                        break;
                    case "ReallyWorld":
                        if (strictMode.getValue() && packetCounter % 4 == 0) {
                            sendReallyWorldPackets();
                        }
                        break;
                    case "Matrix":
                        if (packetCounter % 6 == 0) {
                            sendMatrixPackets();
                        }
                        break;
                    case "HolyWorld":
                        if (packetCounter % 8 == 0) {
                            sendHolyPackets();
                        }
                        break;
                }
                lastPacketTime = System.currentTimeMillis();
            }
        }
    }

    private boolean shouldActivate() {
        if (onlyMoving.getValue() && !MoveUtils.isMoving()) {
            return false;
        }

        if (onlyGround.getValue() && !mc.player.onGround) {
            return false;
        }

        switch (mode.getValue()) {
            case "Funtime":
                return shouldFuntimeActivate();
            case "HolyWorld":
                return shouldHolyWorldActivate();
            case "ReallyWorld":
                return shouldReallyWorldActivate();
            default:
                return true;
        }
    }

    private boolean shouldFuntimeActivate() {
        if (!checkItems.getValue()) return true;

        // Современная проверка предметов
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (stack.getItem() == Items.CROSSBOW && stack.getDamage() > 0) {
                funtimeTicks++;
                return true;
            }
            if (stack.getItem() == Items.SHIELD) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldHolyWorldActivate() {
        if (useEffects.getValue()) {
            return mc.player.isPotionActive(Effects.SPEED) ||
                    mc.player.isPotionActive(Effects.JUMP_BOOST);
        }
        return true;
    }

    private boolean shouldReallyWorldActivate() {
        if (strictMode.getValue()) {
            return mc.player.onGround &&
                    MoveUtils.isMoving() &&
                    !mc.player.isInWater() &&
                    !mc.player.isInLava() &&
                    mc.player.fallDistance < 2.0f;
        }
        return true;
    }

    private void handleNoSlow() {
        if (!MoveUtils.isMoving()) return;

        switch (mode.getValue()) {
            case "Matrix":
                applyMatrixMovement();
                break;
            case "Funtime":
                applyFuntimeMovement();
                break;
            case "ReallyWorld":
                applyReallyWorldMovement();
                break;
            case "HolyWorld":
                applyHolyWorldMovement();
                break;
        }
    }

    private void handleMatrixNoSlow(EventNoSlow event) {
        matrixCounter++;

        // Matrix: улучшенный алгоритм для современных серверов
        if (matrixCounter % 3 == 0) {
            event.cancel();
        }

        if (mc.player.onGround && matrixCounter % 4 == 0) {
            double reduction = 0.8 + (speed.getValue() * 0.2);
            applyMotionReduction(reduction);
        }
    }

    private void handleFuntimeNoSlow(EventNoSlow event) {
        // Funtime: улучшенный рандом для анти-детекта
        if (random.nextInt(100) < (chance.getValue() - 15) && funtimeTicks > 10) {
            event.cancel();
        }

        if (mc.player.onGround && funtimeTicks % 4 == 0) {
            double funSpeed = 0.88 + (speed.getValue() * 0.12);
            applyMotionReduction(funSpeed);
        }
    }

    private void handleReallyWorldNoSlow(EventNoSlow event) {
        // ReallyWorld: улучшенная логика для современных проверок
        if (strictMode.getValue()) {
            if (usageTicks % (delay.getValue() + randomDelay) == 0 && mc.player.onGround) {
                event.cancel();
            }
        } else {
            if (usageTicks % 2 == 0) {
                event.cancel();
            }
        }

        if (mc.player.onGround) {
            double realisticReduction = 0.88 - (speed.getValue() * 0.1);
            applyMotionReduction(realisticReduction);
        }
    }

    private void handleHolyWorldNoSlow(EventNoSlow event) {
        // HolyWorld: улучшенный алгоритм
        if (usageTicks % 3 == 0) {
            event.cancel();
        }

        if (useEffects.getValue()) {
            double effectMultiplier = 1.0;
            if (mc.player.isPotionActive(Effects.SPEED)) {
                effectMultiplier += 0.08;
            }
            applyMotionReduction(0.86 * effectMultiplier);
        } else {
            applyMotionReduction(0.86);
        }
    }

    private void applyMatrixMovement() {
        if (mc.player.onGround && MoveUtils.isMoving()) {
            if (matrixCounter % 4 == 0) {
                double reduction = 0.86 + (speed.getValue() * 0.14);
                applyMotionReduction(reduction);
            }
        }
    }

    private void applyFuntimeMovement() {
        if (random.nextInt(100) < (chance.getValue() - 5) && mc.player.onGround && funtimeTicks > 5) {
            double funFactor = 0.87 + (speed.getValue() * 0.13);
            applyMotionReduction(funFactor);
        }
    }

    private void applyReallyWorldMovement() {
        if (System.currentTimeMillis() - lastTime > (delay.getValue() + randomDelay) * 50L && mc.player.onGround) {
            double realisticSpeed = 0.87;
            applyMotionReduction(realisticSpeed);
            lastTime = System.currentTimeMillis();
            randomDelay = randomizeTiming.getValue() ? random.nextInt(2) : 0;
        }
    }

    private void applyHolyWorldMovement() {
        if (mc.player.onGround && usageTicks % 2 == 0) {
            double holySpeed = 0.86;
            if (useEffects.getValue()) {
                holySpeed = 0.88;
            }
            applyMotionReduction(holySpeed);
        }
    }

    private void applyMotionReduction(double reduction) {
        double motionX = mc.player.getMotion().getX();
        double motionZ = mc.player.getMotion().getZ();

        if (Math.abs(motionX) > 0.03 || Math.abs(motionZ) > 0.03) {
            mc.player.setMotion(
                    motionX * reduction,
                    mc.player.getMotion().getY(),
                    motionZ * reduction
            );
        }
    }

    private void sendSmartPackets() {
        if (mc.player.isHandActive() && packetCounter < 12) {
            mc.player.connection.sendPacket(new CHeldItemChangePacket(mc.player.inventory.currentItem));
        }
    }

    private void sendReallyWorldPackets() {
        if (mc.player.isHandActive() && MoveUtils.isMoving() && packetCounter < 8) {
            if (packetCounter % 6 == 0) {
                mc.player.connection.sendPacket(new CHeldItemChangePacket(mc.player.inventory.currentItem));
            }
        }
    }

    private void sendMatrixPackets() {
        if (mc.player.isHandActive() && packetCounter < 10) {
            if (packetCounter % 7 == 0) {
                mc.player.connection.sendPacket(new CHeldItemChangePacket(mc.player.inventory.currentItem));
            }
        }
    }

    private void sendHolyPackets() {
        if (mc.player.isHandActive() && packetCounter < 15) {
            if (packetCounter % 9 == 0) {
                mc.player.connection.sendPacket(new CHeldItemChangePacket(mc.player.inventory.currentItem));
            }
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        lastTime = System.currentTimeMillis();
        lastPacketTime = System.currentTimeMillis();
        usageTicks = 0;
        wasUsing = false;
        packetCounter = 0;
        matrixCounter = 0;
        funtimeTicks = 0;
        randomDelay = 0;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        usageTicks = 0;
        wasUsing = false;
        matrixCounter = 0;
        funtimeTicks = 0;
        packetCounter = 0;
    }

    public String getModeDescription() {
        switch (mode.getValue()) {
            case "Matrix": return "Улучшенный Matrix режим для современных серверов";
            case "Funtime": return "Адаптированный Funtime с улучшенной защитой";
            case "ReallyWorld": return "Обновленный ReallyWorld для новых античитов";
            case "HolyWorld": return "Современный HolyWorld с улучшенной логикой";
            default: return "Обновленный анти-замедление";
        }
    }
}