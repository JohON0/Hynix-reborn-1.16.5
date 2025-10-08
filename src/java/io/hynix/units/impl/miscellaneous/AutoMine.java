package io.hynix.units.impl.miscellaneous;

import com.google.common.eventbus.Subscribe;
import io.hynix.events.impl.EventUpdate;
import io.hynix.units.api.Category;
import io.hynix.units.api.Unit;
import io.hynix.units.api.UnitRegister;
import io.hynix.units.settings.impl.BooleanSetting;
import io.hynix.units.settings.impl.ModeSetting;
import io.hynix.units.settings.impl.SliderSetting;
import io.hynix.utils.johon0.math.TimerUtils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

@UnitRegister(
        name = "AutoMine",
        category = Category.Miscellaneous
)
public class AutoMine extends Unit {

    // === ОСНОВНЫЕ НАСТРОЙКИ ===
    private final BooleanSetting enabled = new BooleanSetting("Включено", false);
    private final ModeSetting mode = new ModeSetting("Режим", "Зона", new String[]{"Зона", "Туннель", "Авто-поиск", "Сканер"});
    private final SliderSetting range = new SliderSetting("Дистанция", 5.0F, 1.0F, 15.0F, 0.5F);
    private final SliderSetting delay = new SliderSetting("Задержка", 50.0F, 0.0F, 200.0F, 5.0F);
    private final SliderSetting tunnelHeight = new SliderSetting("Высота туннеля", 3.0F, 1.0F, 5.0F, 1.0F);

    // === СИСТЕМА ТОЧЕК ===
    private final BooleanSetting autoMove = new BooleanSetting("Авто-движение", true);
    private final BooleanSetting showZoneInfo = new BooleanSetting("Показать инфо", false);

    // === НАСТРОЙКИ КОПАНИЯ ===
    private final BooleanSetting onlyPickaxe = new BooleanSetting("Только кирка", true);
    private final BooleanSetting autoSwitchPickaxe = new BooleanSetting("Авто-смена кирки", true);
    private final BooleanSetting preferFortune = new BooleanSetting("Приоритет удачи", true);
    private final BooleanSetting mineVeins = new BooleanSetting("Копать жилы", true);
    private final BooleanSetting avoidObsidian = new BooleanSetting("Избегать обсидиан", true);
    private final BooleanSetting avoidWaterLava = new BooleanSetting("Избегать воду/лаву", true);

    // === РЕСУРСЫ ===
    private final BooleanSetting mineDiamonds = new BooleanSetting("Алмазы", true);
    private final BooleanSetting mineEmeralds = new BooleanSetting("Изумруды", true);
    private final BooleanSetting mineIron = new BooleanSetting("Железо", true);
    private final BooleanSetting mineGold = new BooleanSetting("Золото", true);
    private final BooleanSetting mineRedstone = new BooleanSetting("Редстоун", true);
    private final BooleanSetting mineLapis = new BooleanSetting("Лазурит", true);
    private final BooleanSetting mineCoal = new BooleanSetting("Уголь", true);
    private final BooleanSetting mineAncientDebris = new BooleanSetting("Древний мусор", true);

    // === ПЕРЕМЕННЫЕ ===
    private final TimerUtils mineTimer = new TimerUtils();
    private final TimerUtils messageTimer = new TimerUtils();
    private final TimerUtils scanTimer = new TimerUtils();
    private BlockPos point1 = null;
    private BlockPos point2 = null;
    private BlockPos currentTarget = null;
    private final Queue<BlockPos> miningQueue = new ConcurrentLinkedQueue<>();
    private final Set<BlockPos> scannedBlocks = Collections.synchronizedSet(new HashSet<>());
    private int blocksMined = 0;
    private int oresMined = 0;
    private boolean wasShowInfoPressed = false;
    private boolean isScanning = false;

    private static final List<Block> VALUABLE_BLOCKS = Arrays.asList(
            Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE, Blocks.GOLD_ORE,
            Blocks.IRON_ORE, Blocks.REDSTONE_ORE, Blocks.LAPIS_ORE,
            Blocks.COAL_ORE, Blocks.ANCIENT_DEBRIS, Blocks.NETHER_QUARTZ_ORE
    );

