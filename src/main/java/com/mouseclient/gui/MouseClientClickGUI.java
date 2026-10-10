package com.mouseclient.gui;

import com.mouseclient.internal.ArgbColor;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.internal.GuiBlurState;
import com.mouseclient.internal.RoundedClipStack;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.ModuleRegistry;
import com.mouseclient.modules.impl.player.BindSetting;
import com.mouseclient.modules.impl.player.InputBinding;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.modules.impl.render.ColorSetting;
import com.mouseclient.modules.impl.render.HudSettingsModule;
import com.mouseclient.modules.impl.render.SettingSeparator;
import com.mouseclient.render.pipeline.SliderSetting;
import com.mouseclient.util.time.ActionSetting;
import com.mouseclient.util.time.ChoiceSetting;
import com.mouseclient.util.time.ClickGuiContext;
import com.mouseclient.util.time.ClickGuiTheme;
import com.mouseclient.util.time.GuiFont;
import com.mouseclient.util.time.GuiTextRenderer;
import com.mouseclient.util.time.GuiVisuals;
import com.mouseclient.modules.impl.misc.MultiChoiceSetting;
import com.mouseclient.util.time.Setting;
import com.mouseclient.util.time.TextSetting;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.Generated;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
public class MouseClientClickGUI extends Screen implements ClosingOverlayRegistry.ClosingOverlay {

   private final ClickGuiContext ctx = new ClickGuiContext();
   private final ModuleCategory[] categories = ModuleCategory.values();
   private ModuleCategory activeCategory = ModuleCategory.COMBAT;
   private Module selectedModule;
   private final SmoothScrollState moduleScroll = new SmoothScrollState();
   private final SmoothScrollState settingsScroll = new SmoothScrollState();
   private final float[] mainBounds = new float[4];
   private final float[] sidebarBounds = new float[4];
   private final float[] moduleBounds = new float[4];
   private final float[] settingsBounds = new float[4];
   private float visibilityProgress;
   private float transitionStartProgress;
   private long transitionStartTime;
   private boolean previousVisible;
   private boolean closing;
   private boolean screenRemovalScheduled;
   private boolean closingOverlayActive;
   private long lastFrameTime;
   private boolean searchFocused;
   private final TextSetting search;
   private long lastSearchEditTime;
   private Module settingsAnimatedModule;
   private long settingsRevealAt;
   private static MouseClientClickGUI INSTANCE;

   private MouseClientClickGUI() {
      super(Text.literal("маусклиент"));
      this.lastFrameTime = System.nanoTime() / 1000000L;
      this.search = new TextSetting("Поиск", "").withLengthLimits(0, 28);
   }

   public static MouseClientClickGUI instance() {
      if (INSTANCE == null) {
         INSTANCE = new MouseClientClickGUI();
      }
      return INSTANCE;
   }

   @Generated
   public ClickGuiContext getCtx() {
      return this.ctx;
   }

   @Override
   protected void init() {
      super.init();
      if (this.closingOverlayActive) {
         ClosingOverlayRegistry.remove(this);
         this.closingOverlayActive = false;
         this.closing = false;
         this.screenRemovalScheduled = false;
      }
      if (this.selectedModule == null) {
         List<Module> combatModules = ModuleRegistry.byCategory(ModuleCategory.COMBAT);
         if (!combatModules.isEmpty()) {
            this.selectedModule = combatModules.get(0);
         }
      }
   }

   @Override
   public void close() {
      if (!this.closing) {
         this.closing = true;
         this.ctx.focusedText = null;
         this.ctx.inputHandler = null;
         this.ctx.bindingSetting = null;
         this.ctx.popupOwner = null;
         this.ctx.bindingModule = null;
         this.closingOverlayActive = true;
         ClosingOverlayRegistry.add(this);
         if (MinecraftClient.getInstance().currentScreen == this) {
            MinecraftClient.getInstance().setScreen(null);
         }
      }
   }

   private void finishOverlay() {
      GuiBlurState.setEnabled(false);
      this.closingOverlayActive = false;
      this.closing = false;
      this.screenRemovalScheduled = false;
      this.visibilityProgress = 0.0F;
      this.transitionStartProgress = 0.0F;
      this.previousVisible = false;
      this.transitionStartTime = 0L;
   }

   @Override
   public void removed() {
      GuiBlurState.setEnabled(false);
      if (!this.closingOverlayActive) {
         this.closing = false;
         this.screenRemovalScheduled = false;
         this.visibilityProgress = 0.0F;
         this.transitionStartProgress = 0.0F;
         this.previousVisible = false;
         this.transitionStartTime = 0L;
      }
      this.ctx.focusedText = null;
      this.ctx.inputHandler = null;
      this.ctx.bindingSetting = null;
      this.ctx.popupOwner = null;
      this.ctx.bindingModule = null;
      this.searchFocused = false;
      this.search.setValue("");
      this.ctx.searchText = "";
      super.removed();
   }

   @Override
   public boolean shouldPause() {
      return false;
   }

   private static float coordinateScale() {
      return GuiScale.getWindowScale() / GuiScale.getBaseScale();
   }

   private static int opaqueRgb(int rgb) {
      return (-16777216) | (rgb & 16777215);
   }

