package io.hynix.units.impl.combat;

import com.google.common.eventbus.Subscribe;
import io.hynix.events.impl.EventUpdate;
import io.hynix.units.api.Category;
import io.hynix.units.api.Unit;
import io.hynix.units.api.UnitRegister;
import io.hynix.units.settings.impl.BooleanSetting;
import io.hynix.units.settings.impl.ModeSetting;
import io.hynix.units.settings.impl.SliderSetting;
import io.hynix.utils.player.InventoryUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EnderCrystalEntity;
import net.minecraft.entity.item.TNTEntity;
import net.minecraft.entity.item.minecart.TNTMinecartEntity;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.item.AirItem;
import net.minecraft.item.Items;
import net.minecraft.item.SkullItem;
import net.minecraft.potion.Effects;

import java.util.Iterator;

@UnitRegister(name = "AutoTotem", category = Category.Combat,
        desc = "Берет в левую руку тотем при указанном здоровье")
public class AutoTotem extends Unit {

    // === РЕЖИМЫ ===
    private final ModeSetting mode = new ModeSetting("Режим", "Funtime", new String[]{"Funtime", "Обычный"});

    // === ОСНОВНЫЕ НАСТРОЙКИ ===
    private final SliderSetting health = new SliderSetting("Здоровье", 3.5F, 1.0F, 20.0F, 0.1F);
    private final BooleanSetting swapBack = new BooleanSetting("Возвращать предмет", true);
    private final BooleanSetting preserveEnchants = new BooleanSetting("Сохранять чар", true);
    private final BooleanSetting noBallSwitch = new BooleanSetting("Не брать если шар", false);
    private final BooleanSetting elytraMode = new BooleanSetting("Брать в элитре", true);

    // === НАСТРОЙКИ ДЛЯ РЕЖИМА FUNTIME ===
    private final SliderSetting healthElytra = new SliderSetting("Здоровье в элитре", 3.0F, 1.0F, 20.0F, 0.5F);
    private final BooleanSetting goldenHearts = new BooleanSetting("Золотые сердца", true);
    private final BooleanSetting crystals = new BooleanSetting("Кристаллы", true);
    private final BooleanSetting anchor = new BooleanSetting("Якорь", true);
    private final BooleanSetting fall = new BooleanSetting("Падение", true);

    private int oldItem = -1;
    private int oldSlot = -1;
    private int swapCooldown = 0;

    public AutoTotem() {
        addSettings(mode, health, swapBack, preserveEnchants, noBallSwitch, elytraMode);

        // Условная видимость настроек для Funtime
        healthElytra.setVisible(() -> mode.getValue().equals("Funtime") && elytraMode.getValue());
        goldenHearts.setVisible(() -> mode.getValue().equals("Funtime"));
        crystals.setVisible(() -> mode.getValue().equals("Funtime"));
        anchor.setVisible(() -> mode.getValue().equals("Funtime"));
        fall.setVisible(() -> mode.getValue().equals("Funtime"));

        addSettings(healthElytra, goldenHearts, crystals, anchor, fall);
    }

    @Subscribe
    private void handleEventUpdate(EventUpdate event) {
        if (swapCooldown > 0) {
            swapCooldown--;
            return;
        }

        int totemSlot = InventoryUtils.getItemSlot(Items.TOTEM_OF_UNDYING);
        boolean hasTotemInHand = mc.player.getHeldItemOffhand().getItem() == Items.TOTEM_OF_UNDYING;
        boolean hasItemInOffhand = !(mc.player.getHeldItemOffhand().getItem() instanceof AirItem);

        if (shouldSwapTotem()) {
            if (totemSlot >= 0 && !hasTotemInHand) {
                // Сохраняем текущий предмет если нужно
                if (hasItemInOffhand && oldItem == -1 && swapBack.getValue()) {
                    oldItem = mc.player.inventory.currentItem;
                    oldSlot = getOffhandItemSlot();
                }

                // Безопасный свап для Funtime
                if (mode.getValue().equals("Funtime")) {
                    safeSwapFuntime(totemSlot);
                } else {
                    // Обычный режим
                    mc.playerController.windowClick(0, totemSlot, 40, ClickType.SWAP, mc.player);
                }

                swapCooldown = 3; // Задержка для античита
            }
        } else if (oldItem != -1 && swapBack.getValue()) {
            // Возвращаем предмет обратно
            returnItem();
        }
    }

