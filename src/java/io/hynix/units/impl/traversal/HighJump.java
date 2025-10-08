package io.hynix.units.impl.traversal;

import com.google.common.eventbus.Subscribe;
import io.hynix.events.impl.EventMotion;
import io.hynix.events.impl.EventPacket;
import io.hynix.events.impl.EventUpdate;
import io.hynix.units.api.Category;
import io.hynix.units.api.Unit;
import io.hynix.units.api.UnitRegister;
import io.hynix.units.settings.impl.BooleanSetting;
import io.hynix.units.settings.impl.ModeSetting;
import io.hynix.units.settings.impl.SliderSetting;
import io.hynix.utils.johon0.math.TimerUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.ShulkerEntity;
import net.minecraft.network.play.server.SPlayerPositionLookPacket;
import net.minecraft.util.math.vector.Vector3d;

import java.util.Random;

@UnitRegister(name = "HighJump", category = Category.Traversal, desc = "Увеличение высоты прыжка")
public class HighJump extends Unit {
    private final ModeSetting mode = new ModeSetting("Режим", "FunTime", "FunTime", "ReallyWorld", "Matrix");
    private final SliderSetting height = new SliderSetting("Высота", 1.2f, 1.1f, 2.0f, 0.1f);
    private final BooleanSetting onlyOnGround = new BooleanSetting("Только на земле", true);
    private final BooleanSetting autoDisable = new BooleanSetting("Авто-выключение", false);
    private final SliderSetting disableAfter = new SliderSetting("Выкл через (сек)", 3, 1, 10, 1).setVisible(() -> autoDisable.getValue());

    private final TimerUtils timer = new TimerUtils();
    private int jumpTicks = 0;
    private boolean wasOnGround = false;
    private boolean hasShulkerNearby = false;

    private final Random random = new Random();

    public HighJump() {
        addSettings(mode, height, onlyOnGround, autoDisable, disableAfter);
    }

    @Subscribe
    public void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.world == null) return;

        if (mode.is("FunTime")) {
            hasShulkerNearby = checkShulkerNearby();
            if (!hasShulkerNearby) {
                if (jumpTicks > 0) jumpTicks = 0;
                return;
            }
        }

        if (onlyOnGround.getValue() && !mc.player.isOnGround()) return;

        if (mc.player.isOnGround() && !wasOnGround) {
            jumpTicks = 0;
        }
        wasOnGround = mc.player.isOnGround();

        if (mc.player.movementInput.jump && mc.player.isOnGround()) {
            handleJump();
        }
    }

    @Subscribe
    public void onMotion(EventMotion e) {
        if (mc.player == null) return;
        if (mode.is("FunTime") && !hasShulkerNearby) return;

        if (!mc.player.isOnGround() && jumpTicks > 0) {
            handleInAirMotion();
        }
    }

    @Subscribe
    public void onPacket(EventPacket e) {
        if (e.getPacket() instanceof SPlayerPositionLookPacket) {
            jumpTicks = 0;
            timer.reset();
        }
    }

    private boolean checkShulkerNearby() {
        if (mc.world == null || mc.player == null) return false;

        Vector3d playerPos = mc.player.getPositionVec();
        double checkRadius = 10.0;

        for (Entity entity : mc.world.getAllEntities()) {
            if (entity instanceof ShulkerEntity) {
                double distance = entity.getPositionVec().distanceTo(playerPos);
                if (distance <= checkRadius) {
                    return true;
                }
            }
        }
        return false;
    }

    private void handleJump() {
        jumpTicks = getJumpTicks();
        float baseMotion = 0.42f;

        double currentX = mc.player.getMotion().x;
        double currentZ = mc.player.getMotion().z;

        switch (mode.getValue()) {
            case "FunTime":
                mc.player.setMotion(currentX, baseMotion * Math.min(height.getValue() + (random.nextFloat() * 0.05f), 1.3f), currentZ);
                break;
            case "ReallyWorld":
                mc.player.setMotion(currentX, baseMotion * Math.min(height.getValue() + (random.nextFloat() * 0.05f), 1.6f), currentZ);
                break;
            case "Matrix":
                mc.player.setMotion(currentX, baseMotion * height.getValue(), currentZ);
                break;
        }
    }

    private void handleInAirMotion() {
        jumpTicks--;
        float boost = getBoostValue();

        if (jumpTicks > getMinBoostTicks() && boost > 0) {
            Vector3d motion = mc.player.getMotion();
            double newY = motion.y + boost + (random.nextFloat() * 0.005f);
            newY = Math.min(newY, getMaxMotion());
            mc.player.setMotion(motion.x, newY, motion.z);
        }
    }

    private int getJumpTicks() {
        switch (mode.getValue()) {
            case "FunTime": return 8;
            case "ReallyWorld": return 10;
            case "Matrix": return 12;
            default: return 8;
        }
    }

    private int getMinBoostTicks() {
        switch (mode.getValue()) {
            case "FunTime": return 7;
            case "ReallyWorld": return 5;
            case "Matrix": return 3;
            default: return 7;
        }
    }

    private float getBoostValue() {
        switch (mode.getValue()) {
            case "FunTime": return 0.015f;
            case "ReallyWorld": return 0.025f;
            case "Matrix": return 0.04f;
            default: return 0.015f;
        }
    }

    private float getMaxMotion() {
        switch (mode.getValue()) {
            case "FunTime": return 1.0f;
            case "ReallyWorld": return 1.1f;
            case "Matrix": return 1.2f;
            default: return 1.0f;
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        jumpTicks = 0;
        wasOnGround = mc.player != null && mc.player.isOnGround();
        hasShulkerNearby = false;
        timer.reset();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        jumpTicks = 0;
        hasShulkerNearby = false;
    }

    public boolean isShulkerNearby() {
        return hasShulkerNearby;
    }

    public String getStatus() {
        if (!isEnabled()) return "Выключен";
        if (mode.is("FunTime") && !hasShulkerNearby) return "Ждет шалкера";
        return "Активен (" + mode.getValue() + ")";
    }
}
