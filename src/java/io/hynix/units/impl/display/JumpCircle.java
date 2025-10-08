package io.hynix.units.impl.display;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import io.hynix.events.impl.EventChangeWorld;
import io.hynix.events.impl.EventPreRender3D;
import io.hynix.events.impl.EventJump;
import io.hynix.units.api.Category;
import io.hynix.units.api.Unit;
import io.hynix.units.api.UnitRegister;
import io.hynix.units.settings.impl.ModeSetting;
import io.hynix.units.settings.impl.SliderSetting;
import io.hynix.utils.johon0.render.color.ColorUtils;
import io.hynix.utils.johon0.render.render2d.RectUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14C;

import java.util.ArrayList;
import java.util.List;

@UnitRegister(name = "JumpCircle", category = Category.Display, desc = "Создает кружки при прыжке")
public class JumpCircle extends Unit {
    private final ModeSetting texture = new ModeSetting("Вид", "Glow", "Glow", "Swastika", "Circle", "GlowedCircle");
    private final SliderSetting maxRadius = new SliderSetting("Радиус", 4, 1, 10, 0.1f);
    private final SliderSetting circleSpeed = new SliderSetting("Скорость", 1700f, 300f, 5000f, 10f);
    private final String staticLoc = "hynix/images/modules/jumpcircles/";

    private ResourceLocation jumpTexture() {
        return new ResourceLocation(staticLoc + texture.getValue().toLowerCase() + ".png");
    }

    public JumpCircle() {
        addSettings(texture, maxRadius, circleSpeed);
    }

    private void addCircleForEntity(final Entity entity) {
        Vector3d vec = getVec3dFromEntity(entity).add(0.D, .005D, 0.D);
        BlockPos pos = new BlockPos(vec);
        BlockState state = mc.world.getBlockState(pos);
        if (state.getBlock() == Blocks.SNOW) {
            vec = vec.add(0.D, .125D, 0.D);
        }
        circles.add(new JumpRenderer(vec, circles.size()));
    }

    @Subscribe
    public void onJump(EventJump e) {
        addCircleForEntity(mc.player);
    }

    @Subscribe
    public void onRender(EventPreRender3D event) {
        circles.removeIf((final JumpRenderer circle) -> circle.getDeltaTime() >= 5.D);

        if (circles.isEmpty()) return;

        setupDraw(event.getMatrix(), () -> circles.forEach(circle -> doCircle(event.getMatrix(), circle.pos, maxRadius.getValue(), 1.F - circle.getDeltaTime(), circle.getIndex() * 30)));
    }

    private void doCircle(MatrixStack stack, final Vector3d pos, double maxRadius, float deltaTime, int index) {
        float waveDelta = valWave1(1.F - deltaTime);
        float alphaPC = (float) (valWave2(waveDelta));
        float radius = (float) (easeOutCirc(valWave2(1.F - deltaTime)) * (!texture.is("Circle") ? maxRadius : maxRadius * 1.5));

        ResourceLocation res = jumpTexture();
        mc.getTextureManager().bindTexture(res);

        stack.push();
        stack.translate(pos.x - radius / 2.D, pos.y, pos.z - radius / 2.D);
        stack.rotate(Vector3f.XP.rotationDegrees(90.F));
        customRotatedObject2D(stack, 0, 0, radius, radius, 0);

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        buffer.pos(stack.getLast().getMatrix(), 0, 0, 0).tex(0, 0).color(getColor(index, alphaPC)).endVertex();
        buffer.pos(stack.getLast().getMatrix(), 0, radius, 0).tex(0, 1).color(getColor(90 + index, alphaPC)).endVertex();
        buffer.pos(stack.getLast().getMatrix(), radius, radius, 0).tex(1, 1).color(getColor(180 + index, alphaPC)).endVertex();
        buffer.pos(stack.getLast().getMatrix(), radius, 0, 0).tex(1, 0).color(getColor(270 + index, alphaPC)).endVertex();
        tessellator.draw();
        stack.pop();
    }

    @Subscribe
    public void onChange(EventChangeWorld e) {
        reset();
    }

    @Override
    public void onEnable() {
        reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        reset();
        super.onDisable();
    }

    private void reset() {
        if (!circles.isEmpty()) circles.clear();
    }

    private final static List<JumpRenderer> circles = new ArrayList<>();

    private static Vector3d getVec3dFromEntity(final Entity entityIn) {
        final float PT = mc.getRenderPartialTicks();
        final double dx = entityIn.getPosX() - entityIn.lastTickPosX;
        final double dy = entityIn.getPosY() - entityIn.lastTickPosY;
        final double dz = entityIn.getPosZ() - entityIn.lastTickPosZ;
        return new Vector3d(
                entityIn.lastTickPosX + dx * PT,
                entityIn.lastTickPosY + dy * PT,
                entityIn.lastTickPosZ + dz * PT
        );
    }

    private void setupDraw(MatrixStack stack, final Runnable render) {
        final boolean light = GL11.glIsEnabled(GL11.GL_LIGHTING);
        stack.push();
        RenderSystem.enableBlend();
        RenderSystem.enableAlphaTest();
        RenderSystem.alphaFunc(GL14C.GL_GREATER, 0);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        if (light) RenderSystem.disableLighting();
        RenderSystem.shadeModel(GL11.GL_SMOOTH);
        RenderSystem.blendFunc(GL14C.GL_SRC_ALPHA, GL14C.GL_ONE_MINUS_CONSTANT_ALPHA);
        RectUtils.setupOrientationMatrix(stack, 0, 0, 0);
        render.run();
        RenderSystem.blendFunc(GL14C.GL_SRC_ALPHA, GL14C.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.color3f(1.F, 1.F, 1.F);
        RenderSystem.shadeModel(GL11.GL_FLAT);
        if (light) RenderSystem.enableLighting();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.alphaFunc(GL14C.GL_GREATER, .1F);
        RenderSystem.enableAlphaTest();
        stack.pop();
    }

    private int getColor(int index, float alphaPC) {
        int colorize = ColorUtils.getColor(index); // Используем цвет по умолчанию
        return ColorUtils.multAlpha(colorize, alphaPC);
    }

    private final Tessellator tessellator = Tessellator.getInstance();
    private final BufferBuilder buffer = tessellator.getBuffer();

    private final class JumpRenderer {
        private final long time = System.currentTimeMillis();
        private final Vector3d pos;
        int index;

        private JumpRenderer(Vector3d pos, int index) {
            this.pos = pos;
            this.index = index;
        }

        private int getIndex() {
            return this.index;
        }

        private float getDeltaTime() {
            return (float) (System.currentTimeMillis() - time) / circleSpeed.getValue();
        }
    }

    public static float valWave1(float value) {
        return (value > .5 ? 1 - value : value) * 2.F;
    }

    public static float valWave2(float value) {
        return Math.max(0, Math.min(1, value));
    }

    public static double easeOutCirc(double value) {
        return Math.sqrt(1 - Math.pow(value - 1, 2));
    }

    public static void customRotatedObject2D(MatrixStack stack, float oXpos, float oYpos, float oWidth, float oHeight, double rotate) {
        stack.translate(oXpos + oWidth / 2, oYpos + oHeight / 2, 0);
        stack.rotate(Vector3f.ZP.rotationDegrees((float) rotate));
        stack.translate(-oXpos - oWidth / 2, -oYpos - oHeight / 2, 0);
    }
}