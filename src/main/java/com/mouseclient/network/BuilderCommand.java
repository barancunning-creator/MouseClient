package com.mouseclient.network;

import com.mouseclient.gui.ChatMessages;
import com.mouseclient.modules.impl.combat.AttackAura;
import com.mouseclient.util.entity.RotationBuilder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import net.minecraft.util.Util;

public class BuilderCommand implements ClientCommand {
   private static final String USAGE = "Использование: &7.builder start <имя>&r, &7.name <имя>&r, &7.stop&r, &7.save [имя]&r, &7.load <имя>&r, &7.list&r, &7.delete <имя>&r, &7.status&r, &7.dir";

   @Override
   public String name() {
      return "builder";
   }

   @Override
   public String description() {
      return "Записывает твой бой и собирает из него ротацию (.builder start/save/load)";
   }

   @Override
   public ArrayList<String> suggestions(String[] arguments) {
      if (arguments.length <= 1) {
         return new ArrayList<>(Arrays.asList("start", "stop", "save", "load", "list", "delete", "status", "name", "dir"));
      }

      if (arguments.length == 2 && (arguments[0].equalsIgnoreCase("load") || arguments[0].equalsIgnoreCase("delete") || arguments[0].equalsIgnoreCase("save") || arguments[0].equalsIgnoreCase("start") || arguments[0].equalsIgnoreCase("name"))) {
         return RotationBuilder.getInstance().names();
      }

      return new ArrayList<>();
   }

   @Override
   public void execute(String[] arguments) {
      RotationBuilder builder = RotationBuilder.getInstance();
      builder.bind();
      if (arguments.length == 0 || arguments[0].equalsIgnoreCase("help")) {
         ChatMessages.printPrefixed(USAGE);
         return;
      }

      switch (arguments[0].toLowerCase()) {
         case "start" -> this.start(builder, arguments);
         case "name" -> this.rename(builder, arguments);
         case "stop" -> {
            builder.stop();
            ChatMessages.printPrefixed("Builder: запись остановлена. Семплов &7" + builder.sampleCount() + "&r, ударов &7" + builder.attackCount() + "&r. &7.builder save&r обучит ротацию.");
         }
         case "save", "train" -> this.save(builder, arguments);
         case "load", "use" -> this.load(builder, arguments);
         case "list" -> ChatMessages.printPrefixed(builder.names().isEmpty() ? "Сохранённых ротаций пока нет." : "Ротации: &7" + String.join(", ", builder.names()));
         case "delete", "del" -> this.delete(builder, arguments);
         case "status" -> this.status(builder);
         case "dir" -> this.openDirectory();
         default -> ChatMessages.printPrefixed(USAGE);
      }
   }

   private void start(RotationBuilder builder, String[] arguments) {
      if (arguments.length < 2) {
         ChatMessages.printPrefixed("Использование: &7.builder start <имя>");
         return;
      }

      String name = String.join(" ", Arrays.copyOfRange(arguments, 1, arguments.length));
      if (!builder.start(name)) {
         ChatMessages.printPrefixed("Имя ротации пустое или состоит из недопустимых символов.");
         return;
      }

      String extra = builder.profileExists(builder.sessionName()) ? " Профиль с таким именем уже есть, save его перезапишет." : "";
      String aura = AttackAura.getInstance() != null && AttackAura.getInstance().isEnabled() ? " Аура сейчас включена, для чистой записи лучше выключить её и бить самому." : "";
      ChatMessages.printPrefixed("Builder: запись &7" + builder.sessionName() + "&r начата. Бей цель вручную, клиент складывает скорость, точки и удары." + extra + aura);
   }

   private void rename(RotationBuilder builder, String[] arguments) {
      if (arguments.length < 2) {
         ChatMessages.printPrefixed("Использование: &7.builder name <имя>");
         return;
      }

      builder.rename(String.join(" ", Arrays.copyOfRange(arguments, 1, arguments.length)));
      ChatMessages.printPrefixed("Builder: текущая запись называется &7" + builder.sessionName());
   }

   private void save(RotationBuilder builder, String[] arguments) {
      if (arguments.length > 1) {
         builder.rename(String.join(" ", Arrays.copyOfRange(arguments, 1, arguments.length)));
      }

      int samples = builder.sampleCount();
      int attacks = builder.attackCount();
      RotationBuilder.LearnedRotation profile = builder.save();
      if (profile == null) {
         ChatMessages.printPrefixed("Builder: мало данных (&7" + samples + "&r семплов, &7" + attacks + "&r ударов). Нужен короткий бой хотя бы на пару ударов.");
         return;
      }

      String skipped = builder.skippedTicks() > 30 ? " Часть тиков пропущена, потому что в этот момент уже крутила другая ротация." : "";
      ChatMessages.printPrefixed(
         "Builder: &7"
            + profile.name
            + "&r обучена и включена. Семплы &7"
            + profile.ticks
            + "&r, удары &7"
            + profile.attacks
            + "&r, точка &7"
            + Math.round(profile.aimHeight * 100.0F)
            + "%&r, перелёт &7"
            + String.format(java.util.Locale.ROOT, "%.1f", profile.overshoot)
            + "°&r."
            + skipped
      );
   }

   private void load(RotationBuilder builder, String[] arguments) {
      if (arguments.length < 2) {
         ChatMessages.printPrefixed("Использование: &7.builder load <имя>");
         return;
      }

      String name = String.join(" ", Arrays.copyOfRange(arguments, 1, arguments.length));
      RotationBuilder.LearnedRotation profile = builder.load(name);
      ChatMessages.printPrefixed(profile == null ? "Ротация &7" + name + "&r не найдена." : "Builder: &7" + profile.name + "&r загружена. В ауре выбран этот тип наведения.");
   }

   private void delete(RotationBuilder builder, String[] arguments) {
      if (arguments.length < 2) {
         ChatMessages.printPrefixed("Использование: &7.builder delete <имя>");
         return;
      }

      String name = String.join(" ", Arrays.copyOfRange(arguments, 1, arguments.length));
      ChatMessages.printPrefixed(builder.delete(name) ? "Ротация &7" + RotationBuilder.sanitize(name) + "&r удалена." : "Ротация &7" + name + "&r не найдена.");
   }

   private void status(RotationBuilder builder) {
      String mode = builder.isRecording() ? "идёт запись &7" + builder.sessionName() : "запись выключена";
      String active = builder.active() == null ? "нет загруженной ротации" : "активна &7" + builder.active().name;
      ChatMessages.printPrefixed("Builder: " + mode + "&r, семплов &7" + builder.sampleCount() + "&r, ударов &7" + builder.attackCount() + "&r, " + active + "&r.");
   }

   private void openDirectory() {
      try {
         Files.createDirectories(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("mouseclient").resolve("rotations"));
      } catch (Exception error) {
      }

      Util.getOperatingSystem().open(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("mouseclient").resolve("rotations"));
      ChatMessages.printPrefixed("Папка ротаций открыта.");
   }
}
