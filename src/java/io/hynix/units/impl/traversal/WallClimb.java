package io.hynix.units.impl.traversal;

import com.google.common.eventbus.Subscribe;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CEntityActionPacket;
import net.minecraft.network.play.client.CHeldItemChangePacket;
import net.minecraft.network.play.client.CPlayerPacket;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;
import io.hynix.events.impl.EventMotion;
import io.hynix.events.impl.EventUpdate;
import io.hynix.units.api.Category;
import io.hynix.units.api.Unit;
import io.hynix.units.api.UnitRegister;
import io.hynix.units.settings.impl.ModeSetting;
import io.hynix.utils.johon0.math.SensUtils;
import io.hynix.utils.player.MoveUtils;

import java.util.Arrays;
import java.util.List;

@UnitRegister(
        name = "WallClimb",
        category = Category.Traversal
)
public class WallClimb extends Unit {

    private final ModeSetting mode = new ModeSetting("Режим", "Блоки", new String[]{"Блоки"});
    private final ModeSetting rotationMode = new ModeSetting("Ротация", "Клиент", new String[]{"Клиент", "Пакетная"})
            .setVisible(() -> mode.is("Блоки"));

    private int prevSlot = -1;
    public Vector2f rotate = new Vector2f(0.0F, 0.0F);
    private float lastYaw;
    private float lastPitch;

    public WallClimb() {
        this.addSettings(mode, rotationMode);
    }

    private float lerp(float start, float end, float t) {
        return start + t * (end - start);
    }

    public float getSpookyTimeFactor() {
        long currentTime = System.currentTimeMillis();
        float period = 5000.0F;
        return (float) (0.5 + 0.5 * Math.sin(currentTime / period * 2 * Math.PI));
    }

    public float applyReactionDelay(float current, float target, float delay) {
        return current + (target - current) * delay;
    }

    public float getRandomOffset(float range) {
        return (float) (Math.random() - 0.5) * range;
    }

    private void setRotations(float yaw, float pitch) {
        float smoothnessFactor = getSpookyTimeFactor();
        float yawDelta = MathHelper.wrapDegrees(yaw - rotate.x);
        float pitchDelta = MathHelper.wrapDegrees(pitch - rotate.y);

        float clampedYaw = Math.min(Math.max(Math.abs(yawDelta), 1.0F), 9999.0F);
        float clampedPitch = Math.max(Math.abs(pitchDelta), 1.0F);

        float interpolatedYaw = lerp(lastYaw, clampedYaw, smoothnessFactor);
        float interpolatedPitch = lerp(lastPitch, clampedPitch, smoothnessFactor);

        float randomYawOffset = getRandomOffset(0.1F);
        float randomPitchOffset = getRandomOffset(0.1F);
        interpolatedYaw += randomYawOffset;
        interpolatedPitch += randomPitchOffset;

        float targetYaw = rotate.x + (yawDelta > 0.0F ? interpolatedYaw : -interpolatedYaw);
        float targetPitch = MathHelper.clamp(rotate.y + (pitchDelta > 0.0F ? interpolatedPitch : -interpolatedPitch), -89.0F, 89.0F);

        // Используем SensUtils из Hynix вместо SensUtil
        float gcd = SensUtils.getGCDValue();
        targetYaw -= (targetYaw - rotate.x) % gcd;
        targetPitch -= (targetPitch - rotate.y) % gcd;

        float reactionTime = 0.05F;
        float finalYaw = applyReactionDelay(rotate.x, targetYaw, reactionTime);
        float finalPitch = applyReactionDelay(rotate.y, targetPitch, reactionTime);

        rotate = new Vector2f(finalYaw, finalPitch);
        lastYaw = interpolatedYaw;
        lastPitch = interpolatedPitch;
    }

    private void resetRotation() {
        if (mc.player != null) {
            rotate = new Vector2f(mc.player.rotationYaw, mc.player.rotationPitch);
        }
    }

