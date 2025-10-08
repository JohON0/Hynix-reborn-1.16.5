package io.hynix.units.impl.traversal;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import io.hynix.events.impl.EventUpdate;
import io.hynix.units.api.Category;
import io.hynix.units.api.Unit;
import io.hynix.units.api.UnitRegister;
import io.hynix.units.settings.impl.BooleanSetting;
import io.hynix.units.settings.impl.SliderSetting;
import io.hynix.utils.johon0.math.TimerUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

@UnitRegister(
        name = "ItemTeleport",
        category = Category.Traversal
)
public class ItemTeleport extends Unit {

    // Основные настройки
    private final SliderSetting distance = new SliderSetting("Дистанция", 8.0F, 3.0F, 15.0F, 0.5F);
    private final SliderSetting speed = new SliderSetting("Скорость", 1.5F, 0.5F, 3.0F, 0.1F);
    private final SliderSetting delay = new SliderSetting("Задержка", 1000.0F, 500.0F, 3000.0F, 100.0F);

    // Анти-кик система
    private final BooleanSetting antiKick = new BooleanSetting("Анти-Кик", true);
    private final SliderSetting safetyDelay = new SliderSetting("Безопасная задержка", 1500.0F, 500.0F, 5000.0F, 100.0F)
            .setVisible(() -> antiKick.getValue());
    private final BooleanSetting randomizeTiming = new BooleanSetting("Случайные паузы", true)
            .setVisible(() -> antiKick.getValue());
    private final SliderSetting maxTeleports = new SliderSetting("Макс телепортов", 5, 1, 10, 1)
            .setVisible(() -> antiKick.getValue());
    private final BooleanSetting limitVertical = new BooleanSetting("Ограничить вертикаль", true)
            .setVisible(() -> antiKick.getValue());

    // Безопасность
    private final BooleanSetting onlyValuable = new BooleanSetting("Только ценные", true);
    private final BooleanSetting avoidPlayers = new BooleanSetting("Избегать игроков", true);
    private final SliderSetting playerRadius = new SliderSetting("Радиус игроков", 6.0F, 3.0F, 15.0F, 0.5F)
            .setVisible(() -> avoidPlayers.getValue());
    private final BooleanSetting checkGround = new BooleanSetting("Проверять землю", true);

    // Режимы телепортации
    private final BooleanSetting smoothMode = new BooleanSetting("Плавный режим", true);
    private final SliderSetting smoothness = new SliderSetting("Плавность", 3.0F, 1.0F, 10.0F, 0.5F)
            .setVisible(() -> smoothMode.getValue());

    private Vector3d initialPosition = null;
    private ItemEntity currentTarget = null;
    private final TimerUtils teleportTimer = new TimerUtils();
    private final TimerUtils safetyTimer = new TimerUtils();
    private final Random random = new Random();
    private int teleportCount = 0;
    private long lastTeleportTime = 0;
    private boolean isWaiting = false;
    private int waitTicks = 0;

    private static final Set<Item> VALUABLE_ITEMS;

    public ItemTeleport() {
        this.addSettings(
                distance, speed, delay,
                antiKick, safetyDelay, randomizeTiming, maxTeleports, limitVertical,
                onlyValuable, avoidPlayers, playerRadius, checkGround,
                smoothMode, smoothness
        );
    }

    @Subscribe
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;

        // Анти-кик: проверка лимитов
        if (antiKick.getValue() && checkSafetyLimits()) {
            return;
        }

        // Ожидание между телепортами
        if (isWaiting) {
            waitTicks--;
            if (waitTicks <= 0) {
                isWaiting = false;
                teleportCount = 0;
            }
            return;
        }

