package io.hynix.units.impl.miscellaneous;

import com.google.common.eventbus.Subscribe;
import io.hynix.events.impl.EventUpdate;
import io.hynix.units.api.Category;
import io.hynix.units.api.Unit;
import io.hynix.units.api.UnitRegister;
import io.hynix.units.settings.impl.*;
import net.minecraft.client.gui.screen.inventory.ChestScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.monster.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.container.ChestContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.play.client.CChatMessagePacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

@UnitRegister(name = "CreeperFarm", category = Category.Miscellaneous, desc = "AFK фарм криперов: подход + атака + сбор + продажа")
public class CreeperFarm extends Unit {

    // --- Основные настройки ---
    private final ModeSetting mode = new ModeSetting("Режим", "Автоматический", "Автоматический", "Незаметный");
    private final SliderSetting range = new SliderSetting("Поиск (м)", 20f, 3f, 25f, 1f);
    private final SliderSetting safeDistance = new SliderSetting("Безопасная дистанция", 1.2f, 0.8f, 2.5f, 0.05f);
    private final SliderSetting attackDelay = new SliderSetting("Задержка атаки (мс)", 350f, 120f, 1200f, 10f);
    private final SliderSetting moveDelay = new SliderSetting("Задержка движения (мс)", 120f, 40f, 1000f, 10f);

    // --- Авто-сбор лута ---
    private final BooleanSetting autoLoot = new BooleanSetting("Авто-сбор лута", true);
    private final SliderSetting lootRange = new SliderSetting("Дистанция сбора", 5f, 1f, 10f, 0.5f);
    private final BooleanSetting onlyGunpowder = new BooleanSetting("Только порох", true);

    // --- Авто-продажа ---
    private final BooleanSetting autoSell = new BooleanSetting("Авто-продажа", true);
    private final SliderSetting sellThreshold = new SliderSetting("Продавать при", 32f, 1f, 64f, 1f);

    // --- Дополнительные настройки ---
    private final BooleanSetting autoSwitch = new BooleanSetting("Авто-смена оружия", true);
    private final BooleanSetting criticals = new BooleanSetting("Криты (прыжок)", true);
    private final SliderSetting critChance = new SliderSetting("Шанс крита (%)", 55f, 0f, 100f, 5f);
    private final BooleanSetting strafeDuringAttack = new BooleanSetting("Стрейф при атаке", true);
    private final SliderSetting strafeInterval = new SliderSetting("Интервал смены стороны (мс)", 600f, 200f, 2000f, 50f);

    private final BooleanSetting randomizeTimings = new BooleanSetting("Рандомизация таймингов", true);
    private final BooleanSetting humanLikeMovements = new BooleanSetting("Человеческие движения", true);
    private final BooleanSetting limitRotationSpeed = new BooleanSetting("Ограничить скорость поворота", true);
    private final SliderSetting maxRotation = new SliderSetting("Макс. поворот (°/тик)", 8f, 2f, 45f, 1f);

    private final BooleanSetting antiModerator = new BooleanSetting("Защита от модераторов", true);
    private final SliderSetting modCheckRange = new SliderSetting("Проверка модераторов (м)", 20f, 5f, 50f, 1f);

    // --- Внутренние стейты ---
    private enum State { SEARCHING, APPROACHING, ATTACKING, LOOTING, SELLING, AFK }
    private State currentState = State.SEARCHING;

    private Entity targetCreeper = null;
    private long lastAttackTime = 0L;
    private long lastMoveTime = 0L;
    private long lastLootTime = 0L;
    private long lastSellTime = 0L;
    private long lastRotationTime = 0L;
    private long lastStrafeChange = 0L;
    private long lastJumpTime = 0L;

    private boolean strafeLeft = true;
    private boolean isAttacking = false;
    private int killsCount = 0;
    private int gunpowderCollected = 0;

    private BlockPos startPos = null;
    private float targetYaw = 0f;
    private float targetPitch = 0f;
    private float prevStepHeight = 0.6f;

    private final Random rnd = new Random();

    public CreeperFarm() {
        addSettings(mode, range, safeDistance, attackDelay, moveDelay,
                autoLoot, lootRange, onlyGunpowder,
                autoSell, sellThreshold,
                autoSwitch, criticals, critChance, strafeDuringAttack, strafeInterval,
                randomizeTimings, humanLikeMovements, limitRotationSpeed, maxRotation,
                antiModerator, modCheckRange);
    }

