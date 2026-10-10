package com.mouseclient.mixin;

import com.mouseclient.gui.Render2D;
import com.mouseclient.modules.impl.combat.ItemHelper;
import com.mouseclient.modules.impl.misc.InfoReplace;
import com.mouseclient.modules.impl.misc.StreamerMod;
import com.mouseclient.modules.impl.misc.YoutubeModeModule;
import com.mouseclient.modules.impl.misc.EventBus;
import com.mouseclient.modules.impl.render.ArmorBar;
import com.mouseclient.modules.impl.render.CameraFeature;
import com.mouseclient.modules.impl.render.Crosshair;
import com.mouseclient.modules.impl.render.HudSettingsModule;
import com.mouseclient.modules.impl.render.NoRender;
import com.mouseclient.modules.impl.render.WardenEsp;
import com.mouseclient.modules.impl.render.HudRenderEvent;
import com.mouseclient.network.ItemUseQueue;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Collection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.option.AttackIndicator;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.Window;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

   @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"))
   private void renderBelowHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo callback) {
      WardenEsp.getInstance().renderLabels(context, tickCounter.getTickProgress(false));
   }

   @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("RETURN"))
   private void renderHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo callback) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.getWindow() != null) {
         Window window = client.getWindow();
         Render2D.begin(context);
         if (EventBus.getInstance().hasListeners(HudRenderEvent.class)) {
            EventBus.getInstance().post(new HudRenderEvent(context, tickCounter.getTickProgress(false), window.getScaledWidth(), window.getScaledHeight()));
         }

         Render2D.end();
      }
   }

   @Inject(method = "renderOverlay(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/util/Identifier;F)V", at = @At("HEAD"), cancellable = true)
   private void removePumpkinOverlay(DrawContext context, Identifier id, float opacity, CallbackInfo callback) {
      if (NoRender.getInstance().isOverlayDisabled(("Тыква на голове")) && id.getPath().contains(("pumpkinblur"))) {
         callback.cancel();
      }
   }

   @Inject(method = "renderCrosshair(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"), cancellable = true)
   private void hideVanillaCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo callback) {
      if (Crosshair.getInstance().isEnabled() || CameraFeature.getInstance().isFreeLookHeld()) {
         callback.cancel();
      }
   }

   @Inject(method = "renderMainHud(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("TAIL"))
   private void renderHotbarOverlays(DrawContext context, RenderTickCounter tickCounter, CallbackInfo callback) {
      if (ArmorBar.getInstance().isEnabled()) {
         ArmorBar.getInstance().renderArmor(context, tickCounter);
      }

      if (ItemHelper.getInstance().isEnabled()) {
         ItemHelper.getInstance().drawHotbarHighlights(context);
      }
   }

   @Inject(method = "renderStatusEffectOverlay(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"), cancellable = true)
   private void hideVanillaEffects(DrawContext context, RenderTickCounter tickCounter, CallbackInfo callback) {
      if (HudSettingsModule.getInstance().isElementVisible(("potions"))) {
         callback.cancel();
      }
   }

   @ModifyExpressionValue(method = "renderCrosshair(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;"))
   private Object hideCrosshairAttackIndicator(Object attackIndicator) {
      return NoRender.getInstance().isOverlayDisabled(("Индикатор атаки")) ? AttackIndicator.OFF : attackIndicator;
   }

   @ModifyExpressionValue(method = "renderHotbar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;"))
   private Object hideHotbarAttackIndicator(Object attackIndicator) {
      return NoRender.getInstance().isOverlayDisabled(("Индикатор атаки")) ? AttackIndicator.OFF : attackIndicator;
   }

   @WrapOperation(method = "renderHotbar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;getStack(I)Lnet/minecraft/item/ItemStack;"))
   private ItemStack hideCarriedFromHotbar(PlayerInventory inventory, int slot, Operation<ItemStack> operation) {
      return ItemUseQueue.getInstance().displayStack(slot, (ItemStack)operation.call(inventory, slot));
   }

   @WrapOperation(method = "renderHotbar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;getSelectedSlot()I"))
   private int hideHelperSlotSelection(PlayerInventory inventory, Operation<Integer> operation) {
      return ItemHelper.getInstance().displayedHotbarSlot() != -1 ? ItemHelper.getInstance().displayedHotbarSlot() : (Integer)operation.call(inventory);
   }

   @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"), cancellable = true)
   private void hideScoreboardSidebar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo callback) {
      if (NoRender.getInstance().isOverlayDisabled(("Скорборд"))) {
         callback.cancel();
      }
   }

   @ModifyExpressionValue(
      method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/scoreboard/Scoreboard;getScoreboardEntries(Lnet/minecraft/scoreboard/ScoreboardObjective;)Ljava/util/Collection;")
   )
   private Collection<ScoreboardEntry> hideStreamerSidebarRows(Collection<ScoreboardEntry> entries) {
      return !StreamerMod.getInstance().isEnabled() && !YoutubeModeModule.getInstance().isEnabled()
         ? entries
         : entries.stream()
            .filter(
               entry -> (!YoutubeModeModule.getInstance().isEnabled() || !YoutubeModeModule.getInstance().isFortuneScore(entry))
                  && (!StreamerMod.getInstance().isEnabled() || !StreamerMod.getInstance().shouldHideSidebarEntry(entry))
            )
            .toList();
   }

   @ModifyExpressionValue(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/scoreboard/ScoreboardObjective;getDisplayName()Lnet/minecraft/text/Text;"))
   private Text maskStreamerSidebarTitle(Text text) {
      return !StreamerMod.getInstance().isEnabled() ? text : StreamerMod.getInstance().maskSidebarTitle(text);
   }

   @ModifyExpressionValue(
      method = "method_55439(Lnet/minecraft/scoreboard/Scoreboard;Lnet/minecraft/scoreboard/number/NumberFormat;Lnet/minecraft/scoreboard/ScoreboardEntry;)Lnet/minecraft/client/gui/hud/InGameHud$SidebarEntry;",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/scoreboard/Team;decorateName(Lnet/minecraft/scoreboard/AbstractTeam;Lnet/minecraft/text/Text;)Lnet/minecraft/text/MutableText;"
      )
   )
   private MutableText maskInfoSidebarRow(MutableText entry) {
      return !InfoReplace.getInstance().isEnabled() ? entry : InfoReplace.getInstance().replaceSidebarEntry(entry);
   }

   static {
      VMBridge.identifyClass(InGameHudMixin.class, "3SjEjwxM");
   }
}
