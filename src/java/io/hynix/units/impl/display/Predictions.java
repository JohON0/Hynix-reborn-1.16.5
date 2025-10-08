package io.hynix.units.impl.display;

import com.google.common.eventbus.Subscribe;
import io.hynix.events.impl.EventRender2D;
import io.hynix.events.impl.EventRender3D;
import io.hynix.managers.theme.Theme;
import io.hynix.units.api.Category;
import io.hynix.units.api.Unit;
import io.hynix.units.api.UnitRegister;
import io.hynix.units.settings.impl.BooleanSetting;
import io.hynix.utils.johon0.animations.easing.CompactAnimation;
import io.hynix.utils.johon0.render.color.ColorUtils;
import io.hynix.utils.johon0.render.render2d.ProjectionUtils;
import io.hynix.utils.johon0.render.render2d.RenderUtils;
import io.hynix.utils.text.font.ClientFonts;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EnderPearlEntity;
import net.minecraft.entity.projectile.*;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

/**
 * @author JohON0
 */
@UnitRegister(name = "Predictions", category = Category.Display, desc = "Показывает куда летят снаряды")
public class Predictions extends Unit {

    private final BooleanSetting enderPearls = new BooleanSetting("Эндер жемчуги", true);
    private final BooleanSetting arrows = new BooleanSetting("Стрелы", true);
    private final BooleanSetting potions = new BooleanSetting("Взрывные зелья", true);
    private final BooleanSetting tridents = new BooleanSetting("Трезубцы", true);
    private final BooleanSetting snowballs = new BooleanSetting("Снежки", true);

    record ProjectilePoint(Vector3d position, int ticks, ProjectileType type) {}

    enum ProjectileType {
        ENDER_PEARL("hynix/images/modules/timers/pearl.png"),
        ARROW("hynix/images/modules/timers/arrow.png"),
        POTION("hynix/images/modules/timers/potion.png"),
        TRIDENT("hynix/images/modules/timers/trident.png"),
        SNOWBALL("hynix/images/modules/timers/snowball.png");

        final String texture;

        ProjectileType(String texture) {
            this.texture = texture;
        }
    }

    private Map<String, CompactAnimation> animations = new HashMap<>();
    final List<ProjectilePoint> projectilePoints = new ArrayList<>();

    public Predictions() {
        addSettings(enderPearls, arrows, potions, tridents, snowballs);
    }

    @Subscribe
    public void fuckingRender(EventRender2D e) {
        for (ProjectilePoint point : projectilePoints) {
            Vector3d pos = point.position;
            Vector2f projection = ProjectionUtils.project(pos.x, pos.y - 0.3F, pos.z);
            int ticks = point.ticks;

            if (projection.x == Float.MAX_VALUE && projection.y == Float.MAX_VALUE) {
                continue;
            }

            double time = ticks * 50 / 1000.0;
            String text = String.format("%.1f" + " сек.", time);
            float width = ClientFonts.tenacity[14].getWidth(text);

            float textWidth = width + 11 + 11;

            float posX = projection.x - textWidth / 2;
            float posY = projection.y;

            int black = ColorUtils.getColor(10, 10, 10, 140);
            RenderUtils.drawRoundedRect(posX + 3, posY + 2 - 3, textWidth - 4, 16 - 3, 2, black);

            // Используем соответствующую текстуру для каждого типа снаряда
            RenderUtils.drawImage(new ResourceLocation(point.type.texture), posX + 5, posY, 10, 10, ColorUtils.setAlpha(-1, 255));
            ClientFonts.tenacity[14].drawString(e.getMatrixStack(), text, posX + 17, posY + 3f, -1);
        }
    }

    @Subscribe
    public void onRender(EventRender3D event) {
        glPushMatrix();

        glDisable(GL_TEXTURE_2D);
        glDisable(GL_DEPTH_TEST);

        glEnable(GL_BLEND);
        glEnable(GL_LINE_SMOOTH);

        Vector3d renderOffset = mc.getRenderManager().info.getProjectedView();

        glTranslated(-renderOffset.x, -renderOffset.y, -renderOffset.z);

        glLineWidth(2);

        buffer.begin(1, DefaultVertexFormats.POSITION);

        projectilePoints.clear();
        for (Entity entity : mc.world.getAllEntities()) {
            if (entity instanceof EnderPearlEntity && enderPearls.getValue()) {
                processProjectile(entity, ProjectileType.ENDER_PEARL);
            }
            else if (entity instanceof AbstractArrowEntity && arrows.getValue()) {
                processProjectile(entity, ProjectileType.ARROW);
            }
            else if (entity instanceof PotionEntity && potions.getValue()) {
                processProjectile(entity, ProjectileType.POTION);
            }
            else if (entity instanceof TridentEntity && tridents.getValue()) {
                processProjectile(entity, ProjectileType.TRIDENT);
            }
            else if (entity instanceof SnowballEntity && snowballs.getValue()) {
                processProjectile(entity, ProjectileType.SNOWBALL);
            }
        }

        tessellator.draw();

        glDisable(GL_BLEND);
        glDisable(GL_LINE_SMOOTH);

        glEnable(GL_TEXTURE_2D);
        glEnable(GL_DEPTH_TEST);

        glPopMatrix();
    }

    private void processProjectile(Entity projectile, ProjectileType type) {
        Vector3d motion = new Vector3d(projectile.getMotion().x, projectile.getMotion().y, projectile.getMotion().z);
        Vector3d pos = projectile.getPositionVec();
        float gravity = getGravityForEntity(type);

        Vector3d prevPos;
        int ticks = 0;

        for (int i = 0; i < 150; i++) {
            prevPos = pos;
            pos = pos.add(motion);
            motion = applyProjectilePhysics(projectile, motion, gravity);
            ColorUtils.setColor(Theme.mainRectColor);

            buffer.pos(prevPos.x, prevPos.y, prevPos.z).endVertex();

            RayTraceContext rayTraceContext = new RayTraceContext(prevPos, pos, RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE, projectile);
            BlockRayTraceResult blockHitResult = mc.world.rayTraceBlocks(rayTraceContext);
            boolean isLast = blockHitResult.getType() == RayTraceResult.Type.BLOCK;

            if (isLast) {
                pos = blockHitResult.getHitVec();
            }

            buffer.pos(pos.x, pos.y, pos.z).endVertex();

            if (blockHitResult.getType() == BlockRayTraceResult.Type.BLOCK || pos.y < -128) {
                projectilePoints.add(new ProjectilePoint(pos, ticks, type));
                break;
            }
            ticks++;
        }
    }

    private float getGravityForEntity(ProjectileType type) {
        switch (type) {
            case ENDER_PEARL:
            case SNOWBALL:
                return 0.03F;
            case ARROW:
            case POTION:
                return 0.05F;
            case TRIDENT:
                return 0.025F;
            default:
                return 0.03F;
        }
    }

    private Vector3d applyProjectilePhysics(Entity projectile, Vector3d motion, float gravity) {
        float drag = projectile.isInWater() ? 0.8F : 0.99F;
        motion = motion.scale(drag);
        motion = motion.subtract(0, gravity, 0);
        return motion;
    }

    private final Tessellator tessellator = Tessellator.getInstance();
    private final BufferBuilder buffer = tessellator.getBuffer();
}