    private static final List<Block> UNBREAKABLE_BLOCKS = Arrays.asList(
            Blocks.BEDROCK, Blocks.BARRIER, Blocks.OBSIDIAN, Blocks.ENDER_CHEST,
            Blocks.ANVIL, Blocks.ENCHANTING_TABLE, Blocks.NETHERITE_BLOCK
    );

    public AutoMine() {
        this.addSettings(
                enabled, mode, range, delay, tunnelHeight,
                autoMove, showZoneInfo,
                onlyPickaxe, autoSwitchPickaxe, preferFortune, mineVeins,
                avoidObsidian, avoidWaterLava,
                mineDiamonds, mineEmeralds, mineIron, mineGold,
                mineRedstone, mineLapis, mineCoal, mineAncientDebris
        );
    }

    // === ПУБЛИЧНЫЕ МЕТОДЫ ДЛЯ КОМАНД ===
    public void showMineInfo() {
        sendMessage("=== AutoMine Информация ===");
        sendMessage("Режим: " + mode.getValue());
        sendMessage("Дистанция: " + range.getValue());
        sendMessage("Задержка: " + delay.getValue() + "мс");
        sendMessage("Выкопано: " + blocksMined + " блоков (" + oresMined + " руд)");
        if (point1 != null) sendMessage("Точка 1: " + point1);
        if (point2 != null) sendMessage("Точка 2: " + point2);
        if (point1 != null && point2 != null) {
            sendMessage("Размер зоны: " + getZoneSize());
        }
    }

    public void setPoint1() {
        if (mc.player == null) return;
        point1 = mc.player.getPosition();
        sendMessage("Точка 1 установлена: " + point1);

        if (point2 != null) {
            generateMiningQueue();
        }
    }

    public void setPoint2() {
        if (mc.player == null) return;
        point2 = mc.player.getPosition();
        sendMessage("Точка 2 установлена: " + point2);

        if (point1 != null) {
            generateMiningQueue();
        }
    }

    public void clearPoints() {
        point1 = null;
        point2 = null;
        miningQueue.clear();
        scannedBlocks.clear();
        sendMessage("Точки очищены");
    }

    public void startScan() {
        if (mode.getValue().equals("Сканер")) {
            isScanning = true;
            scannedBlocks.clear();
            sendMessage("Сканирование начато...");
        } else {
            sendMessage("Включите режим 'Сканер' для использования этой команды");
        }
    }

    @Subscribe
    public void onUpdate(EventUpdate event) {
        if (!enabled.getValue() || mc.player == null || mc.world == null) return;
        if (mc.currentScreen instanceof ContainerScreen) return;

        handleInfoButton();

        if (onlyPickaxe.getValue() && !hasSuitableTool()) {
            if (!switchToBestPickaxe()) {
                return;
            }
        }

        switch (mode.getValue()) {
            case "Зона":
                handleZoneMode();
                break;
            case "Туннель":
                handleTunnelMode();
                break;
            case "Авто-поиск":
                handleAutoSearchMode();
                break;
            case "Сканер":
                handleScannerMode();
                break;
        }
    }

    private void handleInfoButton() {
        if (showZoneInfo.getValue() && !wasShowInfoPressed) {
            showMineInfo();
            wasShowInfoPressed = true;
        } else if (!showZoneInfo.getValue()) {
            wasShowInfoPressed = false;
        }
    }

