package com.mouseclient.gui;

import com.mouseclient.annotation.EventPriority;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.internal.GuiBlurState;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleRegistry;
import com.mouseclient.modules.impl.misc.ServerItemBindsHud;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.misc.EventBus;
import com.mouseclient.modules.impl.render.HudSettingsModule;
import com.mouseclient.modules.impl.render.StructureInfo;
import com.mouseclient.modules.impl.render.HudRenderEvent;
import com.mouseclient.render.avatar.CompactTargetHud;
import com.mouseclient.util.time.ArrowCountHud;
import com.mouseclient.util.time.HudLayout;
import com.mouseclient.util.time.ItemCooldownHud;
import com.mouseclient.util.time.ToastNotifications;
import com.mouseclient.util.time.HotkeysHud;
import com.mouseclient.util.time.InventoryHud;
import com.mouseclient.util.time.WatermarkHud;
import com.mouseclient.util.time.PotionEffectsHud;
import com.mouseclient.util.time.StaffHud;
import com.mouseclient.util.time.ExtendedTargetHud;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;

public final class HudRenderer {
   private static final HudRenderer INSTANCE = new HudRenderer();
   private final Map<Module, Boolean> moduleStates = new IdentityHashMap<>();
   private boolean moduleStatesInitialized;
   private long lastFrameTimeMillis;
   private float potionsHeight;
   private float cooldownsHeight;
   @EventPriority(Integer.MIN_VALUE)
   private final EventListener<HudRenderEvent> hudRenderListener;

   private void updateModuleNotifications() {
      boolean initialSnapshot = !this.moduleStatesInitialized;

      for (Module module : ModuleRegistry.modules()) {
         Boolean previousEnabled = this.moduleStates.put(module, module.isEnabled());
         if (!initialSnapshot && previousEnabled != null && previousEnabled != module.isEnabled()) {
            ToastNotifications.showIconToast(module.category.iconGlyph(), module.name, module.isEnabled() ? ("is Enabled!") : ("is Disabled!"), module.isEnabled());
         }
      }

      this.moduleStatesInitialized = true;
   }

