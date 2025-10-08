package io.hynix.units.impl.display;

import io.hynix.events.impl.EventRender3D;
import io.hynix.units.api.Category;
import io.hynix.units.api.Unit;
import io.hynix.units.api.UnitRegister;
import io.hynix.units.settings.impl.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import com.google.common.eventbus.Subscribe;
import org.lwjgl.opengl.GL11;
import java.util.*;

/**
 * @author L1r9ije
 */
@UnitRegister(name = "XRay", category = Category.Display, desc = "Отображает выбранные вами руды сквозь стены с ультра-визуалами")
public class XRay extends Unit {

    private final ModeSetting mode = new ModeSetting("Режим", "Обычный", "Обычный", "Древние обломки", "Премиум");
    private final ColorSetting color = new ColorSetting("Цвет", 0xFFFF00FF).setVisible(() -> mode.is("Древние обломки"));
    private final SliderSetting thickness = new SliderSetting("Толщина контура", 2.0f, 1.0f, 5.0f, 0.1f);
    private final BooleanSetting boxFill = new BooleanSetting("3D Заливка", false); // Отключено по умолчанию для FPS
    private final SliderSetting boxFillAlpha = new SliderSetting("Прозрачность", 0.2f, 0.1f, 1.0f, 0.05f).setVisible(() -> boxFill.getValue());
    private final BooleanSetting glowEffect = new BooleanSetting("Неоновое свечение", true);
    private final SliderSetting glowStrength = new SliderSetting("Сила свечения", 1.2f, 0.1f, 3.0f, 0.1f).setVisible(() -> glowEffect.getValue());
    private final BooleanSetting pulseEffect = new BooleanSetting("3D Пульсация", false); // Отключено для FPS
    private final SliderSetting pulseSpeed = new SliderSetting("Скорость пульсации", 1.8f, 0.5f, 5.0f, 0.1f).setVisible(() -> pulseEffect.getValue());
    private final BooleanSetting rainbowEffect = new BooleanSetting("Радужный эффект", false); // Отключено для FPS
    private final SliderSetting rainbowSpeed = new SliderSetting("Скорость радуги", 2.5f, 0.5f, 5.0f, 0.1f).setVisible(() -> rainbowEffect.getValue());
    private final BooleanSetting outlineGradient = new BooleanSetting("Градиент контура", false); // Отключено для FPS
    private final BooleanSetting cornerHighlights = new BooleanSetting("Подсветка углов", false); // Отключено для FPS
    private final BooleanSetting scanLines = new BooleanSetting("Сканирующие лучи", false); // Отключено для FPS
    private final BooleanSetting depthEffect = new BooleanSetting("Эффект глубины", true);
    private final SliderSetting depthFade = new SliderSetting("Затухание глубины", 0.7f, 0.1f, 1.0f, 0.1f).setVisible(() -> depthEffect.getValue());

    // Настройки руд
    private final BooleanSetting diamondOre = new BooleanSetting("Алмазная руда", true);
    private final BooleanSetting ironOre = new BooleanSetting("Железная руда", true);
    private final BooleanSetting goldOre = new BooleanSetting("Золотая руда", false);
    private final BooleanSetting ancientDebris = new BooleanSetting("Древний обломок", true);
    private final BooleanSetting lapisOre = new BooleanSetting("Лазуритовая руда", false);
    private final BooleanSetting redstoneOre = new BooleanSetting("Редстоуновая руда", false);
    private final BooleanSetting coalOre = new BooleanSetting("Угольная руда", false);
    private final BooleanSetting emeraldOre = new BooleanSetting("Изумрудная руда", true);
    private final BooleanSetting netherQuartz = new BooleanSetting("Незер-кварц", false);

    // Оптимизация
    private final SliderSetting renderDistance = new SliderSetting("Дистанция рендера", 32, 16, 64, 4);
    private final BooleanSetting chunkCaching = new BooleanSetting("Кеширование чанков", true);
    private final SliderSetting maxBlocksPerFrame = new SliderSetting("Макс блоков/кадр", 200, 50, 500, 10);
    private final BooleanSetting simpleRender = new BooleanSetting("Простой рендер", true);

    // Кеш
    private final Map<BlockPos, Integer> blockCache = new HashMap<>();
    private long lastCacheClear = System.currentTimeMillis();
    private int renderedBlocksThisFrame = 0;

    public XRay() {
        addSettings(
                mode, color, thickness, boxFill, boxFillAlpha, glowEffect, glowStrength,
                pulseEffect, pulseSpeed, rainbowEffect, rainbowSpeed, outlineGradient,
                cornerHighlights, scanLines, depthEffect, depthFade,
                diamondOre, ironOre, goldOre, ancientDebris, lapisOre, redstoneOre,
                coalOre, emeraldOre, netherQuartz,
                renderDistance, chunkCaching, maxBlocksPerFrame, simpleRender
        );
    }