    private void generateMiningQueue() {
        miningQueue.clear();

        if (point1 == null || point2 == null) return;

        int minX = Math.min(point1.getX(), point2.getX());
        int maxX = Math.max(point1.getX(), point2.getX());
        int minY = Math.min(point1.getY(), point2.getY());
        int maxY = Math.max(point1.getY(), point2.getY());
        int minZ = Math.min(point1.getZ(), point2.getZ());
        int maxZ = Math.max(point1.getZ(), point2.getZ());

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isValidBlock(pos)) {
                        miningQueue.add(pos);
                    }
                }
            }
        }

        sendMessage("Сгенерировано " + miningQueue.size() + " блоков для копания");
    }

    private String getZoneSize() {
        if (point1 == null || point2 == null) return "0x0x0";

        int width = Math.abs(point1.getX() - point2.getX()) + 1;
        int height = Math.abs(point1.getY() - point2.getY()) + 1;
        int length = Math.abs(point1.getZ() - point2.getZ()) + 1;

        return width + "x" + height + "x" + length;
    }

    private void handleZoneMode() {
        if (point1 == null || point2 == null || miningQueue.isEmpty()) {
            if (messageTimer.hasTimeElapsed(10000)) {
                sendMessage("Установите точки: .point 1, .point 2, .point clear");
                messageTimer.reset();
            }
            return;
        }

        long delayValue = (long) delay.getValue().floatValue();
        if (mineTimer.hasTimeElapsed(delayValue)) {
            BlockPos target = miningQueue.peek();

            if (target != null && isValidBlock(target)) {
                if (canReachBlock(target)) {
                    mineBlock(target);
                    miningQueue.poll();
                } else if (autoMove.getValue()) {
                    moveToBlock(target);
                }
            } else {
                miningQueue.poll();
            }

            mineTimer.reset();
        }
    }

    private void handleTunnelMode() {
        long delayValue = (long) delay.getValue().floatValue();
        if (mineTimer.hasTimeElapsed(delayValue)) {
            int height = (int) tunnelHeight.getValue().floatValue();

            for (int y = 0; y < height; y++) {
                BlockPos forward = mc.player.getPosition().add(
                        mc.player.getHorizontalFacing().getXOffset() * 2,
                        y,
                        mc.player.getHorizontalFacing().getZOffset() * 2
                );

                if (isValidBlock(forward)) {
                    mineBlock(forward);
                    break;
                }
            }

            mineTimer.reset();
        }
    }

    private void handleAutoSearchMode() {
        long delayValue = (long) delay.getValue().floatValue();
        if (mineTimer.hasTimeElapsed(delayValue)) {
            BlockPos target = findBestBlock();
            if (target != null) {
                if (canReachBlock(target)) {
                    mineBlock(target);
                } else if (autoMove.getValue()) {
                    moveToBlock(target);
                }
            }
            mineTimer.reset();
        }
    }

    private void handleScannerMode() {
        if (!isScanning) return;

        if (scanTimer.hasTimeElapsed(1000)) {
            scanArea();
            scanTimer.reset();
        }

        handleAutoSearchMode();
    }

    private void scanArea() {
        BlockPos playerPos = mc.player.getPosition();
        int rangeVal = (int) range.getValue().floatValue();

        for (int x = -rangeVal; x <= rangeVal; x++) {
            for (int y = -rangeVal; y <= rangeVal; y++) {
                for (int z = -rangeVal; z <= rangeVal; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (!scannedBlocks.contains(pos) && isValuableBlock(pos)) {
                        miningQueue.add(pos);
                        scannedBlocks.add(pos);
                    }
                }
            }
        }

        if (!miningQueue.isEmpty()) {
            sendMessage("Найдено " + miningQueue.size() + " ценных блоков");
        }
    }

    private BlockPos findBestBlock() {
        BlockPos playerPos = mc.player.getPosition();
        int rangeVal = (int) range.getValue().floatValue();

        for (int x = -rangeVal; x <= rangeVal; x++) {
            for (int y = -rangeVal; y <= rangeVal; y++) {
                for (int z = -rangeVal; z <= rangeVal; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (isValuableBlock(pos) && isValidBlock(pos)) {
                        return pos;
                    }
                }
            }
        }

        for (int x = -rangeVal; x <= rangeVal; x++) {
            for (int y = -rangeVal; y <= rangeVal; y++) {
                for (int z = -rangeVal; z <= rangeVal; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (isValidBlock(pos)) {
                        return pos;
                    }
                }
            }
        }

        return null;
    }

    private boolean isValidBlock(BlockPos pos) {
        if (mc.world.isAirBlock(pos)) return false;
        if (isUnbreakable(pos)) return false;

        Block block = mc.world.getBlockState(pos).getBlock();
        if (avoidWaterLava.getValue() &&
                (block == Blocks.WATER || block == Blocks.LAVA)) return false;
        if (avoidObsidian.getValue() && block == Blocks.OBSIDIAN) return false;

        return true;
    }

    private boolean isValuableBlock(BlockPos pos) {
        Block block = mc.world.getBlockState(pos).getBlock();

        if (block == Blocks.DIAMOND_ORE) return mineDiamonds.getValue();
        if (block == Blocks.EMERALD_ORE) return mineEmeralds.getValue();
        if (block == Blocks.IRON_ORE) return mineIron.getValue();
        if (block == Blocks.GOLD_ORE) return mineGold.getValue();
        if (block == Blocks.REDSTONE_ORE) return mineRedstone.getValue();
        if (block == Blocks.LAPIS_ORE) return mineLapis.getValue();
        if (block == Blocks.COAL_ORE) return mineCoal.getValue();
        if (block == Blocks.ANCIENT_DEBRIS) return mineAncientDebris.getValue();
        if (block == Blocks.NETHER_QUARTZ_ORE) return true;

        return false;
    }

    private boolean isUnbreakable(BlockPos pos) {
        return UNBREAKABLE_BLOCKS.contains(mc.world.getBlockState(pos).getBlock());
    }

    private boolean canReachBlock(BlockPos pos) {
        double distance = Math.sqrt(mc.player.getDistanceSq(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5
        ));
        return distance <= range.getValue().floatValue();
    }

    private void mineBlock(BlockPos pos) {
        lookAtBlock(pos);

        mc.gameSettings.keyBindAttack.setPressed(true);
        mc.player.swingArm(Hand.MAIN_HAND);

        currentTarget = pos;
        blocksMined++;

        if (isValuableBlock(pos)) {
            oresMined++;
        }

        new Thread(() -> {
            try {
                Thread.sleep(100);
                mc.gameSettings.keyBindAttack.setPressed(false);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void moveToBlock(BlockPos pos) {
        double dx = pos.getX() + 0.5 - mc.player.getPosX();
        double dz = pos.getZ() + 0.5 - mc.player.getPosZ();

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        mc.player.rotationYaw = yaw;

        mc.gameSettings.keyBindForward.setPressed(true);

        new Thread(() -> {
            try {
                Thread.sleep(500);
                mc.gameSettings.keyBindForward.setPressed(false);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void lookAtBlock(BlockPos pos) {
        Vector3d eyesPos = new Vector3d(
                mc.player.getPosX(),
                mc.player.getPosY() + mc.player.getEyeHeight(),
                mc.player.getPosZ()
        );

        Vector3d targetPos = new Vector3d(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
        );

        Vector3d diff = targetPos.subtract(eyesPos);
        double diffX = diff.x;
        double diffY = diff.y;
        double diffZ = diff.z;

        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

        mc.player.rotationYaw = yaw;
        mc.player.rotationPitch = pitch;
    }

    private boolean hasSuitableTool() {
        ItemStack mainHand = mc.player.getHeldItemMainhand();
        return mainHand.getItem() instanceof net.minecraft.item.PickaxeItem;
    }

    private boolean switchToBestPickaxe() {
        int bestSlot = -1;
        int bestFortuneLevel = -1;
        int bestEfficiency = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (stack.getItem() instanceof net.minecraft.item.PickaxeItem) {
                int fortune = EnchantmentHelper.getEnchantmentLevel(Enchantments.FORTUNE, stack);
                int efficiency = EnchantmentHelper.getEnchantmentLevel(Enchantments.EFFICIENCY, stack);

                if (preferFortune.getValue()) {
                    if (fortune > bestFortuneLevel ||
                            (fortune == bestFortuneLevel && efficiency > bestEfficiency)) {
                        bestSlot = i;
                        bestFortuneLevel = fortune;
                        bestEfficiency = efficiency;
                    }
                } else {
                    if (efficiency > bestEfficiency ||
                            (efficiency == bestEfficiency && fortune > bestFortuneLevel)) {
                        bestSlot = i;
                        bestFortuneLevel = fortune;
                        bestEfficiency = efficiency;
                    }
                }
            }
        }

        if (bestSlot != -1) {
            mc.player.inventory.currentItem = bestSlot;
            return true;
        }

        return false;
    }

    private void sendMessage(String message) {
        if (mc.player != null) {
            mc.player.sendMessage(new StringTextComponent("§6[AutoMine] §f" + message), mc.player.getUniqueID());
        }
    }

    @Override
    public void onEnable() {
        miningQueue.clear();
        scannedBlocks.clear();
        blocksMined = 0;
        oresMined = 0;
        isScanning = false;
        sendMessage("§aAutoMine включен! Используйте: §e.point 1§a, §e.point 2§a, §e.point clear");
    }

    @Override
    public void onDisable() {
        mc.gameSettings.keyBindAttack.setPressed(false);
        mc.gameSettings.keyBindForward.setPressed(false);
        sendMessage("Выключен. Выкопали: " + blocksMined + " блоков (" + oresMined + " руд)");
    }
}