        // Основная логика телепортации
        long currentDelay = getCurrentDelay();
        if (teleportTimer.hasTimeElapsed(currentDelay)) {
            handleTeleportation();
            teleportTimer.setLastMS(System.currentTimeMillis());
        }
    }

    private boolean checkSafetyLimits() {
        long currentTime = System.currentTimeMillis();

        // Лимит по количеству телепортов
        if (teleportCount >= maxTeleports.getValue()) {
            if (!isWaiting) {
                startSafetyWait();
            }
            return true;
        }

        // Минимальная задержка между телепортами
        if (currentTime - lastTeleportTime < safetyDelay.getValue()) {
            return true;
        }

        // Случайные паузы
        if (randomizeTiming.getValue() && random.nextInt(100) < 15) {
            return true;
        }

        return false;
    }

    private void startSafetyWait() {
        isWaiting = true;
        waitTicks = 100 + random.nextInt(100); // 5-10 секунд ожидания
        currentTarget = null;
    }

    private long getCurrentDelay() {
        long baseDelay = delay.getValue().longValue();

        if (randomizeTiming.getValue()) {
            // Случайное отклонение ±30%
            float variation = 0.7f + random.nextFloat() * 0.6f;
            baseDelay = (long)(baseDelay * variation);
        }

        return baseDelay;
    }

    private void handleTeleportation() {
        // Поиск новой цели если текущей нет
        if (currentTarget == null || !currentTarget.isAlive()) {
            if (!findNewTarget()) {
                return;
            }
        }

        // Телепортация к цели
        teleportToTarget();
    }

    private boolean findNewTarget() {
        float radius = distance.getValue();
        Vector3d playerPos = mc.player.getPositionVec();

        AxisAlignedBB searchBox = new AxisAlignedBB(
                playerPos.x - radius, playerPos.y - radius, playerPos.z - radius,
                playerPos.x + radius, playerPos.y + radius, playerPos.z + radius
        );

        List<ItemEntity> nearbyItems = mc.world.getEntitiesWithinAABB(ItemEntity.class, searchBox,
                entity -> isValidItem(entity) && !isNearPlayers(entity) && isSafeLocation(entity)
        );

        if (!nearbyItems.isEmpty()) {
            currentTarget = findBestTarget(nearbyItems);
            teleportCount++;
            lastTeleportTime = System.currentTimeMillis();
            return true;
        }

        return false;
    }

    private ItemEntity findBestTarget(List<ItemEntity> items) {
        ItemEntity bestTarget = items.get(0);
        double bestScore = calculateItemScore(bestTarget);

        for (int i = 1; i < items.size(); i++) {
            ItemEntity item = items.get(i);
            double score = calculateItemScore(item);
            if (score > bestScore) {
                bestTarget = item;
                bestScore = score;
            }
        }

        return bestTarget;
    }

    private double calculateItemScore(ItemEntity item) {
        double distance = mc.player.getDistance(item);
        double value = getItemValue(item.getItem().getItem());

        // Предпочтение ближайшим и ценным предметам
        return value / (distance + 1);
    }

    private double getItemValue(Item item) {
        if (item == Items.TOTEM_OF_UNDYING) return 100;
        if (item == Items.NETHERITE_INGOT) return 50;
        if (item == Items.ENCHANTED_GOLDEN_APPLE) return 40;
        if (item == Items.NETHERITE_HELMET || item == Items.NETHERITE_CHESTPLATE ||
                item == Items.NETHERITE_LEGGINGS || item == Items.NETHERITE_BOOTS) return 30;
        if (item == Items.NETHERITE_SWORD || item == Items.NETHERITE_PICKAXE) return 25;
        if (item == Items.ELYTRA) return 20;
        if (item == Items.DIAMOND_BLOCK) return 15;
        if (item == Items.GOLDEN_APPLE) return 10;
        if (item == Items.SHULKER_BOX) return 8;
        return 1;
    }

    private void teleportToTarget() {
        if (currentTarget == null || !currentTarget.isAlive()) {
            currentTarget = null;
            return;
        }

        Vector3d targetPos = currentTarget.getPositionVec();
        Vector3d playerPos = mc.player.getPositionVec();

        // Проверка вертикального ограничения
        if (limitVertical.getValue() && Math.abs(targetPos.y - playerPos.y) > 10) {
            currentTarget = null;
            return;
        }

        if (smoothMode.getValue()) {
            smoothTeleportTo(targetPos);
        } else {
            // Безопасная мгновенная телепортация
            safeInstantTeleport(targetPos);
        }

        // Проверяем достигли ли цели
        if (mc.player.getDistance(currentTarget) < 1.5) {
            currentTarget = null;
        }
    }

    private void smoothTeleportTo(Vector3d targetPos) {
        Vector3d currentPos = mc.player.getPositionVec();
        Vector3d direction = targetPos.subtract(currentPos);
        double distance = direction.length();

        if (distance < 0.3) {
            safeInstantTeleport(targetPos);
            return;
        }

        // Ограничение максимальной дистанции за один тик
        double maxMove = Math.min(distance, speed.getValue() / smoothness.getValue());
        Vector3d moveVector = direction.normalize().scale(maxMove);

        Vector3d newPos = currentPos.add(moveVector);

        // Проверка безопасности новой позиции
        if (isPositionSafe(newPos)) {
            mc.player.setPosition(newPos.x, newPos.y, newPos.z);
        } else {
            currentTarget = null; // Отмена телепортации к небезопасной цели
        }
    }

    private void safeInstantTeleport(Vector3d targetPos) {
        // Небольшое смещение для безопасности
        Vector3d safePos = targetPos.add(
                random.nextDouble() * 0.3 - 0.15,
                0.1, // Небольшое поднятие над землей
                random.nextDouble() * 0.3 - 0.15
        );

        if (isPositionSafe(safePos)) {
            mc.player.setPosition(safePos.x, safePos.y, safePos.z);
        }
    }

    private boolean isPositionSafe(Vector3d pos) {
        if (!checkGround.getValue()) return true;

        // Проверка что под ногами есть блок
        return !mc.world.isAirBlock(new net.minecraft.util.math.BlockPos(pos.x, pos.y - 1, pos.z));
    }

    private boolean isValidItem(ItemEntity entity) {
        if (entity == null || entity.getItem() == null) return false;

        if (onlyValuable.getValue()) {
            return VALUABLE_ITEMS.contains(entity.getItem().getItem());
        }

        return true;
    }

    private boolean isNearPlayers(ItemEntity entity) {
        if (!avoidPlayers.getValue()) return false;

        double radius = playerRadius.getValue();
        Vector3d itemPos = entity.getPositionVec();

        AxisAlignedBB playerCheckBox = new AxisAlignedBB(
                itemPos.x - radius, itemPos.y - radius, itemPos.z - radius,
                itemPos.x + radius, itemPos.y + radius, itemPos.z + radius
        );

        List<PlayerEntity> nearbyPlayers = mc.world.getEntitiesWithinAABB(PlayerEntity.class, playerCheckBox,
                player -> player != mc.player && player.isAlive()
        );

        return !nearbyPlayers.isEmpty();
    }

    private boolean isSafeLocation(ItemEntity entity) {
        if (!checkGround.getValue()) return true;

        Vector3d pos = entity.getPositionVec();
        return !mc.world.isAirBlock(new net.minecraft.util.math.BlockPos(pos.x, pos.y - 1, pos.z));
    }

    // Метод для получения информации о статусе
    public String getStatusInfo() {
        if (mc.player == null) return "Отключено";

        if (isWaiting) return "⏳ Ожидание: " + (waitTicks / 20) + "с";
        if (currentTarget != null) return "🎯 Цель: " + (int) mc.player.getDistance(currentTarget) + "м";

        return "🔍 Поиск... (" + teleportCount + "/" + maxTeleports.getValue() + ")";
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.initialPosition = mc.player != null ? mc.player.getPositionVec() : null;
        this.currentTarget = null;
        this.teleportCount = 0;
        this.isWaiting = false;
        this.waitTicks = 0;
        this.lastTeleportTime = System.currentTimeMillis();
        teleportTimer.setLastMS(System.currentTimeMillis());
        safetyTimer.setLastMS(System.currentTimeMillis());
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.initialPosition = null;
        this.currentTarget = null;
        this.isWaiting = false;
    }

    static {
        VALUABLE_ITEMS = new HashSet<>(Arrays.asList(
                Items.TOTEM_OF_UNDYING,
                Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
                Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE, Items.NETHERITE_SHOVEL,
                Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE,
                Items.SHULKER_BOX, Items.NETHERITE_INGOT,
                Items.TRIDENT, Items.ELYTRA, Items.DIAMOND_BLOCK,
                Items.ANCIENT_DEBRIS, Items.DIAMOND, Items.EMERALD_BLOCK
        ));
    }
}