    @Subscribe
    public void onRender3D(EventRender3D event) {
        if (mc.player == null || mc.world == null) return;

        renderedBlocksThisFrame = 0;

        // Очищаем кеш каждые 5 секунд
        if (System.currentTimeMillis() - lastCacheClear > 5000) {
            blockCache.clear();
            lastCacheClear = System.currentTimeMillis();
        }

        switch (mode.getValue()) {
            case "Обычный":
                renderOresOptimized();
                break;
            case "Древние обломки":
                renderAncientDebrisOptimized();
                break;
            case "Премиум":
                renderPremiumOresOptimized();
                break;
        }
    }

    private void renderOresOptimized() {
        BlockPos playerPos = new BlockPos(mc.player.getPosX(), mc.player.getPosY(), mc.player.getPosZ());
        int range = renderDistance.getValue().intValue();

        // Рендерим только каждый второй блок для экономии FPS
        for (int x = playerPos.getX() - range; x <= playerPos.getX() + range; x += 2) {
            for (int y = Math.max(0, playerPos.getY() - range); y <= Math.min(255, playerPos.getY() + range); y += 2) {
                for (int z = playerPos.getZ() - range; z <= playerPos.getZ() + range; z += 2) {
                    if (renderedBlocksThisFrame >= maxBlocksPerFrame.getValue()) return;

                    BlockPos pos = new BlockPos(x, y, z);
                    if (chunkCaching.getValue() && blockCache.containsKey(pos)) {
                        int cachedColor = blockCache.get(pos);
                        if (cachedColor != -1) {
                            renderOptimizedBlockBox(new AxisAlignedBB(pos), cachedColor, pos);
                            renderedBlocksThisFrame++;
                        }
                        continue;
                    }

                    BlockState state = mc.world.getBlockState(pos);
                    if (isOreBlock(state.getBlock())) {
                        int baseColor = getOreColor(state.getBlock());
                        if (baseColor != -1) {
                            int finalColor = applyEffectsOptimized(baseColor, pos);
                            if (chunkCaching.getValue()) {
                                blockCache.put(pos, finalColor);
                            }
                            renderOptimizedBlockBox(new AxisAlignedBB(pos), finalColor, pos);
                            renderedBlocksThisFrame++;
                        } else if (chunkCaching.getValue()) {
                            blockCache.put(pos, -1);
                        }
                    } else if (chunkCaching.getValue()) {
                        blockCache.put(pos, -1);
                    }
                }
            }
        }
    }

