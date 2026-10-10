package com.mouseclient.modules.impl.misc;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.PacketEvent;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;

@ModuleInfo(name = "AutoRegister", description = "Автоматически вводит пароль при запросе регистрации или входа.", category = ModuleCategory.MISC)
public final class AutoRegister extends Module {
   private static AutoRegister INSTANCE;
   public FilteredTextSetting password;
   public FilteredTextSetting registerCommand;
   public FilteredTextSetting loginCommand;
   private long lastCommandMillis;
   private final EventListener<PacketEvent> packetListener = event -> {
      if (minecraftClient.player != null
         && event.isIncoming()
         && !this.password.getText().isEmpty()
         && System.currentTimeMillis() - this.lastCommandMillis >= (2000L)
         && event.getPacket() instanceof GameMessageS2CPacket messagePacket) {
         String promptText = messagePacket.content().getString().toLowerCase();
         if (promptText.contains(("войд")) || promptText.contains(("логин")) || promptText.contains(("login")) || promptText.contains(("авториз"))) {
            this.lastCommandMillis = System.currentTimeMillis();
            this.sendPasswordCommand(this.loginCommand.getText());
         } else if (promptText.contains(("регист")) || promptText.contains(("register"))) {
            this.lastCommandMillis = System.currentTimeMillis();
            this.sendPasswordCommand(this.registerCommand.getText());
         }
      }
   };

   private AutoRegister() {
      this.password = this.addSetting(new FilteredTextSetting(("Пароль"), ("Твой пароль сервера для регистрации и входа."), "", 64));
      this.registerCommand = new FilteredTextSetting(("Команда регистрации"), ("%pass% заменяется на пароль."), ("reg %pass% %pass%"), 128);
      this.loginCommand = new FilteredTextSetting(("Команда входа"), ("%pass% заменяется на пароль."), ("l %pass%"), 128);
   }

   public static AutoRegister getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AutoRegister();
      }

      return INSTANCE;
   }

   private void sendPasswordCommand(String commandTemplate) {
      minecraftClient.player
         .networkHandler
         .sendChatCommand((commandTemplate.startsWith(("/")) ? commandTemplate.substring(1) : commandTemplate).replace(("%pass%"), this.password.getText()));
   }
}
