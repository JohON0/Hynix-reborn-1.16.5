package io.hynix.units.impl.display;

import com.google.common.eventbus.Subscribe;
import io.hynix.HynixMain;
import io.hynix.managers.friend.FriendManager;
import io.hynix.events.impl.EventChangeWorld;
import io.hynix.events.impl.EventRender2D;
import io.hynix.units.api.Category;
import io.hynix.units.api.Unit;
import io.hynix.units.api.UnitRegister;
import io.hynix.units.impl.combat.BotRemover;
import io.hynix.units.settings.impl.BooleanSetting;
import io.hynix.units.settings.impl.ColorSetting;
import io.hynix.units.settings.impl.ModeListSetting;
import io.hynix.units.settings.impl.ModeSetting;
import io.hynix.managers.theme.Theme;
import io.hynix.utils.johon0.math.animation.Animation;
import io.hynix.utils.johon0.math.animation.util.Easings;
import io.hynix.utils.player.MoveUtils;
import io.hynix.utils.johon0.render.color.ColorUtils;
import io.hynix.utils.johon0.render.render2d.RenderUtils;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.settings.PointOfView;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.WaterMobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.opengl.GL11;

@UnitRegister(name = "Arrows", category = Category.Display, desc = "Показывает стрелки где находится игрок")
public class Arrows extends Unit {
    public ModeListSetting targets = new ModeListSetting("Отображать",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Предметы", false),
            new BooleanSetting("Мобы", false)
    );

    final ModeSetting playerColorMode = new ModeSetting("Выбор цвета игроков", "Client", "Client", "Custom").setVisible(() -> targets.is("Игроки").getValue());
    final ModeSetting mobColorMode = new ModeSetting("Выбор цвета мобов", "Custom", "Client", "Custom").setVisible(() -> targets.is("Мобы").getValue());
    final ModeSetting friendColorMode = new ModeSetting("Выбор цвета друзей", "Custom", "Client", "Custom").setVisible(() -> targets.is("Игроки").getValue());
    final ModeSetting itemColorMode = new ModeSetting("Выбор цветов предметов", "Custom", "Client", "Custom").setVisible(() -> targets.is("Предметы").getValue());
    final ColorSetting playerColor = new ColorSetting("Цвет игроков", -1).setVisible(() -> playerColorMode.is("Custom") && targets.is("Игроки").getValue());
    final ColorSetting mobColor = new ColorSetting("Цвет мобов", -1).setVisible(() -> mobColorMode.is("Custom") && targets.is("Мобы").getValue());
    final ColorSetting friendColor = new ColorSetting("Цвет друзей", ColorUtils.rgb(94, 255, 69)).setVisible(() -> friendColorMode.is("Custom") && targets.is("Игроки").getValue());
    final ColorSetting itemColor = new ColorSetting("Цвет предметов", ColorUtils.rgb(255, 72, 69)).setVisible(() -> itemColorMode.is("Custom") && targets.is("Предметы").getValue());

    public Arrows() {
        addSettings(targets, playerColorMode, mobColorMode, friendColorMode, itemColorMode, playerColor, mobColor, friendColor, itemColor);
    }

    @Setter
    @Getter
    private boolean render = false;
    private final Animation yawAnimation = new Animation();
    private final Animation moveAnimation = new Animation();
    private final Animation openAnimation = new Animation();
    private float addX;
    private float addY;

    public int getPlayerColor() {
        return playerColorMode.is("Custom") ? playerColor.getValue() : Theme.rectColor;
    }

    public int getMobColor() {
        return mobColorMode.is("Custom") ? mobColor.getValue() : Theme.rectColor;
    }

    public int getFriendColor() {
        return friendColorMode.is("Custom") ? friendColor.getValue() : Theme.rectColor;
    }

    public int getItemColor() {
        return itemColorMode.is("Custom") ? itemColor.getValue() : Theme.rectColor;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        setRender(false);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        setRender(true);
    }

    @Subscribe
    public void onWorldChange(EventChangeWorld e) {
        setRender(isRender());
    }

