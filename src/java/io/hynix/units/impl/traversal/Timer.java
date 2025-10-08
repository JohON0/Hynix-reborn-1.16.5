package io.hynix.units.impl.traversal;

import com.google.common.eventbus.Subscribe;
import io.hynix.events.impl.EventPacket;
import io.hynix.events.impl.EventUpdate;
import io.hynix.units.api.Category;
import io.hynix.units.api.Unit;
import io.hynix.units.api.UnitRegister;
import io.hynix.units.settings.impl.BooleanSetting;
import io.hynix.units.settings.impl.SliderSetting;
import net.minecraft.network.play.client.CConfirmTransactionPacket;
import net.minecraft.network.play.server.SEntityVelocityPacket;
import net.minecraft.util.math.MathHelper;

@UnitRegister(name = "Timer", category = Category.Traversal, desc = "Ускоряет игру")
public class Timer extends Unit {

    public final SliderSetting speed = new SliderSetting("Скорость", 2f, 0.1f, 10f, 0.1f);
    public final BooleanSetting smart = new BooleanSetting("Умный", true);
    public final SliderSetting ticks = new SliderSetting("Тики", 1.0f, 0.15f, 3.0f, 0.1f);
    public final BooleanSetting moveUp = new BooleanSetting("Восстанавливать", false).setVisible(() -> smart.getValue());
    public final SliderSetting moveUpValue = new SliderSetting("Значение", 0.05f, 0.01f, 0.1f, 0.01f).setVisible(() -> moveUp.getValue() && smart.getValue());

    public double value;
    public float maxViolation = 100.0F;
    public float violation = 0.0F;
    private int safeTicks = 0;

    public Timer() {
        addSettings(speed, ticks, smart, moveUp, moveUpValue);
    }

    @Subscribe
    public void onPacket(EventPacket e) {
        // Отменяем только некоторые пакеты для меньшей заметности
        if (e.getPacket() instanceof CConfirmTransactionPacket) {
            // Не отменяем все пакеты, только каждый 3-й для меньшей заметности
            if (safeTicks % 3 == 0) {
                e.cancel();
            }
        }

        // Сбрасываем при получении отдачи (кибек)
        if (e.getPacket() instanceof SEntityVelocityPacket p) {
            if (p.getEntityID() == mc.player.getEntityId()) {
                safeReset();
            }
        }
    }

    @Subscribe
    private void onUpdate(EventUpdate e) {
        safeTicks++;

        // Авто-сброс каждые 20 секунд для безопасности
        if (safeTicks > 400) {
            safeReset();
            safeTicks = 0;
        }

        if (!mc.player.isOnGround()) {
            this.violation += 0.05f; // Меньше увеличения в воздухе
            this.violation = MathHelper.clamp(this.violation, 0.0F, this.maxViolation / (this.speed.getValue()));
        }

        // Умный режим с улучшенной логикой
        if (this.smart.getValue()) {
            handleSmartMode();
        } else {
            // В обычном режиме используем безопасные значения
            mc.timer.timerSpeed = Math.min(this.speed.getValue(), 2.5f);
        }
    }

    private void handleSmartMode() {
        if (mc.timer.timerSpeed <= 1.0F) {
            return;
        }

        // Более плавное увеличение violation
        if (this.violation < (this.maxViolation) / (this.speed.getValue())) {
            this.violation += this.ticks.getValue() * 0.5f; // Медленнее увеличиваем
            this.violation = MathHelper.clamp(this.violation, 0.0F, this.maxViolation / (this.speed.getValue()));

            // Плавное изменение скорости
            float targetSpeed = this.speed.getValue();
            if (targetSpeed > 2.0f) targetSpeed = 2.0f; // Ограничиваем максимум

            mc.timer.timerSpeed = 1.0f + (targetSpeed - 1.0f) * (this.violation / this.maxViolation);
        } else {
            // Мягкий сброс вместо полного отключения
            softReset();
        }

        // Восстановление при включенной опции
        if (this.moveUp.getValue() && this.violation > 0) {
            this.violation -= this.moveUpValue.getValue();
            if (this.violation < 0) this.violation = 0;
        }
    }

    private void safeReset() {
        // Мягкий сброс скорости
        mc.timer.timerSpeed = 1.0f;
        this.violation = this.maxViolation * 0.2f; // Оставляем немного violation
    }

    private void softReset() {
        // Мягкий сброс без отключения модуля
        mc.timer.timerSpeed = 1.0f;
        this.violation = this.maxViolation * 0.3f; // Начинаем с 30%

        // Постепенно возвращаем скорость
        new Thread(() -> {
            try {
                Thread.sleep(1000); // Ждем 1 секунду
                if (this.isEnabled()) {
                    this.violation = this.maxViolation * 0.4f;
                }
            } catch (InterruptedException ignored) {}
        }).start();
    }

    private void reset() {
        mc.timer.timerSpeed = 1.0f;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        safeTicks = 0;
        this.violation = this.maxViolation * 0.1f; // Начинаем с 10%
        reset();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        reset();
    }
}