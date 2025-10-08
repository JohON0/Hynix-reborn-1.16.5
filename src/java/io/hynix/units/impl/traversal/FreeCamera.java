package io.hynix.units.impl.traversal;

import com.google.common.eventbus.Subscribe;
import com.mojang.authlib.GameProfile;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.entity.player.RemoteClientPlayerEntity;
import net.minecraft.network.IPacket;
import net.minecraft.network.play.client.CPlayerAbilitiesPacket;
import net.minecraft.network.play.client.CPlayerPacket;
import net.minecraft.network.play.server.SPlayerPositionLookPacket;
import net.minecraft.world.GameType;
import io.hynix.events.impl.EventPacket;
import io.hynix.events.impl.EventUpdate;
import io.hynix.units.api.Category;
import io.hynix.units.api.Unit;
import io.hynix.units.api.UnitRegister;
import io.hynix.units.settings.impl.BooleanSetting;
import io.hynix.units.settings.impl.SliderSetting;
import io.hynix.utils.player.MoveUtils;

import java.util.UUID;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@UnitRegister(
        name = "FreeCamera",
        category = Category.Traversal
)
public class FreeCamera extends Unit {

    private final SliderSetting speed = new SliderSetting("Speed", 1.5F, 0.1F, 5.0F, 0.1F);
    private final BooleanSetting cancelPackets = new BooleanSetting("Cancel Packets", true);
    private final BooleanSetting showPosition = new BooleanSetting("Show Position", true);
    private final BooleanSetting autoDisable = new BooleanSetting("Auto Disable", true);

    private final int TEMP_ENTITY_ID = Integer.MAX_VALUE - 1337;
    private double x, y, z;
    private GameType prevGameType;
    private RemoteClientPlayerEntity fakePlayer;

    public FreeCamera() {
        this.addSettings(speed, cancelPackets, showPosition, autoDisable);
    }

    @Override
    public void onEnable() {
        super.onEnable();

        if (mc.player == null || mc.world == null) {
            this.toggle();
            return;
        }

        if (mc.player.getRidingEntity() != null) {
            this.toggle();
            return;
        }

        // Сохраняем текущую позицию и игровой режим
        prevGameType = mc.playerController.getCurrentGameType();
        x = mc.player.getPosX();
        y = mc.player.getPosY();
        z = mc.player.getPosZ();

        // Создаем фейкового игрока
        GameProfile gameProfile = new GameProfile(UUID.randomUUID(), mc.getSession().getUsername());
        fakePlayer = new RemoteClientPlayerEntity(mc.world, gameProfile);

        // Копируем инвентарь и состояние
        fakePlayer.inventory.copyInventory(mc.player.inventory);
        fakePlayer.setHealth(mc.player.getHealth());
        fakePlayer.setPositionAndRotation(x, mc.player.getBoundingBox().minY, z, mc.player.rotationYaw, mc.player.rotationPitch);
        fakePlayer.rotationYawHead = mc.player.rotationYawHead;
        fakePlayer.setGameType(GameType.ADVENTURE);

        // Добавляем фейкового игрока в мир
        mc.world.addEntity(TEMP_ENTITY_ID, fakePlayer);

        // Устанавливаем режим наблюдателя для реального игрока
        mc.playerController.setGameType(GameType.SPECTATOR);
        mc.player.setSneaking(false);
    }

    @Override
    public void onDisable() {
        super.onDisable();

        if (mc.player == null || mc.world == null) return;

        // Восстанавливаем игровой режим
        mc.playerController.setGameType(prevGameType);

        // Возвращаем игрока на исходную позицию
        mc.player.setMotion(0, 0, 0);
        mc.player.setVelocity(0, 0, 0);
        mc.player.setPosition(x, y, z);
        mc.player.setSneaking(false);

        // Удаляем фейкового игрока
        if (fakePlayer != null) {
            mc.world.removeEntityFromWorld(TEMP_ENTITY_ID);
            fakePlayer = null;
        }
    }

    @Subscribe
    public void onPacket(EventPacket event) {
        if (mc.player == null || !cancelPackets.getValue()) return;

        // Получаем пакет через packet() вместо getPacket()
        final IPacket<?> packet = event.getPacket();

        // Проверяем тип пакета и отменяем нужные
        if (packet instanceof CPlayerPacket || packet instanceof CPlayerAbilitiesPacket) {
            event.getPacket();
        }

        if (packet instanceof SPlayerPositionLookPacket) {
            event.getPacket();
        }
    }

    @Subscribe
    public void onUpdate(EventUpdate event) {
        if (mc.player == null) {
            if (autoDisable.getValue()) this.toggle();
            return;
        }

        if (!mc.player.isAlive() && autoDisable.getValue()) {
            this.toggle();
            return;
        }

        // Обновляем позицию для отображения
        if (showPosition.getValue()) {
            x = mc.player.getPosX();
            y = mc.player.getPosY();
            z = mc.player.getPosZ();
        }

        // Управление движением в FreeCam
        mc.player.setVelocity(0, 0, 0);
        mc.player.fallDistance = 0;

        float currentSpeed = speed.getValue();

        // Вертикальное движение
        double motionX = mc.player.getMotion().x;
        double motionY = mc.player.getMotion().y;
        double motionZ = mc.player.getMotion().z;

        if (mc.gameSettings.keyBindSneak.isKeyDown()) {
            motionY = -currentSpeed * 0.75;
        } else if (mc.gameSettings.keyBindJump.isKeyDown()) {
            motionY = currentSpeed * 0.75;
        } else {
            motionY = 0;
        }

        mc.player.setMotion(motionX, motionY, motionZ);

        // Горизонтальное движение
        MoveUtils.setSpeed(currentSpeed);
    }

    // Метод для получения информации о позиции (можно использовать в HUD)
    public String getPositionInfo() {
        if (mc.player == null) return "FreeCam: Disabled";

        double deltaX = x - mc.player.getPosX();
        double deltaY = y - mc.player.getPosY();
        double deltaZ = z - mc.player.getPosZ();

        return String.format("FreeCam: %.1f, %.1f, %.1f", deltaX, deltaY, deltaZ);
    }

    // Метод для получения дистанции от исходной точки
    public double getDistanceFromOrigin() {
        if (mc.player == null) return 0;
        return Math.sqrt(
                Math.pow(mc.player.getPosX() - x, 2) +
                        Math.pow(mc.player.getPosY() - y, 2) +
                        Math.pow(mc.player.getPosZ() - z, 2)
        );
    }
}