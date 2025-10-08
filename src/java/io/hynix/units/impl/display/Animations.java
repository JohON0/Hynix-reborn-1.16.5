package io.hynix.units.impl.display;

import io.hynix.HynixMain;
import io.hynix.ui.notifications.impl.WarningNotify;
import io.hynix.units.api.Category;
import io.hynix.units.api.Unit;
import io.hynix.units.api.UnitRegister;
import io.hynix.units.settings.impl.BooleanSetting;
import io.hynix.units.settings.impl.SliderSetting;
import net.minecraft.util.text.TextFormatting;

@UnitRegister(name = "Animations", category = Category.Display, desc = "Добавляет всякие анимации в игру", premium = true)
public class Animations extends Unit {
    public final BooleanSetting animchunks = new BooleanSetting("Анимировать чанки", true);
    public final BooleanSetting animationcontainer = new BooleanSetting("Анимировать Контейнеры", true);
    public final SliderSetting speedupdatechunk = new SliderSetting("Скорость обновления", 5.0f, 1f, 10.0f, 1f).setVisible(() -> animchunks.getValue());

    public Animations() {
        addSettings(animchunks, animationcontainer, speedupdatechunk);
    }
}