    @Subscribe
    public void onDisplay(EventRender2D e) {
        openAnimation.update();
        moveAnimation.update();
        yawAnimation.update();

        if (!render && openAnimation.getValue() == 0 && openAnimation.isFinished()) return;

        final float moveAnim = calculateMoveAnimation();

        openAnimation.run(render ? 1 : 0, 0.3, Easings.BACK_OUT, true);
        moveAnimation.run(render ? moveAnim : 0, 0.5, Easings.BACK_OUT, true);
        yawAnimation.run(mc.gameRenderer.getActiveRenderInfo().getYaw(), 0.3, Easings.BACK_OUT, true);

        final double cos = Math.cos(Math.toRadians(yawAnimation.getValue()));
        final double sin = Math.sin(Math.toRadians(yawAnimation.getValue()));
        double radius = moveAnimation.getValue();
        final double xOffset = (scaled().x / 2F) - radius;
        final double yOffset = (scaled().y / 2F) - radius;

        for (Entity entity : mc.world.getAllEntities()) {
            if (BotRemover.isBot(entity)) continue;
            if (!(entity instanceof PlayerEntity && targets.is("Игроки").getValue()
                    || entity instanceof ItemEntity && targets.is("Предметы").getValue()
                    || (entity instanceof AnimalEntity || entity instanceof MobEntity || entity instanceof WaterMobEntity) && targets.is("Мобы").getValue()
            )) continue;
            if (entity == mc.player) continue;
            if (entity == HynixMain.getInstance().getModuleManager().getFreeCam().getFakePlayer()) continue;

            Vector3d vector3d = mc.gameRenderer.getActiveRenderInfo().getProjectedView();
            final double xWay = (((entity.getPosX() + (entity.getPosX() - entity.lastTickPosX) * mc.getRenderPartialTicks()) - vector3d.x) * 0.01D);
            final double zWay = (((entity.getPosZ() + (entity.getPosZ() - entity.lastTickPosZ) * mc.getRenderPartialTicks()) - vector3d.z) * 0.01D);
            final double rotationY = -(zWay * cos - xWay * sin);
            final double rotationX = -(xWay * cos + zWay * sin);
            final double angle = Math.toDegrees(Math.atan2(rotationY, rotationX));
            double x = ((radius * Math.cos(Math.toRadians(angle))) + xOffset + radius);
            double y = ((radius * Math.sin(Math.toRadians(angle))) + yOffset + radius);
            Crosshair crosshair = HynixMain.getInstance().getModuleManager().getCrosshair();
            if (crosshair.isEnabled() && crosshair.mode.is("Орбиз") && !crosshair.staticCrosshair.getValue() && mc.gameSettings.getPointOfView() == PointOfView.FIRST_PERSON) {
                addX = crosshair.getAnimatedYaw();
                addY = crosshair.getAnimatedPitch();
            } else {
                addX = addY = 0;
            }

            x += addX;
            y += addY;

            if (isValidRotation(rotationX, rotationY, radius)) {
                GL11.glPushMatrix();
                GL11.glTranslated(x, y, 0D);
                GL11.glRotated(angle, 0D, 0D, 1D);
                GL11.glRotatef(90F, 0F, 0F, 1F);

                int color = 0;
                if (entity instanceof MobEntity || entity instanceof AnimalEntity || entity instanceof WaterMobEntity) {
                    color = getMobColor();
                } else if (FriendManager.isFriend(TextFormatting.getTextWithoutFormattingCodes(entity.getName().getString()))) {
                    color = getFriendColor();
                } else if (entity instanceof PlayerEntity) {
                    color = getPlayerColor();
                } else if (entity instanceof ItemEntity) {
                    color = getItemColor();
                }

                RenderUtils.drawImage(new ResourceLocation("hynix/images/arrow.png"), -8.0F, -9.0F, 16.0F, 16.0F, color);
                GL11.glPopMatrix();
            }
        }
    }

    private float calculateMoveAnimation() {
        float set = 35;
        if (mc.currentScreen instanceof ContainerScreen<?> container) {
            set = Math.max(container.ySize, container.xSize) / 2F + 50;
        }
        float moveAnim = set;
        if (MoveUtils.isMoving()) {
            moveAnim += mc.player.isSneaking() ? 5 : 10;
        } else if (mc.player.isSneaking()) {
            moveAnim -= 2;
        }
        return moveAnim;
    }

    private boolean isValidRotation(double rotationX, double rotationY, double radius) {
        final double mrotY = -rotationY;
        final double mrotX = -rotationX;
        return MathHelper.sqrt(mrotX * mrotX + mrotY * mrotY) < radius;
    }

}