   @Override
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float tickDelta) {
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float tickDelta) {
      float mouseScale = coordinateScale();
      float scaledMouseX = mouseX * mouseScale;
      float scaledMouseY = mouseY * mouseScale;
      this.ctx.beginFrame(
         context,
         1.13F * Math.min(1.0F, Math.min(this.width * mouseScale / 640.0F, this.height * mouseScale / 460.0F)),
         scaledMouseX,
         scaledMouseY
      );
      float deltaSeconds = Math.min(0.05F, (float)(System.nanoTime() / 1000000L - this.lastFrameTime) / 1000.0F);
      this.lastFrameTime = System.nanoTime() / 1000000L;
      HudSettingsModule.getInstance().syncThemeColor();
      Render2D.begin(context);

      boolean previousVisible = !this.closing;
      if (previousVisible != this.previousVisible) {
         this.transitionStartProgress = this.visibilityProgress;
         this.transitionStartTime = System.nanoTime() / 1000000L;
         this.previousVisible = previousVisible;
      }

      float transitionProgress = Math.min(1.0F, (float)(System.nanoTime() / 1000000L - this.transitionStartTime) / 420.0F);
      float easedTransition = transitionProgress * transitionProgress * transitionProgress * (transitionProgress * (transitionProgress * 6.0F - 15.0F) + 10.0F);
      this.visibilityProgress = this.transitionStartProgress + ((previousVisible ? 1.0F : 0.0F) - this.transitionStartProgress) * easedTransition;

      if (this.closing && this.visibilityProgress <= 0.003F) {
         GuiBlurState.setEnabled(false);
         Render2D.end();
         if (!this.screenRemovalScheduled) {
            this.screenRemovalScheduled = true;
            MinecraftClient.getInstance().execute(() -> {
               if (MinecraftClient.getInstance().currentScreen == this) {
                  MinecraftClient.getInstance().setScreen(null);
               }
            });
         }
         return;
      }

      float screenWidth = this.width * coordinateScale();
      float screenHeight = this.height * coordinateScale();
      GuiBlurState.setEnabled(true);
      GuiBlurState.setRadius(8.0F);
      GuiBlurState.setOpacity(0.0F);
      context.createNewRootLayer();
      if (this.closingOverlayActive) {
         try {
            context.applyBlur();
         } catch (IllegalStateException ignored) {
         }
      }

      Render2D.pushAlpha(this.visibilityProgress);

      float scale = this.ctx.scale;
      float containerWidth = 468.0F * scale;
      float containerHeight = 276.0F * scale;
      float mainX = (screenWidth - containerWidth) / 2.0F;
      float settle = 1.0F - this.visibilityProgress;
      float mainY = (screenHeight - containerHeight) / 2.0F + settle * 18.0F * scale;
      this.mainBounds[0] = mainX;
      this.mainBounds[1] = mainY;
      this.mainBounds[2] = containerWidth;
      this.mainBounds[3] = containerHeight;

      // 1. Draw outer main dark obsidian container with soft shadow and blur
      Render2D.drawSharedBlur(
         mainX + 0.5F * scale, mainY + 0.5F * scale,
         containerWidth - 1.0F * scale, containerHeight - 1.0F * scale,
         12.0F * scale, 30.0F, 1.0F, -1
      );
      Render2D.fillRoundedRectangle(mainX, mainY, containerWidth, containerHeight, 12.0F * scale, ClickGuiTheme.surface(0.055F, 0xF6));
      Render2D.drawOutline(mainX, mainY, containerWidth, containerHeight, 12.0F * scale, 0.5F * scale, ClickGuiTheme.accent(0x55));

      float sidebarWidth = 36.0F * scale;
      float moduleColumnWidth = 118.0F * scale;
      float rightPanelWidth = containerWidth - sidebarWidth - moduleColumnWidth;

      this.sidebarBounds[0] = mainX;
      this.sidebarBounds[1] = mainY;
      this.sidebarBounds[2] = sidebarWidth;
      this.sidebarBounds[3] = containerHeight;

      this.moduleBounds[0] = mainX + sidebarWidth;
      this.moduleBounds[1] = mainY;
      this.moduleBounds[2] = moduleColumnWidth;
      this.moduleBounds[3] = containerHeight;

      this.settingsBounds[0] = mainX + sidebarWidth + moduleColumnWidth;
      this.settingsBounds[1] = mainY;
      this.settingsBounds[2] = rightPanelWidth;
      this.settingsBounds[3] = containerHeight;

      // Vertical dividers
      Render2D.drawLine(mainX + sidebarWidth, mainY + 8.0F * scale, mainX + sidebarWidth, mainY + containerHeight - 8.0F * scale, 0.5F * scale, 0x14FFFFFF);
      Render2D.drawLine(mainX + sidebarWidth + moduleColumnWidth, mainY + 8.0F * scale, mainX + sidebarWidth + moduleColumnWidth, mainY + containerHeight - 8.0F * scale, 0.5F * scale, 0x14FFFFFF);

      // 2. Render Left Sidebar
      this.renderSidebar(mainX, mainY, sidebarWidth, containerHeight, scale);

      // 3. Render Middle Column (Modules)
      this.renderModuleList(mainX + sidebarWidth, mainY, moduleColumnWidth, containerHeight, scale, deltaSeconds);

      // 4. Render Right Panel (Top Bar + Settings Grid)
      this.renderRightPanel(mainX + sidebarWidth + moduleColumnWidth, mainY, rightPanelWidth, containerHeight, scale, deltaSeconds);

      // 5. Overlays (Popups, color picker, etc.)
      this.ctx.renderOverlays();

      // 6. Tooltips
      this.drawTip(screenWidth, screenHeight);

      Render2D.popAlpha();
      this.updateDrag(scaledMouseX, scaledMouseY);
      Render2D.end();
   }

   private void renderSidebar(float x, float y, float width, float height, float scale) {
      // Logo at top
      float logoSize = 13.0F * scale;
      float logoX = x + (width - logoSize) / 2.0F;
      float logoY = y + 11.0F * scale;
      Render2D.drawRoundedImage(
         "assets/mouseclient/textures/gui/mainmenu/icons/logo.png",
         logoX,
         logoY,
         logoSize,
         logoSize,
         0.0F,
         ClickGuiTheme.accentOpaque()
      );

      // Category icons
      float startCatY = y + 44.0F * scale;
      float catGap = 25.0F * scale;
      float iconSize = 11.5F * scale;
      int activeIndex = 0;
      boolean funActive = this.activeCategory == ModuleCategory.FUN;
      for (int i = 0; i < this.categories.length; i++) {
         if (this.categories[i] == this.activeCategory && !funActive) {
            activeIndex = i;
            break;
         }
      }
      if (!funActive) {
         float indicatorIndex = this.ctx.animateValue("sidebar.indicator", activeIndex);
         float indicatorY = startCatY + indicatorIndex * catGap - 1.5F * scale;
         Render2D.fillRoundedRectangle(x, indicatorY, 2.2F * scale, iconSize + 3.0F * scale, 1.1F * scale, ClickGuiTheme.accentOpaque());
      }

      for (int i = 0; i < this.categories.length; i++) {
         ModuleCategory cat = this.categories[i];
         if (cat == ModuleCategory.FUN) {
            continue;
         }
         float catY = startCatY + i * catGap;
         float iconX = x + (width - iconSize) / 2.0F;
         boolean isActive = (this.activeCategory == cat);
         boolean isHovered = GuiVisuals.containsPoint(this.ctx.mouseX, this.ctx.mouseY, x, catY - 4.0F * scale, width, catGap);
         float activeProg = this.ctx.animateBoolean("cat:" + cat.name(), isActive);
         float hoverProg = this.ctx.animateBoolean("catHover:" + cat.name(), isHovered && !isActive);
         if (hoverProg > 0.01F) {
            Render2D.fillRoundedRectangle(
               x + 6.0F * scale,
               catY - 3.0F * scale,
               width - 10.0F * scale,
               iconSize + 6.0F * scale,
               4.0F * scale,
               ClickGuiTheme.accent((int)(28.0F * hoverProg))
            );
         }
         int iconColor = GuiVisuals.lerpColor(GuiVisuals.lerpColor(0xFF6E7872, 0xFFC8D0CB, hoverProg), ClickGuiTheme.accentOpaque(), activeProg);
         Render2D.drawRoundedImage(
            ClickGuiTheme.categoryIcon(cat),
            iconX,
            catY,
            iconSize,
            iconSize,
            0.0F,
            iconColor
         );

         this.ctx.registerClick(x, catY - 4.0F * scale, width, catGap, null, () -> {
            this.activeCategory = cat;
            this.moduleScroll.target = 0.0F;
            this.moduleScroll.current = 0.0F;
            if (this.selectedModule == null || this.selectedModule.category != cat) {
               List<Module> mods = ModuleRegistry.byCategory(cat);
               if (!mods.isEmpty()) {
                  this.selectedModule = mods.get(0);
                  this.settingsScroll.target = 0.0F;
                  this.settingsScroll.current = 0.0F;
               }
            }
         });
      }

      // Settings icon at bottom
      float gearSize = 11.0F * scale;
      float gearX = x + (width - gearSize) / 2.0F;
      float gearY = y + height - 22.0F * scale;
      boolean gearHovered = GuiVisuals.containsPoint(this.ctx.mouseX, this.ctx.mouseY, x, gearY - 4.0F * scale, width, 18.0F * scale);
      boolean gearActive = this.activeCategory == ModuleCategory.FUN;
      if (gearActive) {
         Render2D.fillRoundedRectangle(x, gearY - 1.5F * scale, 2.2F * scale, gearSize + 3.0F * scale, 1.1F * scale, ClickGuiTheme.accentOpaque());
      }
      Render2D.drawRoundedImage(
         "assets/mouseclient/textures/gui/mainmenu/icons/settings.png",
         gearX,
         gearY,
         gearSize,
         gearSize,
         0.0F,
         gearActive ? ClickGuiTheme.accentOpaque() : (gearHovered ? 0xFFC8D0CB : 0xFF6E7872)
      );
      this.ctx.registerClick(x, gearY - 4.0F * scale, width, 18.0F * scale, null, () -> {
         this.activeCategory = ModuleCategory.FUN;
         List<Module> funModules = ModuleRegistry.byCategory(ModuleCategory.FUN);
         if (!funModules.isEmpty()) {
            this.selectedModule = funModules.get(0);
            this.settingsScroll.target = 0.0F;
            this.settingsScroll.current = 0.0F;
         }
      });
   }

   private void renderModuleList(float x, float y, float width, float height, float scale, float deltaSeconds) {
      // Category Title
      String titleText = this.ctx.searchText.isEmpty() ? this.activeCategory.displayName() : "Поиск";
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         titleText,
         x + 8.0F * scale,
         y + 11.0F * scale,
         7.5F * scale,
         0xFFFFFFFF
      );

      float viewportY = y + 26.0F * scale;
      float viewportH = height - 32.0F * scale;
      float viewportX = x + 4.0F * scale;
      float viewportW = width - 8.0F * scale;

      this.moduleScroll.update(deltaSeconds);

      List<Module> modulesToRender = new ArrayList<>();
      if (this.ctx.searchText.isEmpty()) {
         modulesToRender = ModuleRegistry.byCategory(this.activeCategory);
      } else {
         String query = this.ctx.searchText.toLowerCase(Locale.ROOT);
         for (Module m : ModuleRegistry.modules()) {
            if (m.name.toLowerCase(Locale.ROOT).contains(query)
               || (m.description != null && m.description.toLowerCase(Locale.ROOT).contains(query))) {
               modulesToRender.add(m);
            }
         }
      }

      Render2D.pushScissor(this.ctx.drawContext, viewportX, viewportY, viewportW, viewportH);
      RoundedClipStack.push(viewportX, viewportY, viewportW, viewportH, 0.0F, 0.0F, 6.0F * scale, 6.0F * scale);
      float[] clip = new float[]{viewportX, viewportY, viewportW, viewportH};

      float rowH = 16.5F * scale;
      float gapY = 2.0F * scale;
      float curY = viewportY - this.moduleScroll.getScroll();

      for (Module mod : modulesToRender) {
         if (curY + rowH >= viewportY - 2.0F && curY <= viewportY + viewportH + 2.0F) {
            boolean isSelected = (this.selectedModule == mod);
            boolean isEnabled = mod.isEnabled();
            boolean isHovered = GuiVisuals.containsPoint(this.ctx.mouseX, this.ctx.mouseY, viewportX, curY, viewportW, rowH)
               && GuiVisuals.containsPoint(this.ctx.mouseX, this.ctx.mouseY, clip[0], clip[1], clip[2], clip[3]);
            float selectedProg = this.ctx.animateBoolean("modSel:" + mod.name, isSelected);
            float hoverProg = this.ctx.animateBoolean("modHover:" + mod.name, isHovered && !isSelected);
            float enabledProg = this.ctx.animateBoolean("modOn:" + mod.name, isEnabled);
            if (selectedProg > 0.01F || hoverProg > 0.01F) {
               int fill = GuiVisuals.lerpColor(ClickGuiTheme.surface(0.08F, (int)(255.0F * hoverProg)), ClickGuiTheme.surface(0.16F, 0xFF), selectedProg);
               Render2D.fillRoundedRectangle(viewportX, curY, viewportW, rowH, 4.0F * scale, fill);
               if (selectedProg > 0.02F) {
                  Render2D.drawOutline(viewportX, curY, viewportW, rowH, 4.0F * scale, 0.5F * scale, ClickGuiTheme.accent((int)(102.0F * selectedProg)));
               }
            }

            int idleColor = GuiVisuals.lerpColor(0xFF8B948E, 0xFFC5CDC8, hoverProg);
            int nameColor = GuiVisuals.lerpColor(isEnabled ? ClickGuiTheme.accentOpaque() : idleColor, 0xFFFFFFFF, selectedProg * (1.0F - enabledProg));
            if (isEnabled) {
               nameColor = GuiVisuals.lerpColor(idleColor, ClickGuiTheme.accentOpaque(), Math.max(enabledProg, selectedProg));
            }
            if (enabledProg > 0.01F) {
               Render2D.fillRoundedRectangle(
                  viewportX + 1.6F * scale,
                  curY + 3.2F * scale,
                  1.6F * scale,
                  (rowH - 6.4F * scale) * enabledProg,
                  0.8F * scale,
                  ClickGuiTheme.accent((int)(255.0F * enabledProg))
               );
            }

            GuiTextRenderer.draw(
               GuiFont.INSTANCE,
               mod.name,
               viewportX + (6.0F + 0.8F * enabledProg) * scale,
               curY + (rowH - 6.2F * scale) / 2.0F,
               6.2F * scale,
               nameColor
            );

            if (mod.description != null && !mod.description.isEmpty() && isHovered) {
               this.ctx.offerTooltip(mod.description, viewportX, curY, viewportW, rowH);
            }

            // Register clicks: Left click selects, Right click toggles, Middle click binds
            this.ctx.registerClick(
               viewportX, curY, viewportW, rowH, clip,
               () -> {
                  this.selectedModule = mod;
                  this.settingsAnimatedModule = null;
                  this.settingsScroll.target = 0.0F;
                  this.settingsScroll.current = 0.0F;
               },
               () -> mod.toggle(),
               () -> this.ctx.bindingModule = (this.ctx.bindingModule == mod ? null : mod)
            );
         }
         curY += rowH + gapY;
      }

      RoundedClipStack.pop();
      Render2D.popScissor(this.ctx.drawContext);

      float totalListH = curY - (viewportY - this.moduleScroll.getScroll());
      this.moduleScroll.maxScroll = Math.max(0.0F, totalListH - viewportH + 4.0F * scale);
   }

   private void renderRightPanel(float x, float y, float width, float height, float scale, float deltaSeconds) {
      // Top Bar: Search
      float barY = y + 8.5F * scale;
      float barH = 15.0F * scale;

      float searchX = x + 12.0F * scale;
      float searchW = width - 24.0F * scale;
      Render2D.fillRoundedRectangle(searchX, barY, searchW, barH, barH / 2.0F, ClickGuiTheme.surface(0.09F, 0xFF));
      Render2D.drawOutline(searchX, barY, searchW, barH, barH / 2.0F, 0.5F * scale, this.searchFocused ? ClickGuiTheme.accent(0xAA) : ClickGuiTheme.accent(0x38));
      Render2D.drawRoundedImage(
         "assets/mouseclient/textures/gui/clickgui/search.png",
         searchX + 5.0F * scale,
         barY + (barH - 7.0F * scale) / 2.0F,
         7.0F * scale,
         7.0F * scale,
         0.0F,
         0xFF8B948E
      );

      String searchText = this.search.getText();
      String searchDisplay = searchText.isEmpty() ? (this.searchFocused ? "" : "Поиск") : searchText;
      float textX = searchX + 15.0F * scale;
      float textY = barY + (barH - 5.6F * scale) / 2.0F;
      float maxTextW = searchW - 20.0F * scale;

      Render2D.pushScissor(this.ctx.drawContext, textX - 1.0F * scale, barY, maxTextW + 2.0F * scale, barH);
      float displayW = GuiTextRenderer.getWidth(GuiFont.INSTANCE, searchDisplay, 5.6F * scale);
      float searchScroll = Math.max(0.0F, displayW - maxTextW);

      if (this.search.hasSelection() && !searchText.isEmpty()) {
         Render2D.fillRoundedRectangle(
            textX - searchScroll + GuiTextRenderer.getWidth(GuiFont.INSTANCE, searchText.substring(0, this.search.getSelectionStart()), 5.6F * scale),
            textY - 1.0F * scale,
            GuiTextRenderer.getWidth(GuiFont.INSTANCE, searchText.substring(this.search.getSelectionStart(), this.search.getSelectionEnd()), 5.6F * scale),
            7.5F * scale,
            1.0F * scale,
            ArgbColor.withAlpha(-1, 56)
         );
      }

      if (!searchDisplay.isEmpty()) {
         GuiTextRenderer.draw(
            GuiFont.INSTANCE,
            searchDisplay,
            textX - searchScroll,
            textY,
            5.6F * scale,
            searchText.isEmpty() ? 0xFF8B948E : 0xFFE6E6E6
         );
      }

      if (this.searchFocused && (System.nanoTime() / 1000000L / 530L) % 2L == 0L) {
         float cursorX = textX - searchScroll + GuiTextRenderer.getWidth(GuiFont.INSTANCE, searchText.substring(0, this.search.getCursor()), 5.6F * scale);
         Render2D.fillRoundedRectangle(cursorX + 0.4F * scale, textY - 0.5F * scale, 0.6F * scale, 6.5F * scale, 0.3F * scale, 0xFFE0E0E0);
      }
      Render2D.popScissor(this.ctx.drawContext);

      this.ctx.registerClick(searchX, barY, searchW, barH, null, () -> {
         this.searchFocused = true;
         this.search.moveToEnd(false);
         this.lastSearchEditTime = System.nanoTime() / 1000000L;
      });

      // Module Header & Settings
      if (this.selectedModule == null) {
         GuiTextRenderer.draw(
            GuiFont.INSTANCE,
            "Выберите модуль для настройки",
            x + (width - GuiTextRenderer.getWidth(GuiFont.INSTANCE, "Выберите модуль для настройки", 6.5F * scale)) / 2.0F,
            y + height / 2.0F,
            6.5F * scale,
            0xFF8B948E
         );
         return;
      }

      float headY = y + 32.0F * scale;
      String modTitle = "Настройки " + this.selectedModule.name;
      float controlsCenter = headY + 3.6F * scale;
      float closeSize = 6.2F * scale;
      float closeBtnX = x + width - 18.0F * scale;
      GuiTextRenderer.draw(GuiFont.SEMI, "×", closeBtnX, controlsCenter - closeSize / 2.0F, closeSize, 0xFF8B948E);
      this.ctx.registerClick(closeBtnX - 3.0F * scale, controlsCenter - 6.0F * scale, 14.0F * scale, 12.0F * scale, null, () -> this.selectedModule = null);

      float toggleSwitchW = 14.0F * scale;
      float toggleSwitchH = 8.0F * scale;
      float toggleSwitchX = closeBtnX - toggleSwitchW - 8.0F * scale;
      float toggleSwitchY = controlsCenter - toggleSwitchH / 2.0F;
      float modTglProg = this.ctx.animateBoolean("headTgl:" + this.selectedModule.name, this.selectedModule.isEnabled());
      Render2D.fillRoundedRectangle(
         toggleSwitchX, toggleSwitchY, toggleSwitchW, toggleSwitchH, toggleSwitchH / 2.0F,
         GuiVisuals.lerpColor(ClickGuiTheme.surface(0.12F, 0xFF), ClickGuiTheme.accentOpaque(), modTglProg)
      );
      if (modTglProg < 0.5F) {
         Render2D.drawOutline(toggleSwitchX, toggleSwitchY, toggleSwitchW, toggleSwitchH, toggleSwitchH / 2.0F, 0.5F * scale, ClickGuiTheme.accent(0x44));
      }
      float modKnobD = 5.6F * scale;
      float modKnobX = toggleSwitchX + 1.2F * scale + modTglProg * (toggleSwitchW - modKnobD - 2.4F * scale);
      Render2D.fillCircle(modKnobX + modKnobD / 2.0F, toggleSwitchY + toggleSwitchH / 2.0F, modKnobD / 2.0F, 0xFFFFFFFF);
      this.ctx.registerClick(toggleSwitchX - 1.0F * scale, toggleSwitchY - 1.0F * scale, toggleSwitchW + 2.0F * scale, toggleSwitchH + 2.0F * scale, null, () -> this.selectedModule.toggle());

      String bindKeyName = (this.ctx.bindingModule == this.selectedModule) ? "..." : (this.selectedModule.getKeyCode() > 0 ? InputBinding.keyboard(this.selectedModule.getKeyCode()).getDisplayName() : "NONE");
      String bindDisplay = "[ " + bindKeyName + " ]";
      float bindTextSize = 5.0F * scale;
      float bindW = GuiTextRenderer.getWidth(GuiFont.SEMI, bindDisplay, bindTextSize) + 8.0F * scale;
      float bindH = 10.0F * scale;
      float bindX = toggleSwitchX - bindW - 6.0F * scale;
      float bindY = controlsCenter - bindH / 2.0F;
      Render2D.fillRoundedRectangle(bindX, bindY, bindW, bindH, 3.0F * scale, ClickGuiTheme.surface(0.12F, 0xFF));
      Render2D.drawOutline(bindX, bindY, bindW, bindH, 3.0F * scale, 0.5F * scale, ClickGuiTheme.accent(0x55));
      int bindColor = (this.ctx.bindingModule == this.selectedModule || this.selectedModule.isBound()) ? ClickGuiTheme.accentOpaque() : 0xFF8B948E;
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         bindDisplay,
         bindX + (bindW - GuiTextRenderer.getWidth(GuiFont.SEMI, bindDisplay, bindTextSize)) / 2.0F,
         bindY + (bindH - bindTextSize) / 2.0F,
         bindTextSize,
         bindColor
      );
      this.ctx.registerClick(bindX, bindY, bindW, bindH, null, () -> this.ctx.bindingModule = (this.ctx.bindingModule == this.selectedModule ? null : this.selectedModule));

      float titleMax = Math.max(24.0F * scale, bindX - (x + 18.0F * scale));
      Render2D.pushScissor(this.ctx.drawContext, x + 12.0F * scale, headY - 1.0F * scale, titleMax, 9.0F * scale);
      GuiTextRenderer.draw(GuiFont.SEMI, modTitle, x + 12.0F * scale, headY, 7.2F * scale, 0xFFFFFFFF);
      Render2D.popScissor(this.ctx.drawContext);

      String desc = this.selectedModule.description != null ? this.selectedModule.description : "";
      if (!desc.isEmpty()) {
         Render2D.pushScissor(this.ctx.drawContext, x + 12.0F * scale, headY + 9.0F * scale, width - 24.0F * scale, 8.0F * scale);
         GuiTextRenderer.draw(GuiFont.INSTANCE, desc, x + 12.0F * scale, headY + 9.6F * scale, 5.0F * scale, 0xFF8B948E);
         Render2D.popScissor(this.ctx.drawContext);
      }

      // Settings Grid Viewport
      float vpY = y + 52.0F * scale;
      float vpH = height - 60.0F * scale;
      float vpX = x + 12.0F * scale;
      float vpW = width - 24.0F * scale;
      if (this.settingsAnimatedModule != this.selectedModule) {
         this.settingsAnimatedModule = this.selectedModule;
         this.settingsRevealAt = System.nanoTime() / 1000000L;
      }
      float revealT = Math.min(1.0F, (float)(System.nanoTime() / 1000000L - this.settingsRevealAt) / 260.0F);
      float reveal = revealT * revealT * (3.0F - 2.0F * revealT);
      Render2D.pushAlpha(reveal);

      this.settingsScroll.update(deltaSeconds);

      List<Setting<?>> settings = new ArrayList<>();
      for (Setting<?> s : this.selectedModule.getSettings()) {
         if (s.isVisible() && !(s instanceof SettingSeparator)) {
            settings.add(s);
         }
      }

      if (settings.isEmpty()) {
         GuiTextRenderer.draw(
            GuiFont.INSTANCE,
            "У этого модуля нет настроек",
            vpX + (vpW - GuiTextRenderer.getWidth(GuiFont.INSTANCE, "У этого модуля нет настроек", 6.0F * scale)) / 2.0F,
            vpY + vpH / 2.0F - 4.0F * scale,
            6.0F * scale,
            0xFF8B948E
         );
         this.settingsScroll.maxScroll = 0.0F;
         Render2D.popAlpha();
         return;
      }

      Render2D.pushScissor(this.ctx.drawContext, vpX, vpY, vpW, vpH);
      RoundedClipStack.push(vpX, vpY, vpW, vpH, 0.0F, 0.0F, 4.0F * scale, 4.0F * scale);
      float[] clip = new float[]{vpX, vpY, vpW, vpH};

      float cursorY = vpY - this.settingsScroll.getScroll();
      float gapBetween = 11.0F * scale;

      for (Setting<?> setting : settings) {
         float itemHeight = this.renderSettingItem(setting, vpX, cursorY, vpW, scale, clip, vpY, vpH);
         cursorY += itemHeight + gapBetween;
      }

      RoundedClipStack.pop();
      Render2D.popScissor(this.ctx.drawContext);

      float totalSettingsH = cursorY - (vpY - this.settingsScroll.getScroll());
      this.settingsScroll.maxScroll = Math.max(0.0F, totalSettingsH - vpH + 8.0F * scale);
      Render2D.popAlpha();
   }

   private float renderSettingItem(Setting<?> setting, float x, float y, float width, float scale, float[] clip, float vpY, float vpH) {
      float estimatedHeight = this.getSettingHeight(setting, width, scale);
      if (y + estimatedHeight < vpY - 4.0F || y > vpY + vpH + 4.0F) {
         return estimatedHeight;
      }

      if (setting instanceof ChoiceSetting choice) {
         // Label
         GuiTextRenderer.draw(GuiFont.INSTANCE, choice.name, x, y, 5.8F * scale, 0xFFD7DDD9);
         if (choice.description != null && !choice.description.isEmpty()) {
            this.ctx.offerTooltip(choice.description, x, y - 1.0F * scale, width, 8.0F * scale);
         }

         List<String> options = choice.getOptions();
         float pillX = x;
         float pillY = y + 8.0F * scale;
         float pillH = 11.0F * scale;

         for (int i = 0; i < options.size(); i++) {
            String opt = options.get(i);
            float optW = GuiTextRenderer.getWidth(GuiFont.SEMI, opt, 5.2F * scale) + 10.0F * scale;
            if (pillX + optW > x + width + 0.01F && pillX > x) {
               pillX = x;
               pillY += pillH + 3.0F * scale;
            }

            boolean isSelected = (choice.getSelectedIndex() == i);
            boolean isHovered = GuiVisuals.containsPoint(this.ctx.mouseX, this.ctx.mouseY, pillX, pillY, optW, pillH)
               && GuiVisuals.containsPoint(this.ctx.mouseX, this.ctx.mouseY, clip[0], clip[1], clip[2], clip[3]);
            float selectedProg = this.ctx.animateBoolean("choice:" + System.identityHashCode(choice) + ":" + i, isSelected);
            float hoverProg = this.ctx.animateBoolean("choiceHover:" + System.identityHashCode(choice) + ":" + i, isHovered && !isSelected);
            int idleFill = GuiVisuals.lerpColor(ClickGuiTheme.surface(0.09F, 0xFF), ClickGuiTheme.surface(0.16F, 0xFF), hoverProg);
            Render2D.fillRoundedRectangle(pillX, pillY, optW, pillH, 3.0F * scale, GuiVisuals.lerpColor(idleFill, ClickGuiTheme.accentOpaque(), selectedProg));
            if (selectedProg < 0.98F) {
               Render2D.drawOutline(pillX, pillY, optW, pillH, 3.0F * scale, 0.5F * scale, ClickGuiTheme.accent((int)(64.0F * (1.0F - selectedProg))));
            }

            GuiTextRenderer.draw(
               GuiFont.SEMI,
               opt,
               pillX + 5.0F * scale,
               pillY + (pillH - 5.2F * scale) / 2.0F,
               5.2F * scale,
               GuiVisuals.lerpColor(isHovered ? 0xFFC5CDC8 : 0xFF8B948E, ClickGuiTheme.onAccent(), selectedProg)
            );

            int clickedIdx = i;
            this.ctx.registerClick(pillX, pillY, optW, pillH, clip, () -> choice.setSelectedIndex(clickedIdx));
            pillX += optW + 3.0F * scale;
         }
         return (pillY + pillH) - y;

      } else if (setting instanceof MultiChoiceSetting multi) {
         // Label on left, "X из Y" on right
         GuiTextRenderer.draw(GuiFont.INSTANCE, multi.name, x, y, 5.8F * scale, 0xFFD7DDD9);
         String countText = multi.getSelectedCount() + " из " + multi.getOptions().size();
         float countW = GuiTextRenderer.getWidth(GuiFont.SEMI, countText, 5.2F * scale);
         GuiTextRenderer.draw(GuiFont.SEMI, countText, x + width - countW, y, 5.2F * scale, ClickGuiTheme.accentOpaque());

         if (multi.description != null && !multi.description.isEmpty()) {
            this.ctx.offerTooltip(multi.description, x, y - 1.0F * scale, width, 8.0F * scale);
         }

         List<String> options = multi.getOptions();
         float pillX = x;
         float pillY = y + 8.0F * scale;
         float pillH = 11.0F * scale;

         for (int i = 0; i < options.size(); i++) {
            String opt = options.get(i);
            float optW = GuiTextRenderer.getWidth(GuiFont.SEMI, opt, 5.2F * scale) + 10.0F * scale;
            if (pillX + optW > x + width + 0.01F && pillX > x) {
               pillX = x;
               pillY += pillH + 3.0F * scale;
            }

            boolean isSelected = multi.isSelectedAt(i);
            boolean isHovered = GuiVisuals.containsPoint(this.ctx.mouseX, this.ctx.mouseY, pillX, pillY, optW, pillH)
               && GuiVisuals.containsPoint(this.ctx.mouseX, this.ctx.mouseY, clip[0], clip[1], clip[2], clip[3]);

            if (isSelected) {
               Render2D.fillRoundedRectangle(pillX, pillY, optW, pillH, 3.0F * scale, ClickGuiTheme.surface(0.22F, 0xFF));
               Render2D.drawOutline(pillX, pillY, optW, pillH, 3.0F * scale, 0.5F * scale, ClickGuiTheme.accentOpaque());
            } else {
               Render2D.fillRoundedRectangle(pillX, pillY, optW, pillH, 3.0F * scale, isHovered ? ClickGuiTheme.surface(0.16F, 0xFF) : ClickGuiTheme.surface(0.09F, 0xFF));
               Render2D.drawOutline(pillX, pillY, optW, pillH, 3.0F * scale, 0.5F * scale, ClickGuiTheme.accent(0x40));
            }

            GuiTextRenderer.draw(
               GuiFont.SEMI,
               opt,
               pillX + 5.0F * scale,
               pillY + (pillH - 5.2F * scale) / 2.0F,
               5.2F * scale,
               isSelected ? ClickGuiTheme.accentOpaque() : (isHovered ? 0xFFC5CDC8 : 0xFF8B948E)
            );

            int clickedIdx = i;
            this.ctx.registerClick(pillX, pillY, optW, pillH, clip, () -> multi.toggleOptionAt(clickedIdx));
            pillX += optW + 3.0F * scale;
         }
         return (pillY + pillH) - y;

      } else if (setting instanceof SliderSetting slider) {
         // Label on left, formatted value on right
         String val = slider.getFormattedValue();
         float valW = GuiTextRenderer.getWidth(GuiFont.SEMI, val, 5.6F * scale);
         GuiTextRenderer.draw(GuiFont.INSTANCE, slider.name, x, y, 5.8F * scale, 0xFFD7DDD9);
         GuiTextRenderer.draw(GuiFont.SEMI, val, x + width - valW, y, 5.6F * scale, ClickGuiTheme.accentOpaque());

         if (slider.description != null && !slider.description.isEmpty()) {
            this.ctx.offerTooltip(slider.description, x, y - 1.0F * scale, width, 8.0F * scale);
         }

         float trackY = y + 8.5F * scale;
         float trackH = 2.2F * scale;
         Render2D.fillRoundedRectangle(x, trackY, width, trackH, trackH / 2.0F, ClickGuiTheme.surface(0.1F, 0xFF));
         Render2D.drawOutline(x, trackY, width, trackH, trackH / 2.0F, 0.5F * scale, ClickGuiTheme.accent(0x38));

         float norm = slider.getNormalizedValue();
         float thumbD = 5.0F * scale;
         float thumbR = thumbD / 2.0F;
         float thumbCx = x + thumbR + norm * Math.max(0.0F, width - thumbD);
         float fillW = Math.max(thumbR, thumbCx - x);
         Render2D.fillRoundedRectangle(x, trackY, fillW, trackH, trackH / 2.0F, ClickGuiTheme.accentOpaque());

         Render2D.fillCircle(thumbCx, trackY + trackH / 2.0F, thumbR, 0xFFFFFFFF);

         this.ctx.sliderBounds.put(slider, new float[]{x, width});
         this.ctx.registerClick(
            x, trackY - 3.0F * scale, width, trackH + 6.0F * scale, clip,
            () -> this.ctx.draggedSlider = slider,
            () -> slider.setValue(this.ctx.stateFor(slider, slider.floatValue()))
         );
         return 14.0F * scale;

      } else if (setting instanceof BooleanSetting bool) {
         // Label on left, switch toggle on right
         GuiTextRenderer.draw(GuiFont.INSTANCE, bool.name, x, y + 1.0F * scale, 5.8F * scale, 0xFFD7DDD9);
         if (bool.description != null && !bool.description.isEmpty()) {
            this.ctx.offerTooltip(bool.description, x, y - 1.0F * scale, width - 16.0F * scale, 10.0F * scale);
         }

         float switchW = 13.0F * scale;
         float switchH = 7.5F * scale;
         float switchX = x + width - switchW;
         float switchY = y;
         float checkedProg = this.ctx.animateBoolean("bool:" + System.identityHashCode(bool), bool.getValue());
         int trackColor = GuiVisuals.lerpColor(ClickGuiTheme.surface(0.12F, 0xFF), ClickGuiTheme.accentOpaque(), checkedProg);

         Render2D.fillRoundedRectangle(switchX, switchY, switchW, switchH, switchH / 2.0F, trackColor);
         if (checkedProg < 0.5F) {
            Render2D.drawOutline(switchX, switchY, switchW, switchH, switchH / 2.0F, 0.5F * scale, ClickGuiTheme.accent(0x44));
         }

         float knobD = 5.2F * scale;
         float knobX = switchX + 1.15F * scale + checkedProg * (switchW - knobD - 2.3F * scale);
         Render2D.fillCircle(knobX + knobD / 2.0F, switchY + switchH / 2.0F, knobD / 2.0F, 0xFFFFFFFF);

         this.ctx.registerClick(x, y - 1.0F * scale, width, switchH + 2.0F * scale, clip, () -> bool.setValue(!bool.getValue()));
         return 11.5F * scale;

      } else if (setting instanceof ColorSetting color) {
         GuiTextRenderer.draw(GuiFont.INSTANCE, color.name, x, y + 1.0F * scale, 5.8F * scale, 0xFFD7DDD9);
         if (color.description != null && !color.description.isEmpty()) {
            this.ctx.offerTooltip(color.description, x, y - 1.0F * scale, width, 10.0F * scale);
         }

         float[] hsba = this.ctx.hsbaState(color);
         int previewColor = opaqueRgb(Color.HSBtoRGB(hsba[0], hsba[1], hsba[2]));
         float boxW = 14.0F * scale;
         float boxH = 7.5F * scale;
         float boxX = x + width - boxW;
         float boxY = y;

         Render2D.fillRoundedRectangle(boxX, boxY, boxW, boxH, 2.5F * scale, previewColor);
         Render2D.drawOutline(boxX, boxY, boxW, boxH, 2.5F * scale, 0.5F * scale, 0x40FFFFFF);

         this.ctx.registerClick(x, y, width, boxH + 2.0F * scale, clip, () -> this.ctx.popupOwner = (this.ctx.popupOwner == color ? null : color));
         int id = System.identityHashCode(color);
         boolean open = (this.ctx.popupOwner == color);
         float popProg = this.ctx.animateValue("colorpop:" + id, open ? 1.0F : 0.0F);
         if (popProg > 0.01F) {
            float anchorX = boxX + boxW + 4.0F * scale;
            float anchorY = y;
            this.ctx.overlayRenderers.add(() -> {
               float[] pickerHsba = this.ctx.hsbaState(color);
               float aSat = this.ctx.animateValue("colorS:" + id, pickerHsba[1]);
               float aBri = this.ctx.animateValue("colorB:" + id, pickerHsba[2]);
               float aHue = this.ctx.animateValue("colorH:" + id, pickerHsba[0]);
               float pad = 4.0F * scale;
               float pW = 58.0F * scale;
               float pH = 40.0F * scale;
               float barH = 3.5F * scale;
               float popW = pW + pad * 2.0F;
               float popH = pad + pH + pad + barH + pad;
               float popX = anchorX - popW;
               float popY = Math.max(4.0F * scale, anchorY - popH) + (1.0F - popProg) * 5.0F * scale;
               if (open) {
                  this.ctx.registerClick(-100000.0F, -100000.0F, 200000.0F, 200000.0F, null, () -> this.ctx.popupOwner = null);
               }
               Render2D.pushAlpha(popProg);
               Render2D.drawSharedBlur(popX + 0.4F * scale, popY + 0.4F * scale, popW - 0.8F * scale, popH - 0.8F * scale, 4.1F * scale, 15.0F, 1.0F, -1);
               Render2D.fillRoundedRectangle(popX, popY, popW, popH, 4.5F * scale, ClickGuiTheme.popupBackground);
               Render2D.drawOutline(popX, popY, popW, popH, 4.5F * scale, 0.5F * scale, ClickGuiTheme.popupOutline);
               float pickX = popX + pad;
               float pickY = popY + pad;
               int hueCol = opaqueRgb(Color.HSBtoRGB(aHue, 1.0F, 1.0F));
               Render2D.fillRoundedGradient(pickX, pickY, pW, pH, 2.5F * scale, GuiVisuals.white, hueCol, hueCol, GuiVisuals.white);
               Render2D.fillRoundedGradient(pickX, pickY, pW, pH, 2.5F * scale, 0, 0, -16777216, -16777216);
               Render2D.drawOutline(pickX, pickY, pW, pH, 2.5F * scale, 0.5F * scale, 1023410176);
               float satX = pickX + aSat * pW;
               float briY = pickY + (1.0F - aBri) * pH;
               Render2D.fillCircle(satX, briY, 2.2F * scale, opaqueRgb(Color.HSBtoRGB(aHue, aSat, aBri)));
               Render2D.strokeCircle(satX, briY, 2.6F * scale, 0.8F * scale, GuiVisuals.white);
               float hBarY = pickY + pH + pad;
               Render2D.drawHuePicker(pickX, hBarY, pW, barH, 1.0F);
               float hX = pickX + aHue * pW;
               Render2D.fillCircle(hX, hBarY + barH / 2.0F, 2.2F * scale, hueCol);
               Render2D.strokeCircle(hX, hBarY + barH / 2.0F, 2.6F * scale, 0.8F * scale, GuiVisuals.white);
               Render2D.popAlpha();
               if (open) {
                  this.ctx.registerClick(pickX, pickY, pW, pH, null, () -> {
                     this.ctx.draggedColor = color;
                     this.ctx.colorDragMode = 0;
                  });
                  this.ctx.registerClick(pickX, hBarY - 2.0F * scale, pW, barH + 4.0F * scale, null, () -> {
                     this.ctx.draggedColor = color;
                     this.ctx.colorDragMode = 1;
                  });
                  this.ctx.colorPickerBounds.put(color, new float[]{pickX, pickY, pW, pH, pickX, hBarY, pW, barH});
               }
            });
         }
         return 11.5F * scale;

      } else if (setting instanceof BindSetting bind) {
         GuiTextRenderer.draw(GuiFont.INSTANCE, bind.name, x, y + 1.0F * scale, 5.8F * scale, 0xFFD7DDD9);
         if (bind.description != null && !bind.description.isEmpty()) {
            this.ctx.offerTooltip(bind.description, x, y - 1.0F * scale, width, 10.0F * scale);
         }

         String bName = (this.ctx.bindingSetting == bind) ? "..." : (bind.isBound() ? bind.getBindingName() : "NONE");
         String bDisplay = "[ " + bName + " ]";
         float bW = GuiTextRenderer.getWidth(GuiFont.SEMI, bDisplay, 5.2F * scale) + 6.0F * scale;
         float bH = 9.5F * scale;
         float bX = x + width - bW;
         float bY = y;

         Render2D.fillRoundedRectangle(bX, bY, bW, bH, 2.5F * scale, ClickGuiTheme.surface(0.12F, 0xFF));
         Render2D.drawOutline(bX, bY, bW, bH, 2.5F * scale, 0.5F * scale, ClickGuiTheme.accent(0x55));
         int bCol = (this.ctx.bindingSetting == bind || bind.isBound()) ? ClickGuiTheme.accentOpaque() : 0xFF8B948E;
         GuiTextRenderer.draw(
            GuiFont.SEMI,
            bDisplay,
            bX + (bW - GuiTextRenderer.getWidth(GuiFont.SEMI, bDisplay, 5.2F * scale)) / 2.0F,
            bY + 2.0F * scale,
            5.2F * scale,
            bCol
         );

         this.ctx.registerClick(bX, bY, bW, bH, clip, () -> this.ctx.bindingSetting = (this.ctx.bindingSetting == bind ? null : bind));
         return 11.5F * scale;

      } else if (setting instanceof ActionSetting action) {
         GuiTextRenderer.draw(GuiFont.INSTANCE, action.name, x, y + 1.0F * scale, 5.8F * scale, 0xFFD7DDD9);
         if (action.description != null && !action.description.isEmpty()) {
            this.ctx.offerTooltip(action.description, x, y - 1.0F * scale, width, 10.0F * scale);
         }

         float btnW = GuiTextRenderer.getWidth(GuiFont.SEMI, action.getLabel(), 5.2F * scale) + 10.0F * scale;
         float btnH = 9.5F * scale;
         float btnX = x + width - btnW;
         float btnY = y;

         Render2D.fillRoundedRectangle(btnX, btnY, btnW, btnH, 2.5F * scale, ClickGuiTheme.accentOpaque());
         GuiTextRenderer.draw(
            GuiFont.SEMI,
            action.getLabel(),
            btnX + 5.0F * scale,
            btnY + 2.0F * scale,
            5.2F * scale,
            ClickGuiTheme.onAccent()
         );

         this.ctx.registerClick(btnX, btnY, btnW, btnH, clip, () -> action.execute());
         return 11.5F * scale;

      } else if (setting instanceof TextSetting textSet) {
         GuiTextRenderer.draw(GuiFont.INSTANCE, textSet.name, x, y, 5.8F * scale, 0xFFD7DDD9);
         if (textSet.description != null && !textSet.description.isEmpty()) {
            this.ctx.offerTooltip(textSet.description, x, y - 1.0F * scale, width, 8.0F * scale);
         }

         float boxY = y + 8.0F * scale;
         float boxH = 11.0F * scale;
         boolean isFocused = (this.ctx.focusedText == textSet);

         Render2D.fillRoundedRectangle(x, boxY, width, boxH, 3.0F * scale, ClickGuiTheme.surface(0.09F, 0xFF));
         Render2D.drawOutline(x, boxY, width, boxH, 3.0F * scale, 0.5F * scale, isFocused ? ClickGuiTheme.accentOpaque() : ClickGuiTheme.accent(0x38));

         String currentText = textSet.getValue();
         String placeholder = textSet.getPlaceholder().isEmpty() ? "Введите текст" : textSet.getPlaceholder();
         String toDraw = isFocused ? currentText : (currentText.isEmpty() ? placeholder : currentText);
         int textColor = currentText.isEmpty() ? 0xFF8B948E : 0xFFE6E6E6;

         Render2D.pushScissor(this.ctx.drawContext, x + 3.0F * scale, boxY, width - 6.0F * scale, boxH);
         if (isFocused) {
            this.ctx.drawTextInput(textSet, x + 4.0F * scale, boxY + 2.8F * scale, 5.2F * scale, 0xFFFFFFFF);
         } else {
            GuiTextRenderer.draw(GuiFont.INSTANCE, toDraw, x + 4.0F * scale, boxY + 2.8F * scale, 5.2F * scale, textColor);
         }
         Render2D.popScissor(this.ctx.drawContext);

         this.ctx.registerClick(x, boxY, width, boxH, clip, () -> this.ctx.focusedText = textSet);
         return 21.0F * scale;
      }

      return 12.0F * scale;
   }

   private float getSettingHeight(Setting<?> setting, float width, float scale) {
      if (setting instanceof ChoiceSetting choice) {
         List<String> options = choice.getOptions();
         float pillX = 0.0F;
         float pillH = 11.0F * scale;
         float totalH = 8.0F * scale + pillH;
         for (String opt : options) {
            float optW = GuiTextRenderer.getWidth(GuiFont.SEMI, opt, 5.2F * scale) + 10.0F * scale;
            if (pillX + optW > width && pillX > 0.0F) {
               pillX = 0.0F;
               totalH += pillH + 3.0F * scale;
            }
            pillX += optW + 3.0F * scale;
         }
         return totalH;
      } else if (setting instanceof MultiChoiceSetting multi) {
         List<String> options = multi.getOptions();
         float pillX = 0.0F;
         float pillH = 11.0F * scale;
         float totalH = 8.0F * scale + pillH;
         for (String opt : options) {
            float optW = GuiTextRenderer.getWidth(GuiFont.SEMI, opt, 5.2F * scale) + 10.0F * scale;
            if (pillX + optW > width && pillX > 0.0F) {
               pillX = 0.0F;
               totalH += pillH + 3.0F * scale;
            }
            pillX += optW + 3.0F * scale;
         }
         return totalH;
      } else if (setting instanceof SliderSetting) {
         return 14.0F * scale;
      } else if (setting instanceof BooleanSetting) {
         return 11.5F * scale;
      } else if (setting instanceof TextSetting) {
         return 21.0F * scale;
      }
      return 11.5F * scale;
   }

   private void drawTip(float screenWidth, float screenHeight) {
      if (this.ctx.hoveredTooltip != null) {
         this.ctx.lastTooltip = this.ctx.hoveredTooltip;
      }

      float visibleProgress = this.ctx.animateValue("tip.show", this.ctx.hoveredTooltip != null ? 1.0F : 0.0F);
      if (this.ctx.hoveredTooltip != null) {
         visibleProgress = 1.0F;
      }

      if (visibleProgress > 0.01F && this.ctx.lastTooltip != null && !this.ctx.lastTooltip.isEmpty()) {
         float scale = this.ctx.scale;
         List<String> lines = GuiVisuals.wrapWords(GuiFont.INSTANCE, this.ctx.lastTooltip, 5.5F * scale, 220.0F * scale);
         float textHeight = lines.size() * 6.5F * scale;
         float tipW = 0.0F;
         for (String line : lines) {
            tipW = Math.max(tipW, GuiTextRenderer.getWidth(GuiFont.INSTANCE, line, 5.5F * scale));
         }
         tipW += 10.0F * scale;
         float tipH = textHeight + 6.0F * scale;
         float tipX = (screenWidth - tipW) / 2.0F;
         float tipY = screenHeight - tipH - 8.0F * scale;

         Render2D.pushAlpha(visibleProgress);
         Render2D.fillRoundedRectangle(tipX, tipY, tipW, tipH, 4.0F * scale, ClickGuiTheme.surface(0.06F, 0xEE));
         Render2D.drawOutline(tipX, tipY, tipW, tipH, 4.0F * scale, 0.5F * scale, ClickGuiTheme.accent(0x55));
         float curY = tipY + 3.0F * scale;
         for (String line : lines) {
            GuiTextRenderer.draw(
               GuiFont.INSTANCE,
               line,
               tipX + (tipW - GuiTextRenderer.getWidth(GuiFont.INSTANCE, line, 5.5F * scale)) / 2.0F,
               curY,
               5.5F * scale,
               0xFFD7DDD9
            );
            curY += 6.5F * scale;
         }
         Render2D.popAlpha();
      }
   }

   private void updateDrag(float mouseX, float mouseY) {
      if (this.ctx.draggedSlider != null || this.ctx.draggedColor != null) {
         float pressedProgress = GLFW.glfwGetMouseButton(MinecraftClient.getInstance().getWindow().getHandle(), 0) == 1 ? 1.0F : 0.0F;
         if (this.ctx.draggedSlider != null) {
            if (pressedProgress < 0.5F) {
               this.ctx.draggedSlider = null;
            } else if (this.ctx.sliderBounds.get(this.ctx.draggedSlider) != null) {
               this.ctx.draggedSlider.setNormalizedValue(
                  ClickGuiContext.clamp((mouseX - this.ctx.sliderBounds.get(this.ctx.draggedSlider)[0]) / this.ctx.sliderBounds.get(this.ctx.draggedSlider)[1], 0.0F, 1.0F)
               );
            }
         }

         if (this.ctx.draggedColor != null) {
            if (pressedProgress < 0.5F) {
               this.ctx.draggedColor = null;
            } else if (this.ctx.colorPickerBounds.get(this.ctx.draggedColor) != null) {
               float[] hsba = this.ctx.hsbaState(this.ctx.draggedColor);
               float[] pickerBounds = this.ctx.colorPickerBounds.get(this.ctx.draggedColor);
               if (this.ctx.colorDragMode == 0) {
                  hsba[1] = ClickGuiContext.clamp((mouseX - pickerBounds[0]) / pickerBounds[2], 0.0F, 1.0F);
                  hsba[2] = ClickGuiContext.clamp(1.0F - (mouseY - pickerBounds[1]) / pickerBounds[3], 0.0F, 1.0F);
               } else {
                  hsba[0] = ClickGuiContext.clamp((mouseX - pickerBounds[4]) / pickerBounds[6], 0.0F, 1.0F);
               }
               this.ctx.applyHsba(this.ctx.draggedColor, hsba);
            }
         }
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalScroll, double verticalScroll) {
      if (this.closing) {
         return true;
      }

      float mouseScale = coordinateScale();
      float scaledMouseX = (float)mouseX * mouseScale;
      float scaledMouseY = (float)mouseY * mouseScale;

      if (this.ctx.scrollBounds != null
         && GuiVisuals.containsPoint(scaledMouseX, scaledMouseY, this.ctx.scrollBounds[0], this.ctx.scrollBounds[1], this.ctx.scrollBounds[2], this.ctx.scrollBounds[3])) {
         this.ctx.popupScrollOffsets.put(
            this.ctx.scrollOwner,
            ClickGuiContext.clamp(
               this.ctx.popupScrollOffsets.getOrDefault(this.ctx.scrollOwner, 0.0F) - (float)verticalScroll * 17.86F * this.ctx.scale,
               0.0F,
               this.ctx.maxPopupScroll
            )
         );
         return true;
      }

      if (this.ctx.modalClickStart > 0) {
         return true;
      }

      if (GuiVisuals.containsPoint(scaledMouseX, scaledMouseY, this.moduleBounds[0], this.moduleBounds[1], this.moduleBounds[2], this.moduleBounds[3])) {
         this.moduleScroll.target = ClickGuiContext.clamp(
            this.moduleScroll.target - (float)verticalScroll * 22.0F * this.ctx.scale,
            0.0F,
            this.moduleScroll.maxScroll
         );
         return true;
      }

      if (GuiVisuals.containsPoint(scaledMouseX, scaledMouseY, this.settingsBounds[0], this.settingsBounds[1], this.settingsBounds[2], this.settingsBounds[3])) {
         this.settingsScroll.target = ClickGuiContext.clamp(
            this.settingsScroll.target - (float)verticalScroll * 22.0F * this.ctx.scale,
            0.0F,
            this.settingsScroll.maxScroll
         );
         return true;
      }

      return super.mouseScrolled(mouseX, mouseY, horizontalScroll, verticalScroll);
   }

   @Override
   public boolean mouseClicked(Click click, boolean doubled) {
      if (this.closing) {
         return true;
      }

      if (this.ctx.bindingSetting != null) {
         if (click.button() != 0) {
            this.ctx.bindingSetting.setBinding(InputBinding.mouse(click.button()));
         }
         this.ctx.bindingSetting = null;
         return true;
      } else {
         if (click.button() == 0 || click.button() == 1 || click.button() == 2) {
            float mouseScale = coordinateScale();
            float scaledMouseX = (float)click.x() * mouseScale;
            float scaledMouseY = (float)click.y() * mouseScale;

            if (click.button() == 0) {
               this.ctx.focusedText = null;
               this.ctx.inputHandler = null;
               this.ctx.bindingModule = null;
               this.searchFocused = false;
            }

            for (int regionIndex = this.ctx.clickRegions.size() - 1; regionIndex >= this.ctx.modalClickStart; regionIndex--) {
               ClickGuiContext.ClickRegion region = this.ctx.clickRegions.get(regionIndex);
               if (GuiVisuals.containsPoint(scaledMouseX, scaledMouseY, region.x(), region.y(), region.getWidth(), region.getHeight())) {
                  if (click.button() == 0) {
                     region.action().run();
                     return true;
                  }
                  if (click.button() == 1 && region.rightAction() != null) {
                     region.rightAction().run();
                     return true;
                  }
                  if (click.button() == 2 && region.middleAction() != null) {
                     region.middleAction().run();
                     return true;
                  }
               }
            }

            if (this.ctx.modalClickStart > 0) {
               return true;
            }
         }

         return super.mouseClicked(click, doubled);
      }
   }

   @Override
   public boolean charTyped(CharInput charInput) {
      if (this.ctx.inputHandler != null) {
         if (charInput.codepoint() >= 32) {
            this.ctx.inputHandler.charTyped((char)charInput.codepoint());
         }
         return true;
      } else if (this.ctx.focusedText != null) {
         if (charInput.codepoint() >= 32) {
            this.ctx.focusedText.insertText(String.valueOf((char)charInput.codepoint()));
         }
         return true;
      } else if (this.searchFocused) {
         if (charInput.codepoint() >= 32) {
            this.search.insertText(String.valueOf((char)charInput.codepoint()));
            this.ctx.searchText = this.search.getText().toLowerCase(Locale.ROOT);
            this.lastSearchEditTime = System.nanoTime() / 1000000L;
            this.moduleScroll.target = 0.0F;
         }
         return true;
      } else {
         return super.charTyped(charInput);
      }
   }

   private int searchWordHop(int direction) {
      String text = this.search.getText();
      int cursor = this.search.getCursor();
      if (direction < 0) {
         while (cursor > 0 && text.charAt(cursor - 1) == ' ') {
            cursor--;
         }
         while (cursor > 0 && text.charAt(cursor - 1) != ' ') {
            cursor--;
         }
         return cursor - this.search.getCursor();
      } else {
         while (cursor < text.length() && text.charAt(cursor) != ' ') {
            cursor++;
         }
         while (cursor < text.length() && text.charAt(cursor) == ' ') {
            cursor++;
         }
         return cursor - this.search.getCursor();
      }
   }

   @Override
   public boolean keyPressed(KeyInput keyInput) {
      if (this.closing) {
         return true;
      }

      int keyCode = keyInput.key();
      if (this.ctx.bindingModule != null) {
         this.ctx.bindingModule.withKeyCode(keyCode != 256 && keyCode != 261 && keyCode != 344 ? keyCode : -1);
         this.ctx.bindingModule = null;
         return true;
      }

      if (this.ctx.bindingSetting != null) {
         this.ctx.bindingSetting.withKeyCode(keyCode != 256 && keyCode != 261 ? keyCode : -1);
         this.ctx.bindingSetting = null;
         return true;
      }

      if (this.ctx.inputHandler != null) {
         if (keyCode == 256) {
            this.ctx.inputHandler = null;
         } else {
            this.ctx.inputHandler.keyPressed(keyCode);
         }
         return true;
      } else if (this.ctx.focusedText != null) {
         if ((keyInput.modifiers() & 2) != 0 && keyCode == 65) {
            this.ctx.focusedText.selectAll();
         } else if ((keyInput.modifiers() & 2) != 0 && keyCode == 67 && this.ctx.focusedText.hasSelection()) {
            MinecraftClient.getInstance().keyboard.setClipboard(this.ctx.focusedText.getSelectedText());
         } else if ((keyInput.modifiers() & 2) != 0 && keyCode == 88 && this.ctx.focusedText.hasSelection()) {
            MinecraftClient.getInstance().keyboard.setClipboard(this.ctx.focusedText.getSelectedText());
            this.ctx.focusedText.backspace();
         } else if ((keyInput.modifiers() & 2) != 0 && keyCode == 86) {
            this.ctx.focusedText.insertText(MinecraftClient.getInstance().keyboard.getClipboard());
         } else if (keyCode == 259) {
            this.ctx.focusedText.backspace();
         } else if (keyCode == 261) {
            this.ctx.focusedText.deleteForward();
         } else if (keyCode == 263) {
            this.ctx.focusedText.moveCursor(-1, (keyInput.modifiers() & 1) != 0);
         } else if (keyCode == 262) {
            this.ctx.focusedText.moveCursor(1, (keyInput.modifiers() & 1) != 0);
         } else if (keyCode == 268) {
            this.ctx.focusedText.moveToStart((keyInput.modifiers() & 1) != 0);
         } else if (keyCode == 269) {
            this.ctx.focusedText.moveToEnd((keyInput.modifiers() & 1) != 0);
         } else if (keyCode == 257 || keyCode == 256) {
            this.ctx.focusedText = null;
         }
         return true;
      } else {
         if (this.ctx.popupOwner != null && keyCode == 256) {
            this.ctx.popupOwner = null;
            return true;
         }

         if ((keyInput.modifiers() & 2) != 0 && keyCode == 70) {
            this.searchFocused = !this.searchFocused;
            if (!this.searchFocused) {
               this.search.setValue("");
               this.ctx.searchText = "";
            }
            this.lastSearchEditTime = System.nanoTime() / 1000000L;
            return true;
         } else if (!this.searchFocused) {
            if (keyCode != 344 && keyCode != 256) {
               return super.keyPressed(keyInput);
            }
            this.close();
            return true;
         } else if (keyCode != 256 && keyCode != 257) {
            boolean controlPressed = (keyInput.modifiers() & 2) != 0;
            boolean shiftPressed = (keyInput.modifiers() & 1) != 0;
            String previousSearchText = this.search.getText();
            if (controlPressed && keyCode == 65) {
               this.search.selectAll();
            } else if (controlPressed && keyCode == 67 && this.search.hasSelection()) {
               MinecraftClient.getInstance().keyboard.setClipboard(this.search.getSelectedText());
            } else if (controlPressed && keyCode == 88 && this.search.hasSelection()) {
               MinecraftClient.getInstance().keyboard.setClipboard(this.search.getSelectedText());
               this.search.backspace();
            } else if (controlPressed && keyCode == 86) {
               this.search.insertText(MinecraftClient.getInstance().keyboard.getClipboard());
            } else if (keyCode == 259) {
               if (controlPressed && !this.search.hasSelection()) {
                  this.search.moveCursor(this.searchWordHop(-1), true);
               }
               this.search.backspace();
            } else if (keyCode == 261) {
               if (controlPressed && !this.search.hasSelection()) {
                  this.search.moveCursor(this.searchWordHop(1), true);
               }
               this.search.deleteForward();
            } else if (keyCode == 263) {
               this.search.moveCursor(controlPressed ? this.searchWordHop(-1) : -1, shiftPressed);
            } else if (keyCode == 262) {
               this.search.moveCursor(controlPressed ? this.searchWordHop(1) : 1, shiftPressed);
            } else if (keyCode == 268) {
               this.search.moveToStart(shiftPressed);
            } else if (keyCode == 269) {
               this.search.moveToEnd(shiftPressed);
            }

            if (!previousSearchText.equals(this.search.getText())) {
               this.ctx.searchText = this.search.getText().toLowerCase(Locale.ROOT);
               this.lastSearchEditTime = System.nanoTime() / 1000000L;
               this.moduleScroll.target = 0.0F;
            }
            return true;
         } else {
            this.searchFocused = false;
            this.search.setValue("");
            this.ctx.searchText = "";
            return true;
         }
      }
   }

   @Override
   public boolean renderClosing(DrawContext context) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (this.closingOverlayActive && client.player != null) {
         this.render(context, (int)client.mouse.getScaledX(client.getWindow()), (int)client.mouse.getScaledY(client.getWindow()), 0.0F);
         if (this.visibilityProgress <= 0.003F) {
            this.finishOverlay();
            return false;
         } else {
            return true;
         }
      } else {
         this.finishOverlay();
         return false;
      }
   }
}
