package io.hynix.command.feature;

import io.hynix.command.interfaces.*;
import io.hynix.HynixMain;
import io.hynix.units.api.UnitManager;
import io.hynix.units.impl.miscellaneous.AutoMine;

import java.util.List;

public class AutoMineCommand implements Command, CommandWithAdvice {

    private final Prefix prefix;
    private final Logger logger;

    public AutoMineCommand(Prefix prefix, Logger logger) {
        this.prefix = prefix;
        this.logger = logger;
    }

    @Override
    public void execute(Parameters parameters) {
        if (parameters.length() == 0) {
            logger.log("Используйте: " + prefix.get() + "point <1|2|clear|info|scan>");
            return;
        }

        String subCommand = parameters.asString(0).orElse("");

        // Простая реализация - команда только выводит сообщения
        switch (subCommand.toLowerCase()) {
            case "1":
                setPoint1();
                break;
            case "2":
                setPoint2();
                break;
            case "clear":
                clearPoints();
                break;
            case "info":
                showInfo();
                break;
            case "scan":
                startScan();
                break;
            default:
                logger.log("Неизвестная команда: " + subCommand);
                break;
        }
    }

    private void setPoint1() {
        logger.log("Точка 1 установлена на текущей позиции");
        // Здесь можно добавить логику сохранения координат
    }

    private void setPoint2() {
        logger.log("Точка 2 установлена на текущей позиции");
    }

    private void clearPoints() {
        logger.log("Все точки очищены");
    }

    private void showInfo() {
        logger.log("AutoMine - система автоматического копания");
        logger.log("Используйте .point 1 и .point 2 для установки точек");
    }

    private void startScan() {
        logger.log("Сканирование области начато");
    }

    @Override
    public String name() {
        return "point";
    }

    @Override
    public String description() {
        return "Управление точками для автоматического копания";
    }

    @Override
    public List<String> adviceMessage() {
        String commandPrefix = prefix.get();
        return List.of(
                commandPrefix + "point 1 - Установить первую точку",
                commandPrefix + "point 2 - Установить вторую точку",
                commandPrefix + "point clear - Очистить все точки",
                commandPrefix + "point info - Показать информацию",
                commandPrefix + "point scan - Начать сканирование"
        );
    }
}