   private HudRenderer() {
      this.lastFrameTimeMillis = System.nanoTime() / (1000000L);
      this.hudRenderListener = event -> {
         GuiBlurState.setRequested(false);
         HudSettingsModule.getInstance().syncThemeColor();
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.player != null && client.world != null) {
            if (!(client.currentScreen instanceof LevelLoadingScreen)
               && !(client.currentScreen instanceof ProgressScreen)
               && !(client.currentScreen instanceof MessageScreen)) {
               long lastFrameTimeMillis = System.nanoTime() / (1000000L);
               float deltaSeconds = Math.min((0.05F), (float)(lastFrameTimeMillis - this.lastFrameTimeMillis) / (1000.0F));
               this.lastFrameTimeMillis = lastFrameTimeMillis;
               float coordinateScale = GuiScale.getWindowScale() / GuiScale.getBaseScale();
               float hudWidth = event.getWidth() * coordinateScale;
               float hudHeight = event.getHeight() * coordinateScale;
               HudRenderContext.update(client, deltaSeconds);
               HudRenderContext.setDrawContext(event.getDrawContext());
               HudLayout.clearFrameBounds();
               HudLayout.updateDrag(
                  (float)(client.mouse.getScaledX(client.getWindow()) * coordinateScale),
                  (float)(client.mouse.getScaledY(client.getWindow()) * coordinateScale),
                  HudRenderContext.isChatOpen(),
                  hudWidth,
                  hudHeight
               );
               ToastNotifications.updatePreview(HudRenderContext.isChatOpen());
               this.updateModuleNotifications();
               if (StructureInfo.getInstance().isEnabled()) {
                  StructureInfo.getInstance().renderLabels(event);
                  Render2D.nextLayer(event.getDrawContext());
               }

               if (!client.options.hudHidden && HudSettingsModule.getInstance().isElementVisible(("cooldowns"))) {
                  ItemCooldownHud.renderHotbarTimers(client, event.getWidth(), event.getHeight());
                  Render2D.nextLayer(event.getDrawContext());
               }

               String lastOverlappingElement = "";
               ArrayList<float[]> previousBounds = new ArrayList();

               for (String measuredElementId : HudLayout.elementOrder()) {
                  float[] elementBounds = (!HudLayout.isHiddenWithF1(measuredElementId) || !client.options.hudHidden) && HudSettingsModule.getInstance().isElementVisible(measuredElementId)
                     ? HudLayout.boundsFor(measuredElementId)
                     : null;
                  if (elementBounds != null) {
                     for (float[] previousElementBounds : previousBounds) {
                        if (elementBounds[0] < previousElementBounds[0] + previousElementBounds[2]
                           && elementBounds[0] + elementBounds[2] > previousElementBounds[0]
                           && elementBounds[1] < previousElementBounds[1] + previousElementBounds[3]
                           && elementBounds[1] + elementBounds[3] > previousElementBounds[1]) {
                           lastOverlappingElement = measuredElementId;
                        }
                     }

                     previousBounds.add(elementBounds);
                  }
               }

               boolean sharedBlurApplied = false;

               for (String renderedElementId : new ArrayList<>(HudLayout.elementOrder())) {
                  if ((!HudLayout.isHiddenWithF1(renderedElementId) || !client.options.hudHidden) && HudSettingsModule.getInstance().isElementVisible(renderedElementId)) {
                     if (client.currentScreen == null && renderedElementId.equals(lastOverlappingElement)) {
                        GuiBlurState.setRequested(true);
                        event.getDrawContext().createNewRootLayer();
                        event.getDrawContext().applyBlur();
                        Render2D.setSharedBlurLayer(1);
                        sharedBlurApplied = true;
                     } else {
                        event.getDrawContext().createNewRootLayer();
                     }

                     float animatedElementScale = HudLayout.animatedScale(renderedElementId);
                     float[] elementCenter = HudLayout.centerFor(renderedElementId);
                     boolean elementTransformActive = Math.abs(animatedElementScale - 1.0F) > (0.001F) && elementCenter != null;
                     if (elementTransformActive) {
                        HudRenderContext.pushScale(elementCenter[0], elementCenter[1], animatedElementScale);
                     }

                     switch (renderedElementId) {
                        case ("watermark"):
                           WatermarkHud.render(client, (12.0F), (12.0F));
                           break;
                        case ("keybinds"):
                           HotkeysHud.render((12.0F), hudHeight - (12.0F));
                           break;
                        case ("potions"):
                           this.potionsHeight = PotionEffectsHud.render(client, hudWidth - (12.0F), (12.0F));
                           break;
                        case ("cooldowns"):
                           this.cooldownsHeight = ItemCooldownHud.render(
                              client, hudWidth - (12.0F), (12.0F) + (!HudLayout.hasSavedPosition(("potions")) ? this.potionsHeight : 0.0F)
                           );
                           break;
                        case ("staffs"):
                           StaffHud.render(
                              hudWidth - (12.0F),
                              (12.0F) + (!HudLayout.hasSavedPosition(("potions")) ? this.potionsHeight : 0.0F) + (!HudLayout.hasSavedPosition(("cooldowns")) ? this.cooldownsHeight : 0.0F)
                           );
                           break;
                        case ("serverbinds"):
                           ServerItemBindsHud.render(client, hudWidth / 2.0F, hudHeight - (34.0F));
                           break;
                        case ("inventory"):
                           InventoryHud.render(client, (12.0F), hudHeight / 2.0F - (20.0F));
                           break;
                        case ("Target"):
                           ExtendedTargetHud.render(client, (12.0F), hudHeight / 2.0F + (30.0F));
                           break;
                        case ("target"):
                           CompactTargetHud.render(client, hudWidth, hudHeight, deltaSeconds);
                           break;
                        case ("arrows"):
                           ArrowCountHud.render(hudWidth, hudHeight);
                     }

                     if (elementTransformActive) {
                        HudRenderContext.popTransform();
                     }
                  }
               }

               if (sharedBlurApplied) {
                  Render2D.setSharedBlurLayer(0);
               }

               event.getDrawContext().createNewRootLayer();
               if (HudSettingsModule.getInstance().isElementVisible(("toasts"))) {
                  boolean toastTransformActive = Math.abs(HudLayout.animatedScale(("toasts")) - 1.0F) > (0.001F) && HudLayout.centerFor(("toasts")) != null;
                  if (toastTransformActive) {
                     HudRenderContext.pushScale(HudLayout.centerFor(("toasts"))[0], HudLayout.centerFor(("toasts"))[1], HudLayout.animatedScale(("toasts")));
                  }

                  ToastNotifications.render(hudWidth, hudHeight);
                  if (toastTransformActive) {
                     HudRenderContext.popTransform();
                  }
               }

               event.getDrawContext().createNewRootLayer();
               HudLayout.renderEditOverlay(
                  (float)(client.mouse.getScaledX(client.getWindow()) * coordinateScale),
                  (float)(client.mouse.getScaledY(client.getWindow()) * coordinateScale),
                  HudRenderContext.isChatOpen()
               );
               if (HudSettingsPopup.isVisible()) {
                  event.getDrawContext().createNewRootLayer();
               }

               HudSettingsPopup.render(
                  (float)(client.mouse.getScaledX(client.getWindow()) * coordinateScale),
                  (float)(client.mouse.getScaledY(client.getWindow()) * coordinateScale),
                  HudRenderContext.isChatOpen()
               );
               if (!ClosingOverlayRegistry.isEmpty()) {
                  Render2D.nextLayer(event.getDrawContext());
                  ClosingOverlayRegistry.render(event.getDrawContext());
               }
            }
         } else {
            this.moduleStatesInitialized = false;
            this.moduleStates.clear();
         }
      };
   }

   public static void register() {
      EventBus.getInstance().subscribe(INSTANCE);
   }
}
