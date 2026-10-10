package com.mouseclient.mixin;

import com.mouseclient.modules.impl.misc.AutoAccept;
import com.mouseclient.modules.impl.misc.StreamerMod;
import com.mouseclient.modules.impl.misc.YoutubeModeModule;
import com.mouseclient.modules.impl.render.SmoothFeature;
import com.mouseclient.network.AutoMine;
import com.mouseclient.network.AutoVillager;
import com.mouseclient.network.AutoWarden;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
   @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), cancellable = true)
   private void muteBaritoneChat(Text text, CallbackInfo callback) {
      if (AutoMine.getInstance().isEnabled() && AutoMine.getInstance().isBaritoneMessage(text.getString())) {
         callback.cancel();
      }
   }

   @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), cancellable = true)
   private void muteVillagerBaritoneChat(Text text, CallbackInfo callback) {
      if (AutoVillager.shouldHideBaritoneChat() && AutoMine.getInstance().isBaritoneMessage(text.getString())) {
         callback.cancel();
      }
   }

   @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), cancellable = true)
   private void muteWardenBaritoneChat(Text text, CallbackInfo callback) {
      if (AutoWarden.shouldHideBaritoneChat() && AutoMine.getInstance().isBaritoneMessage(text.getString())) {
         callback.cancel();
      }
   }

   @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), cancellable = true)
   private void muteServerGreetingChat(Text text, CallbackInfo callback) {
      if (YoutubeModeModule.getInstance().isEnabled() && YoutubeModeModule.getInstance().shouldHideChatMessage(text.getString())
         || StreamerMod.getInstance().isEnabled() && StreamerMod.getInstance().shouldHideChatMessage(text.getString())) {
         callback.cancel();
      }
   }

   @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
   private void acceptChatInvites(Text text, CallbackInfo callback) {
      AutoAccept.getInstance().handleInviteMessage(text.getString());
   }

   @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"), cancellable = true)
   private void stackDuplicateChatSpam(Text text, MessageSignatureData signatureData, MessageIndicator indicator, CallbackInfo callback) {
      if (SmoothFeature.getInstance().mergeRepeatedMessage((ChatHud)(Object)this, text)) {
         callback.cancel();
      }
   }

   @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("TAIL"))
   private void slideChatOnNewLine(Text text, MessageSignatureData signatureData, MessageIndicator indicator, CallbackInfo callback) {
      SmoothFeature.getInstance().markNewChatMessage();
   }

   @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V", at = @At("HEAD"))
   private void beginChatSlideRender(DrawContext context, TextRenderer textRenderer, int currentTick, int mouseX, int mouseY, boolean interactable, boolean unusedRenderFlag, CallbackInfo callback) {
      SmoothFeature.getInstance().beginChatSlide((ChatHud)(Object)this, context);
   }

   @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V", at = @At("RETURN"))
   private void endChatSlideRender(DrawContext context, TextRenderer textRenderer, int currentTick, int mouseX, int mouseY, boolean interactable, boolean unusedRenderFlag, CallbackInfo callback) {
      SmoothFeature.getInstance().endChatSlide(context);
   }
}
