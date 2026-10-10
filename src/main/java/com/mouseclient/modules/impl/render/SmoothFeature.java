package com.mouseclient.modules.impl.render;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.RenderSlotsEvent;
import com.mouseclient.internal.ClientMath;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.mixin.ChatHudAccessor;
import com.mouseclient.mixin.HandledScreenAccessor;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.ModuleRegistry;
import com.mouseclient.modules.impl.misc.EventListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.entity.EntityPosition;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

@ModuleInfo(
   name = "SmoothFeature",
   description = "Плавные мелочи клиента: предметы под курсором, чат, камера при флагах.",
   category = ModuleCategory.RENDER
)
public final class SmoothFeature extends Module {
   private static SmoothFeature INSTANCE;
   public BooleanSetting hoverItemScale;
   public BooleanSetting smoothChat;
   public BooleanSetting chatAntiSpam;
   public BooleanSetting smoothCorrectionCamera;
   private final HashMap<Slot, Float> slotHoverProgress = new HashMap<>();
   private long lastSlotRenderMillis;
   private boolean slotScalePushed;
   private String lastMessage = "";
   private int repeatCount = 1;
   private long newChatMessageAtMillis;
   private boolean chatSlidePushed;
   private Vec3d correctionDelta;
   private long correctionAtMillis;
   private long correctionDurationMillis;
   private final EventListener<RenderSlotsEvent> slotRenderListener = event -> {
      long nowMillis = System.currentTimeMillis();
      float interpolation = 1.0F - (float)Math.exp(-Math.min((100L), nowMillis - this.lastSlotRenderMillis) / (60.0D));
      this.lastSlotRenderMillis = nowMillis;
      Slot slot = ((HandledScreenAccessor)(Object)event.getScreen()).hoveredSlot();
      if (slot != null && this.slotHoverProgress.size() < 256) {
         this.slotHoverProgress.putIfAbsent(slot, 0.0F);
      }

      this.slotHoverProgress.entrySet().removeIf(slotEntry -> slotEntry.getValue() < (0.004F) && slotEntry.getKey() != slot);

      for (Entry entry : this.slotHoverProgress.entrySet()) {
         entry.setValue((Float)entry.getValue() + ((entry.getKey() == slot ? 1.0F : 0.0F) - (Float)entry.getValue()) * interpolation);
      }
   };
   private final EventListener<PacketEvent> packetListener = event -> {
      if (minecraftClient.player != null && this.smoothCorrectionCamera.getValue() && event.isIncoming() && event.getPacket() instanceof PlayerPositionLookS2CPacket correctionPacket) {
         Vec3d position = EntityPosition.apply(EntityPosition.fromEntity(minecraftClient.player), correctionPacket.change(), correctionPacket.relatives()).position();
         double correctionDistance = minecraftClient.player.getEntityPos().distanceTo(position);
         if (!(correctionDistance < (0.05D)) && !(correctionDistance > (24.0D))) {
            this.correctionDelta = minecraftClient.player.getEntityPos().subtract(position);
            this.correctionAtMillis = System.currentTimeMillis();
            this.correctionDurationMillis = (long)ClientMath.clamp((160.0F), (420.0F), (140.0F) + (float)correctionDistance * (40.0F));
         }
      }
   };

   public void beginSlotScale(DrawContext context, Slot slot) {
      this.slotScalePushed = false;
      if (this.isEnabled() && this.hoverItemScale.getValue()) {
         Float hoverProgress = this.slotHoverProgress.get(slot);
         if (hoverProgress != null && !(hoverProgress < (0.004F))) {
            this.slotScalePushed = true;
            float scale = 1.0F + (0.13F) * hoverProgress;
            float centerX = slot.x + (8.0F);
            float centerY = slot.y + (8.0F);
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(centerX, centerY);
            context.getMatrices().scale(scale, scale);
            context.getMatrices().translate(-centerX, -centerY);
         }
      }
   }

   public void markNewChatMessage() {
      this.newChatMessageAtMillis = System.currentTimeMillis();
   }

