package io.hynix.units.impl.combat;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RayTraceResult;
import io.hynix.events.impl.EventUpdate;
import io.hynix.units.api.Category;
import io.hynix.units.api.Unit;
import io.hynix.units.api.UnitRegister;
import io.hynix.units.settings.impl.BooleanSetting;

@UnitRegister(name = "NoEntityTrace", category = Category.Combat)
public class NoEntityTrace extends Unit {

    private final BooleanSetting ignorePlayers = new BooleanSetting("Игнорировать игроков", true);
    private final BooleanSetting onlyWhenSneaking = new BooleanSetting("Только при Shift", false);

    public NoEntityTrace() {
        this.addSettings(ignorePlayers, onlyWhenSneaking);
    }

    @Subscribe
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;
        if (onlyWhenSneaking.getValue() && !mc.player.isSneaking()) return;

        if (mc.objectMouseOver != null && mc.objectMouseOver.getType() == RayTraceResult.Type.ENTITY) {
            Entity hitEntity = mc.objectMouseOver.getEntity();

            if (hitEntity instanceof PlayerEntity && ignorePlayers.getValue()) {
                // Просто сбрасываем рейкаст
                mc.objectMouseOver = null;
            }
        }
    }
}