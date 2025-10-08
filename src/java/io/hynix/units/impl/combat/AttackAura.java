package io.hynix.units.impl.combat;

import com.google.common.eventbus.Subscribe;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import io.hynix.HynixMain;
import io.hynix.events.impl.EventInput;
import io.hynix.events.impl.EventMotion;
import io.hynix.events.impl.EventUpdate;
import io.hynix.managers.friend.FriendManager;
import io.hynix.ui.clickgui.ClickGui;
import io.hynix.ui.notifications.impl.SuccessNotify;
import io.hynix.units.api.Category;
import io.hynix.units.api.Unit;
import io.hynix.units.api.UnitRegister;
import io.hynix.units.settings.api.Setting;
import io.hynix.units.settings.impl.BooleanSetting;
import io.hynix.units.settings.impl.ModeListSetting;
import io.hynix.units.settings.impl.ModeSetting;
import io.hynix.units.settings.impl.SliderSetting;
import io.hynix.utils.attackdev.HudUtils;
import io.hynix.utils.johon0.math.MathUtils;
import io.hynix.utils.johon0.math.SensUtils;
import io.hynix.utils.johon0.math.TimerUtils;
import io.hynix.utils.player.*;
import lombok.Getter;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.entity.player.RemoteClientPlayerEntity;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.IngameMenuScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.PointOfView;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ArmorStandEntity;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.monster.SlimeEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.UseAction;
import net.minecraft.network.play.client.CHeldItemChangePacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;
import org.lwjgl.opengl.GL11;

@UnitRegister(
        name = "AttackAura",
        category = Category.Combat
)
public class AttackAura extends Unit {
    public static boolean breakedShield = false;
    @Getter
    private final ModeSetting type = new ModeSetting("Тип", "Кастом", new String[]{"Кастом", "FunSky", "Резкая", "TEST", "FT Fov", "FT", "HvH"});
    @Getter
    private final ModeSetting elType = (new ModeSetting("Тип элитра-таргета", "Старый", new String[]{"Старый", "Новый"})).setVisible(() -> {
        return this.type.is("Кастом");
    });
    @Getter
    private final SliderSetting attackRange = new SliderSetting("Дистанция атаки", 3.0F, 3.0F, 6.0F, 0.1F);
    @Getter
    private final SliderSetting extraDist = (new SliderSetting("Доп дист", 0.0F, 0.0F, 30.0F, 0.5F)).setVisible(() -> {
        return this.isSmoothAura();
    });
    @Getter
    private final SliderSetting elRange = new SliderSetting("Элитра атака", 3.0F, 0.0F, 5.0F, 0.1F);
    private final SliderSetting elDist = new SliderSetting("Элитра дист", 0.0F, 0.0F, 50.0F, 1.0F);
    final ModeListSetting cancelattack = new ModeListSetting("Не бить...", new BooleanSetting[]{new BooleanSetting("Во время еды", false), new BooleanSetting("Через стены", false)});
    private final SliderSetting Border = new SliderSetting("Куда наводиться", 1.45F, -0.05F, 2.0F, 0.05F);
    private final SliderSetting PitchYawsp = (new SliderSetting("Скорость наводки", 0.85F, 0.1F, 2.0F, 0.01F)).setVisible(() -> {
        return this.type.is("Кастом");
    });
    private final SliderSetting LastYaw11 = (new SliderSetting("LastYaw", 0.85F, 0.1F, 9.0F, 0.05F)).setVisible(() -> {
        return this.type.is("Кастом");
    });
    private final SliderSetting LastYaw111 = (new SliderSetting("LastYaw2", 0.85F, 0.1F, 9.0F, 0.05F)).setVisible(() -> {
        return this.type.is("Кастом");
    });
    private final SliderSetting ClampedPitch1 = (new SliderSetting("ClampedPitch", 0.85F, 0.1F, 9.0F, 0.1F)).setVisible(() -> {
        return this.type.is("Кастом");
    });
    final ModeListSetting targets = new ModeListSetting("Таргеты", new BooleanSetting[]{new BooleanSetting("Игроки", true), new BooleanSetting("Голые", true), new BooleanSetting("Мобы", false), new BooleanSetting("Животные", false), new BooleanSetting("Друзья", false), new BooleanSetting("Голые невидимки", true), new BooleanSetting("Невидимки", true)});
    public static final ModeListSetting options = new ModeListSetting("Опции", new BooleanSetting[]{new BooleanSetting("Только криты", true), new BooleanSetting("Ломать щит", true), new BooleanSetting("Отжимать щит", true), new BooleanSetting("Ускорять ротацию при атаке", false), new BooleanSetting("Синхронизировать атаку с ТПС", false), new BooleanSetting("Фокусировать одну цель", true), new BooleanSetting("Коррекция движения", true)});
    private final BooleanSetting onlySpaceCritical = (new BooleanSetting("Только с пробелом", false)).setVisible(() -> {
        return (Boolean)options.get(0).getValue();
    });
    final ModeSetting sortBy = new ModeSetting("Сортировать по", "Всему", new String[]{"Дистанция", "Здоровье", "Броня", "Всему"});
    private final ModeSetting correctiontype = (new ModeSetting("Тип коррекции", "Сфокусированая", new String[]{"Сфокусированая", "Незаметная"})).setVisible(() -> {
        return (Boolean)options.is("Коррекция движения").getValue();
    });
    private final BooleanSetting displayRadius = (new BooleanSetting("Отображать радиус°", false)).setVisible(() -> {
        return this.type.is("FT Fov");
    });
    public final BooleanSetting perelet = new BooleanSetting("Перегон на элитре", true);
    public final SliderSetting pereletVal = (new SliderSetting("Значение перегона", 0.1F, 0.0F, 5.0F, 0.1F)).setVisible(() -> {
        return (Boolean)this.perelet.getValue();
    });
    private final TimerUtils stopWatch = new TimerUtils();
    public static Vector2f rotateVector = new Vector2f(0.0F, 0.0F);
    @Getter
    public static LivingEntity target;
    private Entity selected;
    int ticks = 0;
    boolean isRotated;
    final AutoPotionUse autoPotion;
    float lastYaw;
    float lastPitch;