   private SmoothFeature() {
      this.hoverItemScale = this.addSetting(new BooleanSetting(("Увеличение предмета под курсором"), ("Плавно подращивает предмет при наводке во всех экранах и шалкерах."), true));
      this.smoothChat = this.addSetting(new BooleanSetting(("Плавный чат"), ("Новые сообщения мягко выезжают снизу, а не появляются рывком."), true));
      this.chatAntiSpam = this.addSetting(new BooleanSetting(("Антиспам чата"), ("Повторы одного сообщения не плодятся, а помечаются счётчиком (x2)."), true));
      this.smoothCorrectionCamera = this.addSetting(new BooleanSetting(("Плавная камера при флагах"), ("Откат позиции сервером не дёргает камеру, а плавно доводит её."), false));
      ModuleRegistry.unregister(this);
      this.setSubscribed(true);
   }

   @Override
   protected void onDisable() {
      this.slotHoverProgress.clear();
      this.slotScalePushed = this.chatSlidePushed = false;
      this.lastMessage = "";
      this.repeatCount = 1;
      this.correctionDelta = null;
   }

   public static SmoothFeature getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new SmoothFeature();
      }

      return INSTANCE;
   }

   public void endSlotScale(DrawContext context) {
      if (this.slotScalePushed) {
         this.slotScalePushed = false;
         context.getMatrices().popMatrix();
      }
   }

   public void endChatSlide(DrawContext context) {
      if (this.chatSlidePushed) {
         this.chatSlidePushed = false;
         context.getMatrices().popMatrix();
      }
   }

   public boolean hasCorrectionOffset() {
      return this.isEnabled()
         && this.smoothCorrectionCamera.getValue()
         && this.correctionDelta != null
         && System.currentTimeMillis() - this.correctionAtMillis < this.correctionDurationMillis
         && !FreeCamera.getInstance().isCameraActive();
   }

   public Vec3d correctionOffset() {
      float elapsedProgress = ClientMath.clampUnit((float)(System.currentTimeMillis() - this.correctionAtMillis) / (float)this.correctionDurationMillis);
      return this.correctionDelta.multiply((1.0F - elapsedProgress) * (1.0F - elapsedProgress) * (1.0F - elapsedProgress));
   }

   public void beginChatSlide(ChatHud chatHud, DrawContext context) {
      this.chatSlidePushed = false;
      if (this.isEnabled() && this.smoothChat.getValue() && ((ChatHudAccessor)(Object)chatHud).chatScrollbarPos() == 0) {
         float elapsedProgress = ClientMath.clampUnit((float)(System.currentTimeMillis() - this.newChatMessageAtMillis) / (240.0F));
         if (!(elapsedProgress >= 1.0F)) {
            float slideProgress = 1.0F - (1.0F - elapsedProgress) * (1.0F - elapsedProgress) * (1.0F - elapsedProgress);
            this.chatSlidePushed = true;
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(0.0F, (9.0F) * minecraftClient.options.getChatScale().getValue().floatValue() * (1.0F - slideProgress));
         }
      }
   }

   public boolean mergeRepeatedMessage(ChatHud chatHud, Text text) {
      if (this.isEnabled() && this.chatAntiSpam.getValue()) {
         String messageString = text.getString();
         java.util.List<net.minecraft.client.gui.hud.ChatHudLine> messages = ((ChatHudAccessor)(Object)chatHud).allMessages();
         if (!messageString.isBlank() && !messages.isEmpty() && messageString.equals(this.lastMessage)) {
            this.repeatCount++;
            ChatHudLine previousMessage = (ChatHudLine)messages.get(0);
            messages.set(
               0,
               new ChatHudLine(
                  previousMessage.creationTick(),
                  text.copy().append(Text.literal(" (x" + this.repeatCount + ")").formatted(Formatting.GRAY)),
                  previousMessage.signature(),
                  previousMessage.indicator()
               )
            );
            ((ChatHudAccessor)(Object)chatHud).invokeRefreshTrimmedMessages();
            this.markNewChatMessage();
            return true;
         } else {
            this.lastMessage = messageString;
            this.repeatCount = 1;
            return false;
         }
      } else {
         return false;
      }
   }
}