    private float[] calculateAngle(Vector3d target) {
        if (mc.player == null) return new float[]{0, 0};

        Vector3d eyesPos = mc.player.getEyePosition(1.0F);
        Vector3d diff = target.subtract(eyesPos);

        double diffX = diff.z;
        double diffY = diff.y;
        double diffZ = diff.x;

        double dist = MathHelper.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(diffX, diffZ)) + 90.0);
        float pitch = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(diffY, dist)));

        return new float[]{yaw, pitch};
    }

    @Subscribe
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;

        int slotId = findBlockSlotId();
        if (slotId != -1) {
            Hand hand = (mc.player.getHeldItemOffhand().getItem() instanceof BlockItem &&
                    ((BlockItem) mc.player.getHeldItemOffhand().getItem()).getBlock().getDefaultState().isSolid()) ?
                    Hand.OFF_HAND : Hand.MAIN_HAND;

            ItemStack itemStack = hand.equals(Hand.OFF_HAND) ?
                    mc.player.getHeldItemOffhand() :
                    mc.player.inventory.getStackInSlot(slotId);

            BlockPos pos = findPos(-1);

            if (canPlace(itemStack) && !pos.equals(BlockPos.ZERO)) {
                Vector3d vec = Vector3d.copyCentered(pos);
                Direction direction = Direction.getFacingFromVector(
                        (float)(vec.x - mc.player.getPosX()),
                        0,
                        (float)(vec.z - mc.player.getPosZ())
                );

                float[] rotateAngles = calculateAngle(vec.subtract(new Vector3d(
                        direction.getXOffset(),
                        direction.getYOffset(),
                        direction.getZOffset()
                ).scale(0.5)));

                if (hand.equals(Hand.MAIN_HAND)) {
                    mc.player.connection.sendPacket(new CHeldItemChangePacket(slotId));
                    prevSlot = mc.player.inventory.currentItem;
                    mc.player.inventory.currentItem = slotId;
                }

                if (rotationMode.is("Пакетная")) {
                    // В Hynix используем стандартные методы для пакетной ротации
                    mc.player.connection.sendPacket(new CPlayerPacket.RotationPacket(
                            rotateAngles[0], rotateAngles[1], mc.player.onGround
                    ));
                } else {
                    setRotations(rotateAngles[0], rotateAngles[1]);
                }

                // Приседание для размещения блока
                if (!mc.player.isSneaking()) {
                    mc.player.connection.sendPacket(new CEntityActionPacket(mc.player, CEntityActionPacket.Action.PRESS_SHIFT_KEY));
                }

                // Размещение блока
                mc.playerController.processRightClickBlock(
                        mc.player,
                        mc.world,
                        hand,
                        new BlockRayTraceResult(vec, direction.getOpposite(), pos, false)
                );

                // Отпускаем Shift
                if (!mc.player.isSneaking()) {
                    mc.player.connection.sendPacket(new CEntityActionPacket(mc.player, CEntityActionPacket.Action.RELEASE_SHIFT_KEY));
                }

                // Возвращаем ротацию
                if (rotationMode.is("Пакетная") && mc.player != null) {
                    mc.player.connection.sendPacket(new CPlayerPacket.RotationPacket(
                            mc.player.rotationYaw, mc.player.rotationPitch, mc.player.onGround
                    ));
                }

                // Возвращаем слот
                if (hand.equals(Hand.MAIN_HAND) && prevSlot != -1) {
                    mc.player.connection.sendPacket(new CHeldItemChangePacket(prevSlot));
                    mc.player.inventory.currentItem = prevSlot;
                    prevSlot = -1;
                }
            }
        }
    }

    @Subscribe
    private void onMotion(EventMotion event) {
        if (mc.player == null) return;

        if (rotate.x != mc.player.rotationYaw || rotate.y != mc.player.rotationPitch) {
            float yaw = rotate.x;
            float pitch = rotate.y;

            event.setYaw(rotate.x);
            event.setPitch(rotate.y);

            mc.player.rotationYawHead = yaw;
            mc.player.renderYawOffset = yaw; // Упрощенная версия без PlayerUtil
            mc.player.rotationPitchHead = pitch;
        }
    }

    private int findBlockSlotId() {
        if (mc.player == null) return -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (stack.getItem() instanceof BlockItem) {
                return i;
            }
        }
        return -1;
    }

    private boolean canPlace(ItemStack stack) {
        if (mc.player == null || mc.world == null) return false;

        return mc.player.getPosY() + mc.player.getMotion().y < mc.player.getPosY() &&
                mc.world.getBlockState(new BlockPos(mc.player.getPositionVec().add(0, -0.01, 0)))
                        .getMaterial().isReplaceable();
    }

    private BlockPos findPos(int yOffset) {
        if (mc.player == null) return BlockPos.ZERO;

        BlockPos blockPos = mc.player.getPosition().add(0, yOffset, 0);
        List<BlockPos> positions = Arrays.asList(
                blockPos.west(), blockPos.east(),
                blockPos.south(), blockPos.north()
        );

        for (BlockPos pos : positions) {
            if (!mc.world.getBlockState(pos).isAir()) {
                return pos;
            }
        }

        return BlockPos.ZERO;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        resetRotation();
        prevSlot = -1;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        resetRotation();

        // Возвращаем слот при отключении
        if (prevSlot != -1 && mc.player != null) {
            mc.player.connection.sendPacket(new CHeldItemChangePacket(prevSlot));
            mc.player.inventory.currentItem = prevSlot;
            prevSlot = -1;
        }
    }
}