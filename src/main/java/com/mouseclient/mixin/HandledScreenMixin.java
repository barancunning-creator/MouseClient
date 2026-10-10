package com.mouseclient.mixin;

import com.mouseclient.gui.RenderSlotsEvent;
import com.mouseclient.modules.impl.misc.AuctionHelper;
import com.mouseclient.modules.impl.misc.EventBus;
import com.mouseclient.modules.impl.movement.ScreenWalk;
import com.mouseclient.modules.impl.render.SmoothFeature;
import com.mouseclient.network.AutoBuyModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen {
   @Shadow
   protected Slot focusedSlot;
   @Shadow
   protected int x;
   @Shadow
   protected int y;
   @Shadow
   protected int backgroundWidth;

   protected HandledScreenMixin(Text text) {
      super(text);
   }

   @Inject(method = "drawSlots(Lnet/minecraft/client/gui/DrawContext;II)V", at = @At("HEAD"))
   private void fireRenderSlotsEvent(DrawContext context, int mouseX, int mouseY, CallbackInfo callback) {
      if (EventBus.getInstance().hasListeners(RenderSlotsEvent.class)) {
         EventBus.getInstance().post(new RenderSlotsEvent((HandledScreen<?>)(Object)this, context));
      }
   }

   @Inject(method = "drawSlot(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/screen/slot/Slot;II)V", at = @At("HEAD"))
   private void growHoveredSlotItem(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo callback) {
      SmoothFeature.getInstance().beginSlotScale(context, slot);
   }

   @Inject(method = "drawSlot(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/screen/slot/Slot;II)V", at = @At("RETURN"))
   private void ungrowHoveredSlotItem(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo callback) {
      SmoothFeature.getInstance().endSlotScale(context);
   }

   @Inject(method = "init()V", at = @At("RETURN"))
   private void addDropAllButton(CallbackInfo callback) {
      if ((Object)this instanceof InventoryScreen && ScreenWalk.getInstance().isEnabled()) {
         int buttonWidth = this.textRenderer.getWidth(("Выкинуть все")) + 12;
         this.addDrawableChild(
            ButtonWidget.builder(Text.literal(("Выкинуть все")), button -> ScreenWalk.getInstance().dropInventoryWithClicks())
               .dimensions(this.x + this.backgroundWidth / 2 - buttonWidth / 2, this.y - 24, buttonWidth, 20)
               .build()
         );
      }
   }

   @Inject(method = "init()V", at = @At("RETURN"))
   private void addAuctionWidgets(CallbackInfo callback) {
      if (AutoBuyModule.getInstance().isEnabled() && AuctionHelper.getInstance().isAuctionTitle(this.getTitle().getString())) {
         this.addDrawableChild(
            ButtonWidget.builder(Text.literal(AutoBuyModule.getInstance().isBuyingActive() ? ("Автобай ВКЛ") : ("Автобай ВЫКЛ")), button -> {
                  AutoBuyModule.getInstance().toggleBuying();
                  button.setMessage(Text.literal(AutoBuyModule.getInstance().isBuyingActive() ? ("Автобай ВКЛ") : ("Автобай ВЫКЛ")));
               })
               .dimensions(
                  this.x + this.backgroundWidth / 2 - (this.textRenderer.getWidth(("Автобай ВЫКЛ")) + 12) / 2,
                  this.y - 24,
                  this.textRenderer.getWidth(("Автобай ВЫКЛ")) + 12,
                  20
               )
               .build()
         );
         this.addDrawableChild(ButtonWidget.builder(Text.literal(AutoBuyModule.getInstance().isPriceParserActive() ? ("Автопарсер ВКЛ") : ("Автопарсер ВЫКЛ")), button -> {
            AutoBuyModule.getInstance().togglePriceParser();
            button.setMessage(Text.literal(AutoBuyModule.getInstance().isPriceParserActive() ? ("Автопарсер ВКЛ") : ("Автопарсер ВЫКЛ")));
         }).dimensions(this.x + this.backgroundWidth + 6, this.y, this.textRenderer.getWidth(("Автопарсер ВЫКЛ")) + 12, 20).build());
         this.addDrawableChild(
            new AutoBuyModule.AutoBuyPercentageSlider(
               this.x + this.backgroundWidth + 6, this.y + 24, this.textRenderer.getWidth(("Автопарсер ВЫКЛ")) + 12, ("Покупка"), AutoBuyModule.getInstance().buyPercentage
            )
         );
         this.addDrawableChild(
            new AutoBuyModule.AutoBuyPercentageSlider(
               this.x + this.backgroundWidth + 6, this.y + 48, this.textRenderer.getWidth(("Автопарсер ВЫКЛ")) + 12, ("Продажа"), AutoBuyModule.getInstance().sellPercentage
            )
         );
      }
   }

   static {
      VMBridge.identifyClass(HandledScreenMixin.class, "nSCB0u1a");
   }
}
