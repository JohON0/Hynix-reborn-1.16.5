package io.hynix.units.impl.traversal;

import com.google.common.eventbus.Subscribe;

import io.hynix.HynixMain;
import io.hynix.events.impl.EventChangeWorld;
import io.hynix.events.impl.EventPacket;
import io.hynix.events.impl.EventUpdate;
import io.hynix.units.api.Category;
import io.hynix.units.api.Unit;
import io.hynix.units.api.UnitRegister;
import io.hynix.units.settings.impl.ModeSetting;
import io.hynix.utils.johon0.math.TimerUtils;
import io.hynix.utils.player.InventoryUtils;
import io.hynix.utils.player.MouseUtils;
import io.hynix.utils.player.MoveUtils;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.entity.Pose;
import net.minecraft.network.play.client.CConfirmTeleportPacket;
import net.minecraft.network.play.server.SPlayerPositionLookPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;

@UnitRegister(name = "LongJump", category = Category.Traversal, desc = "Автоматически прыгает вперед")
public class LongJump extends Unit {
    public ModeSetting mode = new ModeSetting("Мод", "Slap", "Slap", "FlagBoost", "FunTime", "ReallyWorld");

    public LongJump() {
        addSettings(mode);
    }

    private boolean placed;
    private int counter;
    private final TimerUtils slapTimer = new TimerUtils();
    private final TimerUtils flagTimer = new TimerUtils();

    @Subscribe
    public void onWorldChange(EventChangeWorld e) {
        toggle();
    }

    @Subscribe
    public void onUpdate(EventUpdate e) {
        if (HynixMain.getInstance().getModuleManager().getFreeCam().isEnabled()) return;
        if (mc.player == null || mc.world == null) return;

        // -----------------------------
        // Slap mode (плейс полублока)
        // -----------------------------
        if (mode.is("Slap") && !mc.player.isInWater()) {
            int slot = InventoryUtils.getSlotInInventoryOrHotbar();
            if (slot == -1) {
                print("У вас нет полублоков в хотбаре!");
                toggle();
                return;
            }
            int old = mc.player.inventory.currentItem;
            if (MouseUtils.rayTraceResult(2, mc.player.rotationYaw, 90, mc.player) instanceof BlockRayTraceResult result) {
                if (MoveUtils.isMoving()) {
                    if (mc.player.fallDistance >= 0.8
                            && mc.world.getBlockState(mc.player.getPosition()).isAir()
                            && !mc.world.getBlockState(result.getPos()).isAir()
                            && mc.world.getBlockState(result.getPos()).isSolid()
                            && !(mc.world.getBlockState(result.getPos()).getBlock() instanceof SlabBlock)
                            && !(mc.world.getBlockState(result.getPos()).getBlock() instanceof StairsBlock)) {
                        mc.player.inventory.currentItem = slot;
                        placed = true;
                        mc.playerController.processRightClickBlock(mc.player, mc.world, Hand.MAIN_HAND, result);
                        mc.player.inventory.currentItem = old;
                        mc.player.fallDistance = 0;
                    }
                    mc.gameSettings.keyBindJump.pressed = false;
                    if ((mc.player.isOnGround() && !mc.gameSettings.keyBindJump.pressed) && placed
                            && mc.world.getBlockState(mc.player.getPosition()).isAir()
                            && !mc.world.getBlockState(result.getPos()).isAir()
                            && mc.world.getBlockState(result.getPos()).isSolid()
                            && !(mc.world.getBlockState(result.getPos()).getBlock() instanceof SlabBlock)
                            && slapTimer.isReached(750)) {
                        mc.player.setPose(Pose.STANDING);
                        slapTimer.reset();
                        placed = false;
                    } else if (mc.player.isOnGround() && !mc.gameSettings.keyBindJump.pressed) {
                        mc.player.jump();
                        placed = false;
                    }
                }
            } else {
                if (mc.player.isOnGround() && !mc.gameSettings.keyBindJump.pressed) {
                    mc.player.jump();
                    placed = false;
                }
            }
        }

        // -----------------------------
        // FlagBoost (тп-пакеты + буст)
        // -----------------------------
        if (mode.is("FlagBoost")) {
            if (mc.player.getMotion().y != -0.0784000015258789) {
                flagTimer.reset();
            }

            if (!MoveUtils.isMoving()) {
                flagTimer.setTime(flagTimer.getTime() + 50L);
            }

            if (flagTimer.isReached(100) && MoveUtils.isMoving()) {
                flagHop();
                setMotionY(1.0);
            }
        }

        // -----------------------------
        // FunTime (резкий буст с прыжком)
        // -----------------------------
        if (mode.is("FunTime") && mc.player.hurtTime == 7) {
            MoveUtils.setCuttingSpeed(5.8);
            setMotionY(0.42);
        }

        // -----------------------------
        // ReallyWorld (чуть плавнее, но выше)
        // -----------------------------
        if (mode.is("ReallyWorld") && mc.player.hurtTime == 7) {
            MoveUtils.setCuttingSpeed(6.2);
            setMotionY(0.5);
        }
    }

    @Subscribe
    public void onPacket(EventPacket e) {
        if (HynixMain.getInstance().getModuleManager().getFreeCam().isEnabled()) return;

        if (mode.is("Slap")) {
            if (e.getPacket() instanceof SPlayerPositionLookPacket) {
                placed = false;
                counter = 0;
                mc.player.setPose(Pose.STANDING);
            }
        }

        if (mode.is("FlagBoost") && e.isReceive()) {
            if (e.getPacket() instanceof SPlayerPositionLookPacket look) {
                mc.player.setPosition(look.getX(), look.getY(), look.getZ());
                mc.player.connection.sendPacket(new CConfirmTeleportPacket(look.getTeleportId()));
                flagHop();
                e.cancel();
            }
        }
    }

    private void flagHop() {
        setMotionY(0.4229);
        MoveUtils.setSpeed(1.953f);
    }

    private void setMotionY(double y) {
        Vector3d motion = mc.player.getMotion();
        mc.player.setMotion(motion.x, y, motion.z);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        counter = 0;
        placed = false;
    }
}