    private void safeSwapFuntime(int totemSlot) {
        // Безопасный свап для Funtime с рандомными задержками
        if (Math.random() > 0.7) {
            mc.playerController.windowClick(0, totemSlot, 40, ClickType.SWAP, mc.player);
        }

        // Добавляем небольшую задержку
        try {
            Thread.sleep((long) (10 + Math.random() * 20));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void returnItem() {
        if (oldSlot != -1) {
            mc.playerController.windowClick(0, oldSlot, 40, ClickType.SWAP, mc.player);
            oldItem = -1;
            oldSlot = -1;
            swapCooldown = 2;
        }
    }

    private int getOffhandItemSlot() {
        // Получаем слот оффхенда
        for (int i = 0; i < 45; i++) {
            if (mc.player.inventory.getStackInSlot(i) == mc.player.getHeldItemOffhand()) {
                return i;
            }
        }
        return -1;
    }

    private boolean shouldSwapTotem() {
        if (mode.getValue().equals("Funtime")) {
            return shouldSwapFuntime();
        } else {
            return shouldSwapNormal();
        }
    }

    private boolean shouldSwapFuntime() {
        // Проверка здоровья с учетом золотых сердец
        float absorption = goldenHearts.getValue() && mc.player.isPotionActive(Effects.ABSORPTION) ?
                mc.player.getAbsorptionAmount() : 0.0F;

        // Основная проверка здоровья
        if (mc.player.getHealth() + absorption <= health.getValue().floatValue()) {
            return true;
        }

        // Проверка элитры
        if (elytraMode.getValue() && checkElytra()) {
            return true;
        }

        // Проверка кристаллов
        if (crystals.getValue() && checkCrystal()) {
            return true;
        }

        // Проверка падения
        if (fall.getValue() && checkFall()) {
            return true;
        }

        // Проверка якоря
        if (anchor.getValue() && checkAnchor()) {
            return true;
        }

        return false;
    }

    private boolean shouldSwapNormal() {
        // Простая проверка здоровья для обычного режима
        return mc.player.getHealth() <= health.getValue().floatValue() ||
                (elytraMode.getValue() && checkElytraSimple()) ||
                mc.player.fallDistance > 5.0F;
    }

    private boolean checkElytra() {
        return mc.player.inventory.armorInventory.get(2).getItem() == Items.ELYTRA &&
                mc.player.getHealth() <= healthElytra.getValue().floatValue();
    }

    private boolean checkElytraSimple() {
        return mc.player.inventory.armorInventory.get(2).getItem() == Items.ELYTRA &&
                mc.player.getHealth() <= 5.0F;
    }

    private boolean checkFall() {
        return mc.player.fallDistance > 5.0F;
    }

    private boolean checkAnchor() {
        // Проверка на наличие респавн якоря рядом
        Iterator<Entity> iterator = mc.world.getAllEntities().iterator();
        while (iterator.hasNext()) {
            Entity entity = iterator.next();
            if (entity instanceof EnderCrystalEntity && mc.player.getDistance(entity) <= 6.0F) {
                return true;
            }
        }
        return false;
    }

    private boolean checkCrystal() {
        Iterator<Entity> iterator = mc.world.getAllEntities().iterator();
        while (iterator.hasNext()) {
            Entity entity = iterator.next();
            if ((entity instanceof EnderCrystalEntity ||
                    entity instanceof TNTEntity ||
                    entity instanceof TNTMinecartEntity) &&
                    mc.player.getDistance(entity) <= 6.0F) {
                return true;
            }
        }
        return false;
    }

    private boolean isBall() {
        return noBallSwitch.getValue() &&
                mc.player.getHeldItemOffhand().getItem() instanceof SkullItem;
    }

    private void reset() {
        oldItem = -1;
        oldSlot = -1;
        swapCooldown = 0;
    }

    @Override
    public void onEnable() {
        reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        // Возвращаем предмет при выключении
        if (swapBack.getValue() && oldItem != -1) {
            returnItem();
        }
        reset();
        super.onDisable();
    }
}