    // Новые переменные для обхода античитов
    private final Random random = new Random();
    private int attackCooldown = 0;
    private long lastRotationTime = 0;
    private int rotationPattern = 0;
    private long lastPatternChange = 0;
    private int failedAttackAttempts = 0;
    private long lastServerTick = 0;

    public AttackAura(AutoPotionUse autoPotion) {
        this.autoPotion = autoPotion;
        this.addSettings(this.type, this.elType, this.displayRadius, this.attackRange, this.extraDist, this.elRange, this.elDist, this.perelet, this.pereletVal, this.targets, options, this.onlySpaceCritical, this.sortBy, this.correctiontype, this.cancelattack, this.Border, this.PitchYawsp, this.LastYaw11, this.LastYaw111, this.ClampedPitch1);
    }

    @Subscribe
    public void onInput(EventInput var1) {
        if ((Boolean)options.is("Коррекция движения").getValue() && this.correctiontype.is("Незаметная") && target != null && mc.player != null && !mc.player.isElytraFlying() && (this.isSmoothAura() || this.isSnapAura())) {
            MoveUtils.fixMovement(var1, rotateVector.x);
        }
    }

    public static Vector3d getVector(LivingEntity target) {
        double wHalf = (double)(target.getWidth() * target.getWidth() * target.getWidth() * target.getWidth());
        double yExpand = MathHelper.clamp(target.getPosYEye() - target.getPosY(), 0.0, (double)target.getHeight() / 1.7);
        double xExpand = MathHelper.clamp(mc.player.getPosX() - target.getPosX(), -wHalf, wHalf);
        double zExpand = MathHelper.clamp(mc.player.getPosZ() - target.getPosZ(), -wHalf, wHalf);
        return new Vector3d(target.getPosX() - mc.player.getPosX() + xExpand, target.getPosY() - mc.player.getPosYEye() + yExpand, target.getPosZ() - mc.player.getPosZ() + zExpand);
    }

    @Subscribe
    public void onUpdate(EventUpdate eventUpdate) {
        if (mc.player == null || mc.world == null) return;

        // Античит: проверка GUI и паузы
        if (mc.currentScreen != null && !(mc.currentScreen instanceof ChatScreen)) {
            return;
        }

        // Античит: динамические паузы
        if (shouldTakeBreak()) {
            return;
        }

        // Античит: отслеживание серверных тиков
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastServerTick > 1000) {
            failedAttackAttempts = Math.max(0, failedAttackAttempts - 1);
            lastServerTick = currentTime;
        }

        if ((Boolean)options.is("Фокусировать одну цель").getValue() && (target == null || !this.isValid(target)) || !(Boolean)options.is("Фокусировать одну цель").getValue()) {
            this.updateTargetValue();
        }