    private void renderPremiumOresOptimized() {
        BlockPos playerPos = new BlockPos(mc.player.getPosX(), mc.player.getPosY(), mc.player.getPosZ());
        int range = renderDistance.getValue().intValue();

        for (int x = playerPos.getX() - range; x <= playerPos.getX() + range; x++) {
            for (int y = Math.max(0, playerPos.getY() - range); y <= Math.min(255, playerPos.getY() + range); y++) {
                for (int z = playerPos.getZ() - range; z <= playerPos.getZ() + range; z++) {
                    if (renderedBlocksThisFrame >= maxBlocksPerFrame.getValue()) return;

                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);

                    if (isOreBlock(state.getBlock())) {
                        int baseColor = getOreColor(state.getBlock());
                        if (baseColor != -1) {
                            int finalColor = applyPremiumEffects(baseColor, pos);
                            renderOptimizedBlockBox(new AxisAlignedBB(pos), finalColor, pos);
                            renderedBlocksThisFrame++;
                        }
                    }
                }
            }
        }
    }

    private void renderAncientDebrisOptimized() {
        BlockPos playerPos = new BlockPos(mc.player.getPosX(), mc.player.getPosY(), mc.player.getPosZ());
        int range = renderDistance.getValue().intValue();

        for (int x = playerPos.getX() - range; x <= playerPos.getX() + range; x += 2) {
            for (int y = Math.max(0, playerPos.getY() - range); y <= Math.min(255, playerPos.getY() + range); y += 2) {
                for (int z = playerPos.getZ() - range; z <= playerPos.getZ() + range; z += 2) {
                    if (renderedBlocksThisFrame >= maxBlocksPerFrame.getValue()) return;

                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);

                    if (state.getBlock() == Blocks.ANCIENT_DEBRIS) {
                        int baseColor = color.getValue();
                        int finalColor = applyEffectsOptimized(baseColor, pos);
                        renderOptimizedBlockBox(new AxisAlignedBB(pos), finalColor, pos);
                        renderedBlocksThisFrame++;
                    }
                }
            }
        }
    }

    private void renderOptimizedBlockBox(AxisAlignedBB bb, int color, BlockPos pos) {
        if (simpleRender.getValue()) {
            renderSimpleBox(bb, color);
        } else {
            renderUltraBlockBox(bb, color, pos);
        }
    }

    private void renderSimpleBox(AxisAlignedBB bb, int color) {
        GL11.glPushMatrix();
        setupGL();

        double x = mc.getRenderManager().info.getProjectedView().x;
        double y = mc.getRenderManager().info.getProjectedView().y;
        double z = mc.getRenderManager().info.getProjectedView().z;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        // Простой контур без эффектов
        GL11.glLineWidth(thickness.getValue());
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        drawSimpleOutline(buffer, bb, color, x, y, z);
        tessellator.draw();

        cleanupGL();
        GL11.glPopMatrix();
    }

    private void drawSimpleOutline(BufferBuilder buffer, AxisAlignedBB bb, int color, double x, double y, double z) {
        double minX = bb.minX - x;
        double minY = bb.minY - y;
        double minZ = bb.minZ - z;
        double maxX = bb.maxX - x;
        double maxY = bb.maxY - y;
        double maxZ = bb.maxZ - z;

        float r = (float)(color >> 16 & 255) / 255.0F;
        float g = (float)(color >> 8 & 255) / 255.0F;
        float b = (float)(color & 255) / 255.0F;
        float a = (float)(color >> 24 & 255) / 255.0F;

        // Только основные линии
        addLine(buffer, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        addLine(buffer, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        addLine(buffer, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        addLine(buffer, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        addLine(buffer, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        addLine(buffer, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        addLine(buffer, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        addLine(buffer, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        addLine(buffer, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        addLine(buffer, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        addLine(buffer, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        addLine(buffer, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private int applyEffectsOptimized(int baseColor, BlockPos pos) {
        int color = baseColor;

        // Только базовые эффекты для оптимизации
        if (depthEffect.getValue()) {
            double dist = Math.sqrt(mc.player.getPosition().distanceSq(pos));
            float depthFactor = (float) Math.max(0.3, 1.0 - dist * depthFade.getValue() / renderDistance.getValue());
            color = applyAlpha(color, (int) ((color >> 24 & 0xFF) * depthFactor));
        }

        if (glowEffect.getValue()) {
            color = applyNeonGlow(color, glowStrength.getValue());
        }

        return color;
    }

    // Остальные методы остаются без изменений, но используются реже
    private boolean isOreBlock(Block block) {
        if (block == Blocks.DIAMOND_ORE && diamondOre.getValue()) return true;
        if (block == Blocks.IRON_ORE && ironOre.getValue()) return true;
        if (block == Blocks.GOLD_ORE && goldOre.getValue()) return true;
        if (block == Blocks.ANCIENT_DEBRIS && ancientDebris.getValue()) return true;
        if (block == Blocks.LAPIS_ORE && lapisOre.getValue()) return true;
        if (block == Blocks.REDSTONE_ORE && redstoneOre.getValue()) return true;
        if (block == Blocks.COAL_ORE && coalOre.getValue()) return true;
        if (block == Blocks.EMERALD_ORE && emeraldOre.getValue()) return true;
        if (block == Blocks.NETHER_QUARTZ_ORE && netherQuartz.getValue()) return true;
        return false;
    }

    private int getOreColor(Block block) {
        if (block == Blocks.DIAMOND_ORE) return 0xFF00FFFF;
        if (block == Blocks.IRON_ORE) return 0xFF00FF00;
        if (block == Blocks.GOLD_ORE) return 0xFFFFFF00;
        if (block == Blocks.ANCIENT_DEBRIS) return 0xFFFF00FF;
        if (block == Blocks.LAPIS_ORE) return 0xFF0080FF;
        if (block == Blocks.REDSTONE_ORE) return 0xFFFF4000;
        if (block == Blocks.COAL_ORE) return 0xFF4040FF;
        if (block == Blocks.EMERALD_ORE) return 0xFF00FF80;
        if (block == Blocks.NETHER_QUARTZ_ORE) return 0xFFFFC0FF;
        return -1;
    }

    private int applyPremiumEffects(int baseColor, BlockPos pos) {
        return applyEffectsOptimized(baseColor, pos); // Упрощено для оптимизации
    }

    private int applyNeonGlow(int color, float strength) {
        int r = MathHelper.clamp((int) ((color >> 16 & 0xFF) * strength), 0, 255);
        int g = MathHelper.clamp((int) ((color >> 8 & 0xFF) * strength), 0, 255);
        int b = MathHelper.clamp((int) ((color & 0xFF) * strength), 0, 255);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private int applyAlpha(int color, int alpha) {
        return (MathHelper.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private void setupGL() {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1.0f);
    }

    private void cleanupGL() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glPopAttrib();
    }

    private void addLine(BufferBuilder buffer, double x1, double y1, double z1, double x2, double y2, double z2,
                         float r, float g, float b, float a) {
        buffer.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.pos(x2, y2, z2).color(r, g, b, a).endVertex();
    }

    // Старые методы для премиум режима (используются редко)
    private void renderUltraBlockBox(AxisAlignedBB bb, int color, BlockPos pos) {
        GL11.glPushMatrix();
        setupGL();

        double x = mc.getRenderManager().info.getProjectedView().x;
        double y = mc.getRenderManager().info.getProjectedView().y;
        double z = mc.getRenderManager().info.getProjectedView().z;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        GL11.glLineWidth(thickness.getValue());
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        drawSimpleOutline(buffer, bb, color, x, y, z);
        tessellator.draw();

        cleanupGL();
        GL11.glPopMatrix();
    }

    @Override
    public void onDisable() {
        blockCache.clear();
        super.onDisable();
    }
}