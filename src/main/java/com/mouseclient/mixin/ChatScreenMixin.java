package com.mouseclient.mixin;

import com.mouseclient.gui.HudSettingsPopup;
import com.mouseclient.network.ClientInitializer;
import com.mouseclient.util.time.HudLayout;
import com.mouseclient.util.time.WatermarkHud;
import com.mouseclient.util.time.IrcClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
   @Shadow
   protected TextFieldWidget chatField;

   protected ChatScreenMixin(Text text) {
      super(text);
   }

   @Inject(method = "sendMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
   private void dispatchClientCommand(String input, boolean addToHistory, CallbackInfo callback) {
      if (ClientInitializer.inst().commandDispatcher() != null && ClientInitializer.inst().commandDispatcher().dispatch(input)) {
         this.client.inGameHud.getChatHud().addToMessageHistory(input);
         callback.cancel();
      }
   }

   @Inject(method = "sendMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
   private void sendIrcMessage(String chatText, boolean addToHistory, CallbackInfo callback) {
      if (IrcClient.getInstance().isEnabled() && chatText.startsWith(("@"))) {
         IrcClient.getInstance().sendMessage(chatText.substring(1).trim());
         this.client.inGameHud.getChatHud().addToMessageHistory(chatText);
         callback.cancel();
      }
   }

   @Inject(method = "mouseClicked(Lnet/minecraft/client/gui/Click;Z)Z", at = @At("HEAD"), cancellable = true)
   private void dragClick(Click click, boolean doubled, CallbackInfoReturnable<Boolean> callback) {
      if (HudSettingsPopup.mouseClicked(click.x(), click.y(), click.button())) {
         callback.setReturnValue(true);
      } else if (WatermarkHud.mouseClicked(click.x(), click.y(), click.button())) {
         callback.setReturnValue(true);
      } else {
         if (HudLayout.mouseClicked(click.x(), click.y(), click.button())) {
            callback.setReturnValue(true);
         }
      }
   }

   @Inject(method = "removed()V", at = @At("HEAD"))
   private void dragRemoved(CallbackInfo callback) {
      WatermarkHud.finishDrag();
      HudLayout.finishEditing();
   }

   @Override
   public boolean mouseReleased(Click click) {
      HudSettingsPopup.stopDragging();
      WatermarkHud.finishDrag();
      HudLayout.mouseReleased(click.button());
      return super.mouseReleased(click);
   }

   static {
      VMBridge.identifyClass(ChatScreenMixin.class, "CVis2JDr");
   }
}
