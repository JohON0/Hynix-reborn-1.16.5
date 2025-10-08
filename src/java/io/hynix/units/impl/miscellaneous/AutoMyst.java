package io.hynix.units.impl.miscellaneous;

import com.google.common.eventbus.Subscribe;
import io.hynix.HynixMain;
import io.hynix.events.impl.EventUpdate;
import io.hynix.ui.notifications.impl.WarningNotify;
import io.hynix.units.api.Category;
import io.hynix.units.api.Unit;
import io.hynix.units.api.UnitRegister;
import io.hynix.units.settings.impl.BooleanSetting;
import io.hynix.units.settings.impl.ModeSetting;
import io.hynix.units.settings.impl.SliderSetting;
import io.hynix.utils.client.ClientUtils;
import io.hynix.utils.johon0.math.TimerUtils;
import net.minecraft.inventory.container.ChestContainer;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.Item;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@UnitRegister(name = "AutoMyst", category = Category.Miscellaneous, desc = "Auto Myst", premium = true)
public class AutoMyst extends Unit {
    private final BooleanSetting autodelay = new BooleanSetting("Подбирать задержку", false);
    private final SliderSetting stealDelay = new SliderSetting("Задержка", 280, 0, 1000, 1).setVisible(() -> !autodelay.getValue());
    private final ModeSetting servers = new ModeSetting("Сервера", "FunTime", "FunTime", "StormHvH", "HolyWorld").setVisible(() -> autodelay.getValue());
    final BooleanSetting close = new BooleanSetting("Закрывать если пустой", true);
    final BooleanSetting leave = new BooleanSetting("Ливать в лобби", true);
    private final TimerUtils timerUtil = new TimerUtils(), bypassTimer = new TimerUtils();

    public AutoMyst() {
        addSettings(autodelay,servers,stealDelay,close,leave);
    }

    @Subscribe
    public void onUpdate(EventUpdate e) {
        if (mc.player.openContainer instanceof ChestContainer) {
            ChestContainer container = (ChestContainer) mc.player.openContainer;
            List<Slot> slots = container.inventorySlots.stream().filter(i -> !i.getStack().isEmpty()).toList();

            if (container.getLowerChestInventory().isEmpty()) {
                if (close.getValue())
                    mc.player.closeScreen();
                if (leave.getValue() && !ClientUtils.isPvP()) {
                    mc.player.sendChatMessage("/hub");
                    HynixMain.getInstance().getNotifyManager().add(new WarningNotify(getName() + " был выключен, так как вы были телепортированы в хаб", 2000));

                    toggle();
                }
            }

            for (int index = 0; index < container.inventorySlots.size(); ++index) {
                long delay = stealDelay.getValue().longValue();
                if (autodelay.getValue()) {
                    if (servers.is("FunTime")) {
                        delay = 280;
                    }
                    if (servers.is("StormHvH")) {
                        delay = 120;
                    }
                    if (servers.is("HolyWorld")) {
                        delay = 280;
                    }
                } else {
                    delay = stealDelay.getValue().longValue();
                }
                Slot s = slots.get(slots.isEmpty() ? 0 : ThreadLocalRandom.current().nextInt(0, slots.size()));
                if (mc.player.getCooldownTracker().hasCooldown(s.getStack().getItem())) return;
                if (container.getLowerChestInventory().getStackInSlot(s.slotNumber).getItem() != Item.getItemById(0)
                        && timerUtil.hasTimeElapsed(delay) && bypassTimer.hasTimeElapsed(ThreadLocalRandom.current().nextLong(0, 10), true)) {
                    mc.playerController.windowClick(container.windowId, s.slotNumber, 0, ClickType.QUICK_MOVE, mc.player);
                    timerUtil.reset();
                }
//                } else {
//                    for (int i = 0; i < myst_slot.size() - 1; i++) {
//                        if (container.getLowerChestInventory().getStackInSlot(i).getItem() != Item.getItemById(0)
//                                && timerUtil.hasTimeElapsed(stealDelay.getValue().longValue()) && bypassTimer.hasTimeElapsed(ThreadLocalRandom.current().nextLong(0, 10), true)) {
//                            mc.playerController.windowClick(container.windowId, i, 0, ClickType.QUICK_MOVE, mc.player);
//                            timerUtil.reset();
//                        }
//                    }
//                    for (Slot s : slots) {
//                        if (container.getLowerChestInventory().getStackInSlot(s.slotNumber).getItem() != Item.getItemById(0)
//                                && timerUtil.hasTimeElapsed(stealDelay.getValue().longValue()) && bypassTimer.hasTimeElapsed(ThreadLocalRandom.current().nextLong(0, 10), true)) {
//                            mc.playerController.windowClick(container.windowId, s.slotNumber, 0, ClickType.QUICK_MOVE, mc.player);
//                            timerUtil.reset();
//                            continue;
//                        }
//                    }
//                }
            }
        }
    }


    @Override
    public void onDisable() {
        super.onDisable();
    }
}