    @Subscribe
    public void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.world == null) return;
        if (!isEnabled()) return;

        long now = System.currentTimeMillis();

        // Инициализация
        if (startPos == null) {
            startPos = mc.player.getPosition();
            prevStepHeight = mc.player.stepHeight;
            targetYaw = mc.player.rotationYaw;
            targetPitch = mc.player.rotationPitch;
        }

        // Проверка модераторов
        if (mode.is("Незаметный") && antiModerator.getValue() && isModeratorNearby(modCheckRange.getValue())) {
            if (mc.player.connection != null) {
                mc.player.connection.sendPacket(new CChatMessagePacket("/hub"));
            }
            toggle();
            return;
        }

        // Проверка продажи (высший приоритет)
        if (autoSell.getValue() && shouldSell() && currentState != State.SELLING) {
            startSelling();
            return;
        }

        // Если продаем, не делаем ничего другого
        if (currentState == State.SELLING) {
            handleSelling();
            return;
        }

        // Сбор лута
        if (autoLoot.getValue() && now - lastLootTime > 200 && currentState != State.LOOTING) {
            if (collectLoot()) {
                currentState = State.LOOTING;
                lastLootTime = now;
                return;
            }
        }

        // Если собираем лут, ждем завершения
        if (currentState == State.LOOTING) {
            if (now - lastLootTime > 500) {
                currentState = State.SEARCHING;
            }
            return;
        }

        // Редкие случайные паузы
        if (randomizeTimings.getValue() && rnd.nextInt(1000) < 4) return;

        // Плавный поворот
        if (now - lastRotationTime > 40 + rnd.nextInt(30)) {
            smoothLook();
            lastRotationTime = now;
        }

        // FSM
        switch (currentState) {
            case SEARCHING -> doSearch();
            case APPROACHING -> doApproach();
            case ATTACKING -> doAttack();
            case AFK -> doAFK();
        }
    }

    // ========== СИСТЕМА ПРОДАЖИ ==========

    private boolean shouldSell() {
        if (System.currentTimeMillis() - lastSellTime < 10000) return false;

        int gunpowderCount = 0;
        for (int i = 0; i < mc.player.inventory.getSizeInventory(); i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == Items.GUNPOWDER) {
                gunpowderCount += stack.getCount();
            }
        }
        return gunpowderCount >= sellThreshold.getValue();
    }

    private void startSelling() {
        System.out.println("[CreeperFarm] Запуск продажи пороха");
        mc.player.sendChatMessage("/buyer");
        currentState = State.SELLING;
        lastSellTime = System.currentTimeMillis();
    }

    private void handleSelling() {
        if (mc.currentScreen instanceof ChestScreen) {
            ChestScreen chestScreen = (ChestScreen) mc.currentScreen;
            ChestContainer container = (ChestContainer) chestScreen.getContainer();

            // Ищем порох в инвентаре игрока
            for (int i = 0; i < 36; i++) {
                ItemStack stack = container.getSlot(i).getStack();
                if (!stack.isEmpty() && stack.getItem() == Items.GUNPOWDER) {
                    mc.playerController.windowClick(container.windowId, i, 0, net.minecraft.inventory.container.ClickType.PICKUP, mc.player);

                    // Ищем слот для продажи
                    for (int sellSlot = 10; sellSlot <= 25; sellSlot++) {
                        if (sellSlot >= container.inventorySlots.size()) break;

                        ItemStack sellStack = container.getSlot(sellSlot).getStack();
                        if (sellStack.isEmpty() || sellStack.getItem() == Items.GUNPOWDER) {
                            mc.playerController.windowClick(container.windowId, sellSlot, 0, net.minecraft.inventory.container.ClickType.PICKUP, mc.player);
                            System.out.println("[CreeperFarm] Продаем порох в слот " + sellSlot);
                            break;
                        }
                    }
                    break;
                }
            }

            mc.player.closeScreen();
            currentState = State.SEARCHING;
            System.out.println("[CreeperFarm] Продажа завершена");
        }
    }

    // ========== СИСТЕМА СБОРА ЛУТА ==========

    private boolean collectLoot() {
        double lootRangeValue = lootRange.getValue();
        List<ItemEntity> items = new ArrayList<>();

        for (Entity entity : mc.world.getAllEntities()) {
            if (entity instanceof ItemEntity) {
                ItemEntity item = (ItemEntity) entity;
                if (!item.getItem().isEmpty()) {
                    if (!onlyGunpowder.getValue() || item.getItem().getItem() == Items.GUNPOWDER) {
                        double distance = mc.player.getDistance(item);
                        if (distance <= lootRangeValue) {
                            items.add(item);
                        }
                    }
                }
            }
        }

        items.sort(Comparator.comparingDouble(item -> mc.player.getDistance(item)));

        for (ItemEntity item : items) {
            mc.playerController.interactWithEntity(mc.player, item, Hand.MAIN_HAND);
            if (item.getItem().getItem() == Items.GUNPOWDER) {
                gunpowderCollected += item.getItem().getCount();
            }
            System.out.println("[CreeperFarm] Подбираем: " + item.getItem().getDisplayName().getString());
            return true;
        }
        return false;
    }

    // ========== СИСТЕМА ОХОТЫ ==========

    private void doSearch() {
        if (!canMove()) return;

        List<CreeperEntity> creepers = findNearbyCreepers();
        if (!creepers.isEmpty()) {
            targetCreeper = creepers.get(0);
            setLookTarget(targetCreeper);
            currentState = State.APPROACHING;
            lastMoveTime = System.currentTimeMillis();
        } else {
            // Естественное сканирование
            if (humanLikeMovements.getValue()) {
                targetYaw = (targetYaw + 1 + rnd.nextInt(3)) % 360f;
                targetPitch = Math.max(-30f, Math.min(30f, targetPitch + (rnd.nextInt(3) - 1)));
            }
        }
    }

    private void doApproach() {
        if (targetCreeper == null || !targetCreeper.isAlive()) {
            targetCreeper = null;
            currentState = State.SEARCHING;
            return;
        }

        if (!canMove()) return;

        double dist = mc.player.getDistance(targetCreeper);
        float safe = safeDistance.getValue();

        if (dist > safe + 0.15) {
            // Подходим к криперу
            safeStepUp();
            moveTowardsEntity(targetCreeper);
        } else {
            // Достигли дистанции - атакуем
            releaseStepUp();
            currentState = State.ATTACKING;
            lastAttackTime = System.currentTimeMillis();
        }
    }

    private void doAttack() {
        if (targetCreeper == null || !targetCreeper.isAlive()) {
            finishAttack();
            killsCount++;
            currentState = State.SEARCHING;
            targetCreeper = null;
            return;
        }

        double dist = mc.player.getDistance(targetCreeper);
        float safe = safeDistance.getValue();

        if (dist > safe + 0.6) {
            currentState = State.APPROACHING;
            return;
        }

        setLookTarget(targetCreeper);

        if (strafeDuringAttack.getValue()) {
            doStrafeDuringAttack();
        }

        if (autoSwitch.getValue()) autoSelectWeapon();

        if (canAttack()) {
            performHitSequence();
        }
    }

    private void doAFK() {
        if (System.currentTimeMillis() - lastMoveTime > 15000 + rnd.nextInt(20000)) {
            lastMoveTime = System.currentTimeMillis();
            currentState = State.SEARCHING;
        }
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========

    private void performHitSequence() {
        if (targetCreeper == null) return;

        // Криты
        boolean doCrit = false;
        if (criticals.getValue()) {
            int chance = critChance.getValue().intValue();
            doCrit = rnd.nextInt(100) < chance && mc.player.onGround;
        }
        if (doCrit && System.currentTimeMillis() - lastJumpTime > 600) {
            mc.gameSettings.keyBindJump.setPressed(true);
            lastJumpTime = System.currentTimeMillis();
        }

        // Атака
        mc.playerController.attackEntity(mc.player, targetCreeper);
        mc.player.swingArm(Hand.MAIN_HAND);

        long base = Math.round(attackDelay.getValue().doubleValue());
        long jitter = randomizeTimings.getValue() ? (long) (base * (0.05f + rnd.nextFloat() * 0.12f)) : 0L;
        lastAttackTime = System.currentTimeMillis() + base + jitter;
    }

    private void doStrafeDuringAttack() {
        long now = System.currentTimeMillis();
        long interval = Math.round(strafeInterval.getValue().doubleValue());
        if (now - lastStrafeChange > interval) {
            strafeLeft = !strafeLeft;
            lastStrafeChange = now;
        }

        if (strafeLeft) {
            mc.gameSettings.keyBindLeft.setPressed(true);
            mc.gameSettings.keyBindRight.setPressed(false);
        } else {
            mc.gameSettings.keyBindRight.setPressed(true);
            mc.gameSettings.keyBindLeft.setPressed(false);
        }
    }

    private void finishAttack() {
        mc.gameSettings.keyBindLeft.setPressed(false);
        mc.gameSettings.keyBindRight.setPressed(false);
        mc.gameSettings.keyBindForward.setPressed(false);
        mc.gameSettings.keyBindBack.setPressed(false);
        mc.gameSettings.keyBindJump.setPressed(false);
    }

    private void moveTowardsEntity(Entity ent) {
        if (mc.player == null || ent == null) return;

        setLookTarget(ent);
        mc.gameSettings.keyBindForward.setPressed(true);
        lastMoveTime = System.currentTimeMillis();
    }

    private void safeStepUp() {
        if (mc.player != null && mc.player.stepHeight < 1.0f) {
            mc.player.stepHeight = 1.0f;
        }
    }

    private void releaseStepUp() {
        if (mc.player != null) {
            mc.player.stepHeight = prevStepHeight;
        }
    }

    private void setLookTarget(Entity entity) {
        if (entity == null || mc.player == null) return;

        Vector3d eyes = mc.player.getEyePosition(1.0F);
        Vector3d tpos = entity.getPositionVec().add(0.0, entity.getEyeHeight(), 0.0);

        double dx = tpos.x - eyes.x;
        double dy = tpos.y - eyes.y;
        double dz = tpos.z - eyes.z;
        double dxz = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-(Math.toDegrees(Math.atan2(dy, dxz))));

        setLookDirection(yaw, pitch);
    }

    private void setLookDirection(float yaw, float pitch) {
        targetYaw = yaw;
        targetPitch = Math.max(-90f, Math.min(90f, pitch));
    }

    private void smoothLook() {
        if (mc.player == null) return;

        float currYaw = mc.player.rotationYaw;
        float currPitch = mc.player.rotationPitch;

        float yawDiff = wrapDegrees(targetYaw - currYaw);
        float pitchDiff = targetPitch - currPitch;

        float max = limitRotationSpeed.getValue() ? maxRotation.getValue().floatValue() : 360f;
        float stepYaw = clamp(yawDiff, -max, max) * (0.28f + rnd.nextFloat() * 0.22f);
        float stepPitch = clamp(pitchDiff, -max / 2f, max / 2f) * (0.28f + rnd.nextFloat() * 0.22f);

        mc.player.rotationYaw = currYaw + stepYaw;
        mc.player.rotationPitch = currPitch + stepPitch;
    }

    private List<CreeperEntity> findNearbyCreepers() {
        List<CreeperEntity> out = new ArrayList<>();
        for (Entity ent : mc.world.getAllEntities()) {
            if (ent instanceof CreeperEntity && ent.isAlive()) {
                double d = mc.player.getDistance(ent);
                if (d <= range.getValue()) {
                    out.add((CreeperEntity) ent);
                }
            }
        }
        out.sort(Comparator.comparingDouble(a -> mc.player.getDistance(a)));
        return out;
    }

    private boolean isModeratorNearby(float checkRange) {
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p != mc.player && mc.player.getDistance(p) <= checkRange) {
                return true;
            }
        }
        return false;
    }

    private boolean canAttack() {
        return System.currentTimeMillis() >= lastAttackTime;
    }

    private boolean canMove() {
        long base = Math.round(moveDelay.getValue().doubleValue());
        long extra = randomizeTimings.getValue() ? rnd.nextInt(120) : 0;
        return System.currentTimeMillis() - lastMoveTime >= base + extra;
    }

    private void autoSelectWeapon() {
        if (mc.player == null) return;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof net.minecraft.item.SwordItem) {
                mc.player.inventory.currentItem = i;
                return;
            }
        }
    }

    private float wrapDegrees(double ang) {
        float a = (float) ang;
        a %= 360f;
        if (a >= 180f) a -= 360f;
        if (a < -180f) a += 360f;
        return a;
    }

    private float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }

    @Override
    public void onEnable() {
        super.onEnable();
        currentState = State.SEARCHING;
        targetCreeper = null;
        lastAttackTime = System.currentTimeMillis();
        lastMoveTime = System.currentTimeMillis();
        lastRotationTime = System.currentTimeMillis();
        lastStrafeChange = System.currentTimeMillis();
        startPos = mc.player != null ? mc.player.getPosition() : null;
        prevStepHeight = mc.player != null ? mc.player.stepHeight : 0.6f;
        if (mc.player != null) {
            targetYaw = mc.player.rotationYaw;
            targetPitch = mc.player.rotationPitch;
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        finishAttack();
        if (mc.player != null) mc.player.stepHeight = prevStepHeight;
        if (mc.gameSettings != null) {
            mc.gameSettings.keyBindForward.setPressed(false);
            mc.gameSettings.keyBindBack.setPressed(false);
            mc.gameSettings.keyBindLeft.setPressed(false);
            mc.gameSettings.keyBindRight.setPressed(false);
            mc.gameSettings.keyBindJump.setPressed(false);
        }
    }

    public String getDisplayInfo() {
        return killsCount + " kills | " + gunpowderCollected + " powder";
    }
}