        if (target != null && (!this.autoPotion.isEnabled() || !this.autoPotion.isActive())) {
            this.isRotated = false;

            // Античит: умный кулдаун атаки
            if (this.shouldPlayerFalling() && this.stopWatch.hasTimeElapsed() && attackCooldown <= 0 && failedAttackAttempts < 3) {
                this.updateAttack();
                this.ticks = 2;
                if (this.type.is("TEST")) {
                    this.ticks = 3;
                }

                // Динамический кулдаун
                attackCooldown = getDynamicAttackCooldown();
            }

            if (attackCooldown > 0) {
                attackCooldown--;
            }

            double targetY = (double)(Float)this.Border.getValue();

            // Античит: ограничение частоты ротации
            if (currentTime - lastRotationTime < getRotationDelay()) {
                return;
            }
            lastRotationTime = currentTime;

            // Античит: смена паттерна ротации
            if (currentTime - lastPatternChange > 2000 + random.nextInt(3000)) {
                rotationPattern = random.nextInt(3);
                lastPatternChange = currentTime;
            }

            if (this.type.is("Резкая") || this.type.is("TEST") || this.type.is("FT")) {
                if (!mc.player.isElytraFlying()) {
                    if (this.ticks > 0) {
                        this.updateRotation(true, 75.0F, 75.0F, targetY);
                        --this.ticks;
                    } else {
                        this.reset();
                    }
                } else if (!this.isRotated) {
                    this.updateRotation(false, 80.0F, 35.0F, targetY);
                }
            }

            if (this.type.is("FT Fov")) {
                if (this.ticks > 0 && this.LookTarget(target)) {
                    this.updateRotation(true, 65.0F, 30.0F, targetY);
                    --this.ticks;
                } else {
                    this.reset();
                }
            } else if ((this.type.is("Кастом") || this.type.is("FunSky") || this.type.is("HvH")) && !this.isRotated) {
                this.updateRotation(false, 80.0F, 35.0F, targetY);
            }
        } else {
            this.stopWatch.setLastMS(0L);
            this.reset();
        }
    }

    // Новые методы для обхода античитов
    private boolean shouldTakeBreak() {
        // Случайные паузы для обхода детекции
        return random.nextInt(350) < 2 ||
                (System.currentTimeMillis() % 25000 < 50);
    }

    private int getDynamicAttackCooldown() {
        int baseCooldown = 1;
        // Добавляем случайную вариацию
        baseCooldown += random.nextInt(3);
        return baseCooldown;
    }

    private long getRotationDelay() {
        // Динамическая задержка в зависимости от режима
        switch ((String)this.type.getValue()) {
            case "HvH": return 20 + random.nextInt(15); // 20-35ms
            case "FT": return 35 + random.nextInt(20);  // 35-55ms
            case "FT Fov": return 40 + random.nextInt(25); // 40-65ms
            default: return 30 + random.nextInt(20); // 30-50ms
        }
    }

    @Subscribe
    private void onWalking(EventMotion e) {
        if (target != null && (!this.autoPotion.isEnabled() || !this.autoPotion.isActive())) {
            // Античит: применение ротации с вариациями
            float yaw = applyAdvancedRotation(rotateVector.x);
            float pitch = applyAdvancedRotation(rotateVector.y);

            e.setYaw(yaw);
            e.setPitch(pitch);

            // Разные комбинации для обхода античитов
            switch (rotationPattern) {
                case 0:
                    mc.player.rotationYawHead = yaw;
                    mc.player.renderYawOffset = yaw;
                    break;
                case 1:
                    mc.player.rotationYawHead = yaw;
                    break;
                case 2:
                    mc.player.renderYawOffset = yaw;
                    break;
            }

            mc.player.rotationPitchHead = pitch;
        }
    }

    private float applyAdvancedRotation(float rotation) {
        // Добавляем небольшую случайную вариацию
        float variance = (random.nextFloat() - 0.5f) * 0.3f;
        return rotation + variance;
    }

    private void updateTargetValue() {
        List<LivingEntity> targets = new ArrayList();
        if (mc.world == null || mc.player == null) return;

        Iterator var2 = mc.world.getAllEntities().iterator();

        while(true) {
            LivingEntity living;
            do {
                do {
                    Entity entity;
                    do {
                        if (!var2.hasNext()) {
                            if (targets.isEmpty()) {
                                target = null;
                                return;
                            }

                            targets.sort((entity1, entity2) -> {
                                double dist1 = mc.player.getDistance(entity1);
                                double dist2 = mc.player.getDistance(entity2);
                                double health1 = this.getEntityHealth(entity1);
                                double health2 = this.getEntityHealth(entity2);
                                double armor1 = entity1 instanceof PlayerEntity ? this.getEntityArmor((PlayerEntity)entity1) : 0;
                                double armor2 = entity2 instanceof PlayerEntity ? this.getEntityArmor((PlayerEntity)entity2) : 0;

                                if (this.sortBy.is("Дистанция")) {
                                    return Double.compare(dist1, dist2);
                                } else if (this.sortBy.is("Здоровье")) {
                                    return Double.compare(health1, health2);
                                } else if (this.sortBy.is("Броня")) {
                                    return Double.compare(armor1, armor2);
                                } else if (this.sortBy.is("Всему")) {
                                    double score1 = health1 + dist1 + armor1;
                                    double score2 = health2 + dist2 + armor2;
                                    return Double.compare(score1, score2);
                                } else {
                                    return 0;
                                }
                            });

                            target = targets.get(0);
                            return;
                        }

                        entity = (Entity)var2.next();
                    } while(!(entity instanceof LivingEntity));

                    living = (LivingEntity)entity;
                } while(!this.isValid(living));
            } while((Boolean)this.cancelattack.is("Через стены").getValue() && !mc.player.canEntityBeSeen(living));

            targets.add(living);
        }
    }

    private void updateRotation(boolean attack, float rotationYawSpeed, float rotationPitchSpeed, double targetY) {
        if (target == null || mc.player == null) return;

        Vector3d targetPos = target.getPositionVec();
        Vector3d vec = targetPos.add(0.0, targetY, 0.0).subtract(mc.player.getEyePosition(1.0F));
        this.isRotated = true;

        if (mc.player.isElytraFlying()) {
            if ((Boolean)this.perelet.getValue() && target.isElytraFlying() && HudUtils.calculateBPSTarget() > 10) {
                Vector3d targetPosition = target.getPositionVec();
                Vector3d scale = target.getForward().normalize().scale((double)(Float)this.pereletVal.getValue());
                vec = targetPosition.add(scale);
            } else {
                vec = MathUtils.getVector(target);
            }
        }

        double vecX = vec.x;
        double vecY = vec.y;
        double vecZ = vec.z;

        float yawToTarget = (float)MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(vecZ, vecX)) - 90.0);
        float pitchToTarget = (float)(-Math.toDegrees(Math.atan2(vecY, Math.hypot(vecX, vecZ))));

        // Античит: небольшая рандомизация целей
        yawToTarget += (random.nextFloat() - 0.5f) * 0.5f;
        pitchToTarget += (random.nextFloat() - 0.5f) * 0.3f;

        float yawDelta = MathHelper.wrapDegrees(yawToTarget - rotateVector.x);
        float pitchDelta = MathHelper.wrapDegrees(pitchToTarget - rotateVector.y);
        int roundedYaw = (int)yawDelta;

        if (!this.type.is("FT Fov") || this.LookTarget(target)) {
            float randomYawSpeed = (float)(60.0 + Math.random() * 12.0);
            float randomPitchSpeed = (float)(20.0 + Math.random() * 12.0);

            switch ((String)this.type.getValue()) {
                case "Кастом":
                    if (mc.player.isOnGround() || mc.player.isElytraFlying() && this.elType.is("Старый")) {
                        float clampedYaw = Math.min(Math.max(Math.abs(yawDelta), (Float)this.PitchYawsp.getValue()), rotationYawSpeed);
                        float clampedPitch = Math.min(Math.max(Math.abs(pitchDelta), (Float)this.PitchYawsp.getValue()), rotationPitchSpeed);

                        if (attack && this.selected != target && (Boolean)options.is("Ускорять ротацию при атаке").getValue()) {
                            clampedPitch = Math.max(Math.abs(pitchDelta), 0.5F);
                        } else {
                            clampedPitch /= (Float)this.ClampedPitch1.getValue();
                        }

                        if (Math.abs(clampedYaw - this.lastYaw) <= (Float)this.LastYaw11.getValue()) {
                            clampedYaw = this.lastYaw + (Float)this.LastYaw111.getValue();
                        }

                        float yaw = rotateVector.x + (yawDelta > 0.0F ? clampedYaw : -clampedYaw);
                        float pitch = MathHelper.clamp(rotateVector.y + (pitchDelta > 0.0F ? clampedPitch : -clampedPitch), -89.0F, 89.0F);
                        float gcd = SensUtils.getGCDValue();
                        yaw -= (yaw - rotateVector.x) % gcd;
                        pitch -= (pitch - rotateVector.y) % gcd;
                        rotateVector = new Vector2f(yaw, pitch);
                        this.lastYaw = clampedYaw;
                        this.lastPitch = clampedPitch;

                        if ((Boolean)options.is("Коррекция движения").getValue()) {
                            mc.player.rotationYawOffset = yaw;
                        }
                    }

                    if (this.elType.is("Новый") && mc.player.isElytraFlying()) {
                        float clampedYaw = rotateVector.x + (float)roundedYaw;
                        float clampedPitch = MathHelper.clamp(rotateVector.y + pitchDelta, -89.0F, 89.0F);
                        clampedYaw = (float)(clampedYaw + Math.random() * 2.0 * 0.5);
                        clampedPitch = (float)(clampedPitch + Math.random() / 2.0 * 0.10000000149011612);
                        clampedYaw = SensUtils.smoothGCD(clampedYaw, rotateVector.x, 0.02F);
                        clampedPitch = SensUtils.smoothGCD(clampedPitch, rotateVector.y, 0.02F);
                        rotateVector = new Vector2f(clampedYaw, clampedPitch);

                        if ((Boolean)options.is("Коррекция движения").getValue()) {
                            mc.player.rotationYawOffset = clampedYaw;
                        }
                    }
                    break;

                case "FunSky":
                    float clampedYaw = Math.min(Math.max(Math.abs(yawDelta), 0.48F), rotationYawSpeed);
                    float clampedPitch = Math.min(Math.max(Math.abs(pitchDelta), 0.48F), rotationPitchSpeed);

                    if (attack && this.selected != target && (Boolean)options.is("Ускорять ротацию при атаке").getValue()) {
                        clampedPitch = Math.max(Math.abs(pitchDelta), 0.5F);
                    } else {
                        clampedPitch /= 3.0F;
                    }

                    if (Math.abs(clampedYaw - this.lastYaw) <= 3.0F) {
                        clampedYaw = this.lastYaw + 3.1F;
                    }

                    float yaw = rotateVector.x + (yawDelta > 0.0F ? clampedYaw : -clampedYaw);
                    float pitch = MathHelper.clamp(rotateVector.y + (pitchDelta > 0.0F ? clampedPitch : -clampedPitch), -89.0F, 89.0F);
                    float gcd = SensUtils.getGCDValue();
                    yaw -= (yaw - rotateVector.x) % gcd;
                    pitch -= (pitch - rotateVector.y) % gcd;
                    rotateVector = new Vector2f(yaw, pitch);
                    this.lastYaw = clampedYaw;
                    this.lastPitch = clampedPitch;

                    if ((Boolean)options.is("Коррекция движения").getValue()) {
                        mc.player.rotationYawOffset = yaw;
                    }
                    break;

                case "Резкая":
                    clampedYaw = rotateVector.x + (float)roundedYaw;
                    clampedPitch = MathHelper.clamp(rotateVector.y + pitchDelta, -90.0F, 90.0F);
                    gcd = SensUtils.getGCDValue();
                    clampedYaw -= (clampedYaw - rotateVector.x) % gcd;
                    clampedPitch -= (clampedPitch - rotateVector.y) % gcd;
                    rotateVector = new Vector2f(clampedYaw, clampedPitch);

                    if ((Boolean)options.is("Коррекция движения").getValue()) {
                        mc.player.rotationYawOffset = clampedYaw;
                    }
                    break;

                case "HvH":
                    clampedYaw = rotateVector.x + (float)roundedYaw;
                    clampedPitch = MathHelper.clamp(rotateVector.y + pitchDelta, -80.0F, 80.0F);
                    clampedYaw = (float)(clampedYaw + Math.random() * 2.0 * 0.5);
                    clampedPitch = (float)(clampedPitch + Math.random() / 2.0 * 0.10000000149011612);
                    clampedYaw = SensUtils.smoothGCD(clampedYaw, rotateVector.x, 0.02F);
                    clampedPitch = SensUtils.smoothGCD(clampedPitch, rotateVector.y, 0.02F);
                    rotateVector = new Vector2f(clampedYaw, clampedPitch);

                    if ((Boolean)options.is("Коррекция движения").getValue()) {
                        mc.player.rotationYawOffset = clampedYaw;
                    }
                    break;

                case "TEST":
                    clampedYaw = rotateVector.x + (float)roundedYaw;
                    clampedPitch = MathHelper.clamp(rotateVector.y + pitchDelta, -80.0F, 80.0F);
                    clampedYaw = (float)(clampedYaw + (Math.random() * 2.0 - 1.0) * 0.5);
                    clampedPitch = (float)(clampedPitch + (Math.random() * 2.0 - 1.0) * 0.30000001192092896);
                    clampedYaw = SensUtils.smoothGCD(clampedYaw, rotateVector.x, 0.02F);
                    clampedPitch = SensUtils.smoothGCD(clampedPitch, rotateVector.y, 0.02F);
                    rotateVector = new Vector2f(clampedYaw, clampedPitch);

                    if ((Boolean)options.is("Коррекция движения").getValue()) {
                        mc.player.rotationYawOffset = clampedYaw;
                    }
                    break;

                case "FT Fov":
                    clampedYaw = Math.min(Math.max(Math.abs(yawDelta), 1.0F), randomYawSpeed);
                    clampedPitch = Math.min(Math.max(Math.abs(pitchDelta), 1.0F), randomPitchSpeed);
                    clampedPitch /= 3.0F;
                    yaw = rotateVector.x + (yawDelta > 0.0F ? clampedYaw : -clampedYaw);
                    pitch = MathHelper.clamp(rotateVector.y + (pitchDelta > 0.0F ? clampedPitch : -clampedPitch), -89.0F, 89.0F);
                    gcd = SensUtils.getGCD();
                    yaw -= (yaw - rotateVector.x) % gcd;
                    pitch -= (pitch - rotateVector.y) % gcd;
                    rotateVector = new Vector2f(yaw, pitch);
                    this.lastYaw = clampedYaw;
                    this.lastPitch = clampedPitch;

                    if ((Boolean)options.is("Коррекция движения").getValue()) {
                        mc.player.rotationYawOffset = yaw;
                    }
                    break;

                case "FT":
                    this.isRotated = true;
                    float[] rotations = new float[]{(float)Math.toDegrees(Math.atan2(vecZ, vecX)) - 90.0F, (float)(-Math.toDegrees(Math.atan2(vecY, Math.hypot(vecX, vecZ))))};
                    float deltaYaw = MathHelper.wrapDegrees(MathUtils.calculateDelta(rotations[0], rotateVector.x));
                    float deltaPitch = MathUtils.calculateDelta(rotations[1], rotateVector.y);
                    float yawSpeed = Math.min(Math.max(Math.abs(deltaYaw), 1.0F), 360.0F);
                    float pitchSpeed = Math.min(Math.max(Math.abs(deltaPitch), 1.0F), 90.0F);
                    float finalYaw = rotateVector.x + (deltaYaw > 0.0F ? yawSpeed : -yawSpeed);
                    float finalPitch = MathHelper.clamp(rotateVector.y + (deltaPitch > 0.0F ? pitchSpeed : -pitchSpeed), -90.0F, 90.0F);
                    gcd = SensUtils.getGCDValue();
                    finalYaw -= (finalYaw - rotateVector.x) % gcd;
                    finalPitch -= (finalPitch - rotateVector.y) % gcd;
                    rotateVector = new Vector2f(finalYaw, finalPitch);

                    if ((Boolean)options.is("Коррекция движения").getValue()) {
                        mc.player.rotationYawOffset = finalYaw;
                    }
            }
        }
    }

    private double attackDistance() {
        return (double)(Float)this.attackRange.getValue();
    }

    private boolean LookTarget(LivingEntity target) {
        if (target == null || mc.player == null) return false;

        Vector3d playerDirection = mc.player.getLook(0.0F);
        Vector3d targetDirection = target.getPositionVec().subtract(mc.player.getEyePosition(1.0F)).normalize();
        double angle = Math.toDegrees(Math.acos(playerDirection.dotProduct(targetDirection)));
        return angle <= 36.5;
    }

    private void updateAttack() {
        if (target == null || mc.player == null) return;

        float distanceAttck;
        if (mc.player.isElytraFlying()) {
            distanceAttck = (Float)this.elRange.getValue();
        } else {
            distanceAttck = (Float)this.attackRange.getValue();
        }

        double distanceToTarget = mc.player.getDistance(target);
        if (distanceToTarget > (double)distanceAttck) return;

        if ((Boolean)this.cancelattack.is("Во время еды").getValue() && mc.player.isHandActive()) return;

        if ((Boolean)this.cancelattack.is("Через стены").getValue() && !mc.player.canEntityBeSeen(target)) {
            target = null;
            this.updateTargetValue();
            return;
        }

        this.selected = target;
        if (this.selected != null && this.selected == target) {
            if (!this.type.is("FT Fov") || this.LookTarget(target)) {
                boolean wasSprinting = mc.player.isSprinting();
                if (wasSprinting) {
                    mc.player.setSprinting(false);
                }

                // Античит: проверка блокировки перед атакой
                if (mc.player.isBlocking() && (Boolean)options.is("Отжимать щит").getValue()) {
                    mc.playerController.onStoppedUsingItem(mc.player);
                }

                // Античит: случайная небольшая задержка перед атакой
                try {
                    Thread.sleep(random.nextInt(8));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                this.stopWatch.setLastMS(500L);
                boolean attackSuccess = mc.playerController.attackEntity(mc.player, target);
                mc.player.swingArm(Hand.MAIN_HAND);

                // Античит: отслеживание успешности атаки
                if (!attackSuccess) {
                    failedAttackAttempts++;
                } else {
                    failedAttackAttempts = Math.max(0, failedAttackAttempts - 1);
                }

                if (target instanceof PlayerEntity) {
                    PlayerEntity player = (PlayerEntity)target;
                    if ((Boolean)options.is("Ломать щит").getValue()) {
                        this.breakShieldPlayer(player);
                    }
                }

                if (wasSprinting) {
                    mc.player.setSprinting(true);
                }
            }
        }
    }

    private boolean shouldPlayerFalling() {
        if (mc.player == null) return false;

        boolean onSpace = (Boolean)this.onlySpaceCritical.getValue() && mc.player.isOnGround() && !mc.gameSettings.keyBindJump.isKeyDown();
        boolean var1 = mc.player.isInWater() && mc.player.areEyesInFluid(FluidTags.WATER) || mc.player.isInLava() || mc.player.isOnLadder() || mc.player.isPassenger() || mc.player.abilities.isFlying;
        float var2 = mc.player.getCooledAttackStrength((Boolean)options.is("Синхронизировать атаку с ТПС").getValue() ? HynixMain.getInstance().getTpsCalc().getAdjustTicks() : 1.5F);

        if (var2 < 0.92F) {
            return false;
        } else if (!var1 && (Boolean)options.is("Только криты").getValue()) {
            return onSpace || !mc.player.isOnGround() && mc.player.fallDistance > 0.0F;
        } else {
            return true;
        }
    }

    private boolean isValid(LivingEntity entity) {
        if (entity == null || mc.player == null) return false;

        float extra = 0.0F;
        float dist = 0.0F;

        if (entity instanceof ClientPlayerEntity) {
            return false;
        }

        if (this.isSmoothAura()) {
            if (mc.player.isElytraFlying()) {
                extra = (Float)this.elDist.getValue();
            } else {
                extra = (Float)this.extraDist.getValue();
            }
        }

        if (!this.isSmoothAura() && mc.player.isElytraFlying()) {
            extra = (Float)this.elDist.getValue();
        }

        if (mc.player.isElytraFlying() && !mc.player.inWater) {
            dist = (Float)this.elRange.getValue();
        } else {
            dist = (Float)this.attackRange.getValue();
        }

        if (entity.ticksExisted < 3) {
            return false;
        } else if (mc.player.getDistanceEyePos(entity) > (double)(extra + dist)) {
            return false;
        } else if (mc.player.getDistance(entity) > extra + dist) {
            return false;
        } else if (mc.player.getDistanceEyePos(entity) > (double)dist + (double)extra) {
            return false;
        } else {
            if (entity instanceof PlayerEntity) {
                PlayerEntity p = (PlayerEntity)entity;
                if (BotRemover.isBot(entity)) {
                    return false;
                }

                if (!(Boolean)this.targets.is("Друзья").getValue() && FriendManager.isFriend(p.getName().getString())) {
                    return false;
                }

                if (p.getName().getString().equalsIgnoreCase(mc.player.getName().getString())) {
                    return false;
                }
            }

            if (entity instanceof PlayerEntity && !(Boolean)this.targets.is("Игроки").getValue()) {
                return false;
            } else if (entity instanceof PlayerEntity && entity.getTotalArmorValue() == 0 && !(Boolean)this.targets.is("Голые").getValue()) {
                return false;
            } else if (entity instanceof PlayerEntity && entity.isInvisible() && entity.getTotalArmorValue() == 0 && !(Boolean)this.targets.is("Голые невидимки").getValue()) {
                return false;
            } else if (entity instanceof PlayerEntity && entity.isInvisible() && !(Boolean)this.targets.is("Невидимки").getValue()) {
                return false;
            } else if (entity instanceof MonsterEntity && !(Boolean)this.targets.is("Мобы").getValue()) {
                return false;
            } else if ((entity instanceof AnimalEntity || entity instanceof VillagerEntity) && !(Boolean)this.targets.is("Животные").getValue()) {
                return false;
            } else {
                return !entity.isInvulnerable() && entity.isAlive() && !(entity instanceof ArmorStandEntity);
            }
        }
    }

    private void breakShieldPlayer(PlayerEntity entity) {
        if (entity == null || mc.player == null) return;

        if (entity.isBlocking()) {
            int invSlot = InventoryUtils.getInstance().getAxeInInventory(false);
            int hotBarSlot = InventoryUtils.getInstance().getAxeInInventory(true);

            if (hotBarSlot == -1 && invSlot != -1) {
                int bestSlot = InventoryUtils.getInstance().findBestSlotInHotBar();
                mc.playerController.windowClick(0, invSlot, 0, ClickType.PICKUP, mc.player);
                mc.playerController.windowClick(0, bestSlot + 36, 0, ClickType.PICKUP, mc.player);
                mc.player.connection.sendPacket(new CHeldItemChangePacket(bestSlot));
                mc.playerController.attackEntity(mc.player, entity);
                mc.player.swingArm(Hand.MAIN_HAND);
                mc.player.connection.sendPacket(new CHeldItemChangePacket(mc.player.inventory.currentItem));
                mc.playerController.windowClick(0, bestSlot + 36, 0, ClickType.PICKUP, mc.player);
                mc.playerController.windowClick(0, invSlot, 0, ClickType.PICKUP, mc.player);
            }

            if (hotBarSlot != -1) {
                mc.player.connection.sendPacket(new CHeldItemChangePacket(hotBarSlot));
                mc.playerController.attackEntity(mc.player, entity);
                mc.player.swingArm(Hand.MAIN_HAND);
                mc.player.connection.sendPacket(new CHeldItemChangePacket(mc.player.inventory.currentItem));
            }

            HynixMain.getInstance().getNotifyManager().add(new SuccessNotify("Щит игрока " + target.getDisplayName() + " успешно сломан!", 2000));
            breakedShield = true;
        }
    }

    private void reset() {
        if (mc.player == null) return;

        if ((Boolean)options.is("Коррекция движения").getValue()) {
            mc.player.rotationYawOffset = -2.1474836E9F;
        }

        rotateVector = new Vector2f(mc.player.rotationYaw, mc.player.rotationPitch);
    }

    public void onEnable() {
        super.onEnable();
        this.reset();
        target = null;
        attackCooldown = 0;
        failedAttackAttempts = 0;
        lastRotationTime = System.currentTimeMillis();
        lastPatternChange = System.currentTimeMillis();
        lastServerTick = System.currentTimeMillis();
    }

    public void onDisable() {
        super.onDisable();
        this.reset();
        this.stopWatch.setLastMS(0L);
        target = null;
        attackCooldown = 0;
        failedAttackAttempts = 0;
    }

    private double getEntityArmor(PlayerEntity entityPlayer2) {
        if (entityPlayer2 == null) return 0.0;

        double d2 = 0.0;

        for(int i2 = 0; i2 < 4; ++i2) {
            ItemStack is = entityPlayer2.inventory.armorInventory.get(i2);
            if (is.getItem() instanceof ArmorItem) {
                d2 += this.getProtectionLvl(is);
            }
        }

        return d2;
    }

    private double getProtectionLvl(ItemStack stack) {
        if (stack.isEmpty()) return 0.0;

        Item var3 = stack.getItem();
        if (var3 instanceof ArmorItem i) {
            double damageReduceAmount = (double)i.getDamageReduceAmount();
            if (stack.isEnchanted()) {
                damageReduceAmount += (double) EnchantmentHelper.getEnchantmentLevel(Enchantments.PROTECTION, stack) * 0.25;
            }

            return damageReduceAmount;
        } else {
            return 0.0;
        }
    }

    private double getEntityHealth(LivingEntity ent) {
        if (ent == null) return 0.0;

        if (ent instanceof PlayerEntity player) {
            return (double)(player.getHealth() + player.getAbsorptionAmount()) * (this.getEntityArmor(player) / 20.0);
        } else {
            return (double)(ent.getHealth() + ent.getAbsorptionAmount());
        }
    }

    private boolean isSmoothAura() {
        return this.type.is("Кастом") || this.type.is("FunSky") || this.type.is("HvH");
    }

    private boolean isSnapAura() {
        return this.type.is("Резкая") || this.type.is("TEST") || this.type.is("FT Fov") || this.type.is("FT");
    }
}