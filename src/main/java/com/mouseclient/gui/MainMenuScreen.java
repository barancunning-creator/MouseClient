package com.mouseclient.gui;

import com.mouseclient.internal.GuiScale;
import com.mouseclient.modules.impl.render.HudSettingsModule;
import com.mouseclient.util.time.AccountPanel;
import com.mouseclient.util.time.ClickGuiContext;
import com.mouseclient.util.time.ClickGuiTheme;
import com.mouseclient.util.time.CosmeticMenuPanel;
import com.mouseclient.util.time.GuiFont;
import com.mouseclient.util.time.GuiTextRenderer;
import com.mouseclient.util.time.GuiVisuals;
import com.mouseclient.util.time.ScalarAnimation;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

public class MainMenuScreen extends Screen {
   private final AccountPanel accountsPanel = new AccountPanel();
   private final CosmeticMenuPanel cosmeticsPanel = new CosmeticMenuPanel();
   private boolean accountsOpen = false;
   private boolean cosmeticsOpen = false;
   private boolean wallpapersOpen = false;
   private final ScalarAnimation accountsAnimation = new ScalarAnimation(260L);
   private final ScalarAnimation cosmeticsAnimation = new ScalarAnimation(260L);
   private final ScalarAnimation wallpapersAnimation = new ScalarAnimation(260L);
   private final SmoothScrollState wallpapersScroll = new SmoothScrollState();
   private final Map<String, ScalarAnimation> hoverAnimations = new HashMap<>();
   private final List<MainMenuScreen.MenuActionRegion> actionRegions = new ArrayList<>();
   private String hoveredTooltip;
   private float lastFrameTime;

   public MainMenuScreen(boolean showWelcome) {
      super(Text.literal("маусклиент"));
   }

   public MainMenuScreen() {
      this(false);
   }

   @Override
   public boolean shouldCloseOnEsc() {
      return false;
   }

   @Override
   public boolean shouldPause() {
      return false;
   }

   @Override
   protected void init() {
      super.init();
      WallpaperManager.init();
      this.accountsOpen = false;
      this.cosmeticsOpen = false;
      this.wallpapersOpen = false;
      this.accountsAnimation.reset(0.0);
      this.cosmeticsAnimation.reset(0.0);
      this.wallpapersAnimation.reset(0.0);
      this.accountsPanel.blurInput();
      this.cosmeticsPanel.clearInputState();
   }

   private static float coordinateScale() {
      return GuiScale.getWindowScale() / GuiScale.getBaseScale();
   }

   @Override
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      float coordinateMultiplier = coordinateScale();
      float scaledMouseX = mouseX * coordinateMultiplier;
      float scaledMouseY = mouseY * coordinateMultiplier;
      float screenWidth = this.width * coordinateMultiplier;
      float screenHeight = this.height * coordinateMultiplier;
      float sceneScale = Math.min(screenWidth / 960.0F, screenHeight / 540.0F);

      float deltaSeconds = Math.min(0.05F, (float)(System.nanoTime() / 1000000L - (long)this.lastFrameTime) / 1000.0F);
      this.lastFrameTime = (float)(System.nanoTime() / 1000000L);

      this.actionRegions.clear();
      this.hoveredTooltip = null;

      HudSettingsModule.getInstance().syncThemeColor();
      Render2D.begin(context);

      // 1. Draw Background Wallpaper
      String currentWp = WallpaperManager.getCurrentWallpaper();
      GuiVisuals.drawCoverBackground(currentWp, screenWidth, screenHeight, 1.0F);
      // Subtle dark tint for contrast
      Render2D.fillRoundedRectangle(0.0F, 0.0F, screenWidth, screenHeight, 0.0F, 0x33000000);

      this.accountsAnimation.setTarget(this.accountsOpen ? 1.0 : 0.0);
      this.cosmeticsAnimation.setTarget(this.cosmeticsOpen ? 1.0 : 0.0);
      this.wallpapersAnimation.setTarget(this.wallpapersOpen ? 1.0 : 0.0);

      float accountsProgress = (float)this.accountsAnimation.value();
      float cosmeticsProgress = (float)this.cosmeticsAnimation.value();
      float wallpapersProgress = (float)this.wallpapersAnimation.value();
      boolean anyModalOpen = this.accountsOpen || this.cosmeticsOpen || this.wallpapersOpen
         || accountsProgress > 0.05F || cosmeticsProgress > 0.05F || wallpapersProgress > 0.05F;

      // 2. Center Above: Big Clock & Shimmering Welcome Text
      float clockSize = 42.0F * sceneScale;
      String timeText = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
      float clockW = GuiTextRenderer.getWidth(GuiFont.SEMI, timeText, clockSize);
      float clockX = (screenWidth - clockW) / 2.0F;
      float clockY = screenHeight / 2.0F - 72.0F * sceneScale;

      // Clock
      GuiTextRenderer.draw(GuiFont.SEMI, timeText, clockX, clockY, clockSize, 0xFFFFFFFF);

      // Shimmering text: "Добро пожаловать, маус юзер!"
      String welcomeText = "Добро пожаловать, маус юзер!";
      float welcomeSize = 7.8F * sceneScale;
      float welcomeW = GuiTextRenderer.getWidth(GuiFont.SEMI, welcomeText, welcomeSize);
      float welcomeX = (screenWidth - welcomeW) / 2.0F;
      float welcomeY = clockY + clockSize + 10.0F * sceneScale;

      float welcomePulse = (float)(Math.sin(System.currentTimeMillis() / 700.0) * 0.5 + 0.5);
      int welcomeColor = GuiVisuals.lerpColor(ClickGuiTheme.accentOpaque(), 0xFFFFFFFF, 0.28F + 0.42F * welcomePulse);
      GuiTextRenderer.draw(GuiFont.SEMI, welcomeText, welcomeX, welcomeY, welcomeSize, welcomeColor);

      MinecraftClient client = this.client != null ? this.client : MinecraftClient.getInstance();
      float dockH = 18.0F * sceneScale;
      float dockGap = 4.0F * sceneScale;
      String[] labels = {"Обои", "Одиночная", "Сеть", "Настройки", "Выход", "Аккаунты"};
      String[] icons = {
         GuiVisuals.PATTERN_TEXTURE,
         "assets/mouseclient/textures/gui/mainmenu/icons/singleplayer.png",
         "assets/mouseclient/textures/gui/mainmenu/icons/grass.png",
         "assets/mouseclient/textures/gui/mainmenu/icons/settings.png",
         "assets/mouseclient/textures/gui/mainmenu/icons/exit.png",
         "assets/mouseclient/textures/gui/mainmenu/icons/accounts.png"
      };
      String[] keys = {"dockWallpapers", "dockSingle", "dockMulti", "dockSettings", "dockExit", "dockAccounts"};
      Runnable[] actions = {
         this::toggleWallpapers,
         () -> client.setScreen(new SelectWorldScreen(this)),
         () -> client.setScreen(new MultiplayerScreen(this)),
         () -> client.setScreen(new OptionsScreen(this, client.options)),
         client::scheduleStop,
         this::toggleAccounts
      };
      float textSize = 5.5F * sceneScale;
      float iconSize = 6.5F * sceneScale;
      float[] widths = new float[labels.length];
      float dockW = dockGap * (labels.length - 1);
      for (int index = 0; index < labels.length; index++) {
         widths[index] = 8.0F * sceneScale + iconSize + 3.5F * sceneScale + GuiTextRenderer.getWidth(GuiFont.SEMI, labels[index], textSize);
         dockW += widths[index];
      }

      float dockX = (screenWidth - dockW) / 2.0F;
      float dockY = screenHeight - 22.0F * sceneScale - dockH;
      float cursor = dockX;
      for (int index = 0; index < labels.length; index++) {
         this.drawDockButton(cursor, dockY, widths[index], dockH, sceneScale, icons[index], labels[index], keys[index], scaledMouseX, scaledMouseY, anyModalOpen, actions[index]);
         cursor += widths[index] + dockGap;
      }

      // 6. Draw Modals
      // A) Wallpaper Selector Modal
      if (wallpapersProgress > 0.001F) {
         Render2D.pushAlpha(wallpapersProgress);
         this.renderWallpapersModal(context, screenWidth, screenHeight, sceneScale, scaledMouseX, scaledMouseY, wallpapersProgress, deltaSeconds);
         Render2D.popAlpha();
      }

      // B) Cosmetics Modal
      if (cosmeticsProgress > 0.001F) {
         Render2D.pushAlpha(cosmeticsProgress);
         float verticalOffset = (screenHeight - 540.0F * sceneScale) / 2.0F;
         this.cosmeticsPanel.render(context, (screenWidth - 329.5F * sceneScale) / 2.0F, sceneScale, verticalOffset, screenWidth, screenHeight, scaledMouseX, scaledMouseY);
         Render2D.popAlpha();
      }

      // C) Accounts Modal
      if (accountsProgress > 0.001F) {
         Render2D.pushAlpha(accountsProgress);
         Render2D.fillRoundedRectangle(0.0F, 0.0F, screenWidth, screenHeight, 0.0F, 0x99000000);
         float verticalOffset = (screenHeight - 540.0F * sceneScale) / 2.0F;
         float panelX = (screenWidth - 329.5F * sceneScale) / 2.0F;
         float cardX = panelX + 32.0F * sceneScale;
         float cardY = verticalOffset + 86.0F * sceneScale + (1.0F - accountsProgress) * 16.0F * sceneScale;
         float cardW = 266.0F * sceneScale;
         float cardH = 358.0F * sceneScale;
         Render2D.drawSharedBlur(cardX + 0.5F * sceneScale, cardY + 0.5F * sceneScale, cardW - 1.0F * sceneScale, cardH - 1.0F * sceneScale, 14.0F * sceneScale, 24.0F, 1.0F, -1);
         Render2D.fillRoundedRectangle(cardX, cardY, cardW, cardH, 14.0F * sceneScale, ClickGuiTheme.surface(0.07F, 0xF4));
         Render2D.drawOutline(cardX, cardY, cardW, cardH, 14.0F * sceneScale, 0.5F * sceneScale, ClickGuiTheme.accent(0x66));
         this.accountsPanel.render(context, panelX, sceneScale, verticalOffset + (1.0F - accountsProgress) * 16.0F * sceneScale, scaledMouseX, scaledMouseY);
         Render2D.popAlpha();
      }

      // 7. Hovered Tooltip
      if (this.hoveredTooltip != null && !anyModalOpen) {
         float tipW = GuiTextRenderer.getWidth(GuiFont.SEMI, this.hoveredTooltip, 6.0F * sceneScale) + 12.0F * sceneScale;
         float tipH = 14.0F * sceneScale;
         float tipX = scaledMouseX - tipW / 2.0F;
         float tipY = scaledMouseY - tipH - 8.0F * sceneScale;
         Render2D.fillRoundedRectangle(tipX, tipY, tipW, tipH, 4.0F * sceneScale, 0xF20B140F);
         Render2D.drawOutline(tipX, tipY, tipW, tipH, 4.0F * sceneScale, 0.5F * sceneScale, ClickGuiTheme.accent(0x66));
         GuiTextRenderer.draw(
            GuiFont.SEMI, this.hoveredTooltip,
            tipX + (tipW - GuiTextRenderer.getWidth(GuiFont.SEMI, this.hoveredTooltip, 6.0F * sceneScale)) / 2.0F,
            tipY + (tipH - 6.0F * sceneScale) / 2.0F,
            6.0F * sceneScale, 0xFFFFFFFF
         );
      }

      Render2D.end();

      if (accountsProgress > 0.5F) {
         float verticalOffset = (screenHeight - 540.0F * sceneScale) / 2.0F + (1.0F - accountsProgress) * 16.0F * sceneScale;
         this.accountsPanel.renderSkins(context, (screenWidth - 329.5F * sceneScale) / 2.0F, sceneScale, verticalOffset);
      }
   }

   private void drawCircleButton(
      float x, float y, float diameter, float scale,
      String iconPath, float iconSize,
      String tooltip, float mouseX, float mouseY, boolean anyModalOpen,
      Runnable action
   ) {
      boolean hovered = GuiVisuals.containsPoint(mouseX, mouseY, x, y, diameter, diameter);
      float hover = this.hoverProgress("circle_" + tooltip, hovered && !anyModalOpen);

      float centerX = x + diameter / 2.0F;
      float centerY = y + diameter / 2.0F;
      float radius = diameter / 2.0F;

      int circleBg = GuiVisuals.lerpColor(ClickGuiTheme.surface(0.08F, 0xF0), ClickGuiTheme.surface(0.2F, 0xF2), hover);
      int circleOutline = GuiVisuals.lerpColor(ClickGuiTheme.accent(0x44), ClickGuiTheme.accentOpaque(), hover);

      Render2D.fillCircle(centerX, centerY, radius, circleBg);
      Render2D.strokeCircle(centerX, centerY, radius, 0.5F * scale, circleOutline);

      int iconColor = GuiVisuals.lerpColor(0xFFE6EBE8, ClickGuiTheme.accentOpaque(), hover);
      GuiVisuals.drawCenteredImage(iconPath, centerX, centerY, iconSize, iconSize, iconColor);

      if (hovered && !anyModalOpen) {
         this.hoveredTooltip = tooltip;
         this.actionRegions.add(new MenuActionRegion(x, y, diameter, diameter, action));
      } else if (!anyModalOpen) {
         this.actionRegions.add(new MenuActionRegion(x, y, diameter, diameter, action));
      }
   }

   private void drawDockButton(
      float x, float y, float width, float height, float scale,
      String icon, String label, String key,
      float mouseX, float mouseY, boolean blocked, Runnable action
   ) {
      boolean hovered = GuiVisuals.containsPoint(mouseX, mouseY, x, y, width, height);
      float hover = this.hoverProgress(key, hovered && !blocked);
      float lift = hover * 1.0F * scale;
      float drawY = y - lift;
      Render2D.fillRoundedRectangle(
         x, drawY, width, height, height / 2.0F,
         GuiVisuals.lerpColor(ClickGuiTheme.surface(0.1F, 0xE6), ClickGuiTheme.surface(0.22F, 0xF6), hover)
      );
      Render2D.drawOutline(
         x, drawY, width, height, height / 2.0F, 0.5F * scale,
         GuiVisuals.lerpColor(ClickGuiTheme.accent(0x28), ClickGuiTheme.accentOpaque(), hover)
      );
      float iconSize = 6.5F * scale;
      float textSize = 5.5F * scale;
      float textW = GuiTextRenderer.getWidth(GuiFont.SEMI, label, textSize);
      float contentW = iconSize + 3.5F * scale + textW;
      float contentX = x + (width - contentW) / 2.0F;
      GuiVisuals.drawCenteredImage(icon, contentX + iconSize / 2.0F, drawY + height / 2.0F, iconSize, iconSize, GuiVisuals.lerpColor(0xFFE6EBE8, ClickGuiTheme.accentOpaque(), hover));
      GuiTextRenderer.draw(GuiFont.SEMI, label, contentX + iconSize + 3.5F * scale, drawY + (height - textSize) / 2.0F - 0.5F * scale, textSize, GuiVisuals.lerpColor(0xFFD7DDD9, 0xFFFFFFFF, hover));
      if (!blocked) {
         this.actionRegions.add(new MenuActionRegion(x, y - 2.0F * scale, width, height + 3.0F * scale, action));
      }
   }

   private void renderWallpapersModal(
      DrawContext context, float screenWidth, float screenHeight, float sceneScale,
      float mouseX, float mouseY, float progress, float deltaSeconds
   ) {
      // Backdrop dark blur overlay
      Render2D.fillRoundedRectangle(0.0F, 0.0F, screenWidth, screenHeight, 0.0F, 0x88000000);

      float modalW = 420.0F * sceneScale;
      float modalH = 270.0F * sceneScale;
      float modalX = (screenWidth - modalW) / 2.0F;
      float modalY = (screenHeight - modalH) / 2.0F + (1.0F - progress) * 15.0F * sceneScale;

      Render2D.drawSharedBlur(modalX + 0.5F * sceneScale, modalY + 0.5F * sceneScale, modalW - 1.0F * sceneScale, modalH - 1.0F * sceneScale, 12.0F * sceneScale, 25.0F, 1.0F, -1);
      Render2D.fillRoundedRectangle(modalX, modalY, modalW, modalH, 12.0F * sceneScale, ClickGuiTheme.surface(0.055F, 0xF6));
      Render2D.drawOutline(modalX, modalY, modalW, modalH, 12.0F * sceneScale, 0.5F * sceneScale, ClickGuiTheme.accent(0x66));

      GuiTextRenderer.draw(GuiFont.SEMI, "Выбор обоев", modalX + 16.0F * sceneScale, modalY + 14.0F * sceneScale, 8.5F * sceneScale, 0xFFFFFFFF);

      // Open Folder Button
      float folderBtnW = 58.0F * sceneScale;
      float folderBtnH = 15.0F * sceneScale;
      float folderBtnX = modalX + modalW - folderBtnW - 28.0F * sceneScale;
      float folderBtnY = modalY + 11.5F * sceneScale;
      boolean folderHover = GuiVisuals.containsPoint(mouseX, mouseY, folderBtnX, folderBtnY, folderBtnW, folderBtnH);

      Render2D.fillRoundedRectangle(folderBtnX, folderBtnY, folderBtnW, folderBtnH, 4.0F * sceneScale, folderHover ? ClickGuiTheme.surface(0.18F, 0xFF) : ClickGuiTheme.surface(0.09F, 0xFF));
      Render2D.drawOutline(folderBtnX, folderBtnY, folderBtnW, folderBtnH, 4.0F * sceneScale, 0.5F * sceneScale, folderHover ? ClickGuiTheme.accentOpaque() : ClickGuiTheme.accent(0x40));
      GuiTextRenderer.draw(
         GuiFont.SEMI, "Папка",
         folderBtnX + (folderBtnW - GuiTextRenderer.getWidth(GuiFont.SEMI, "Папка", 5.5F * sceneScale)) / 2.0F,
         folderBtnY + (folderBtnH - 5.5F * sceneScale) / 2.0F,
         5.5F * sceneScale, folderHover ? ClickGuiTheme.accentOpaque() : 0xFFD7DDD9
      );
      this.actionRegions.add(new MenuActionRegion(folderBtnX, folderBtnY, folderBtnW, folderBtnH, WallpaperManager::openFolder));

      float closeX = modalX + modalW - 22.0F * sceneScale;
      float closeY = modalY + 12.5F * sceneScale;
      boolean closeHover = GuiVisuals.containsPoint(mouseX, mouseY, closeX - 2.0F * sceneScale, closeY - 2.0F * sceneScale, 14.0F * sceneScale, 14.0F * sceneScale);
      GuiTextRenderer.draw(GuiFont.SEMI, "×", closeX, closeY, 7.5F * sceneScale, closeHover ? 0xFFFFFFFF : 0xFF8B948E);
      this.actionRegions.add(new MenuActionRegion(closeX - 2.0F * sceneScale, closeY - 2.0F * sceneScale, 14.0F * sceneScale, 14.0F * sceneScale, this::toggleWallpapers));

      // Content Viewport: 3 columns of wallpaper cards
      float vpX = modalX + 14.0F * sceneScale;
      float vpY = modalY + 38.0F * sceneScale;
      float vpW = modalW - 28.0F * sceneScale;
      float vpH = modalH - 50.0F * sceneScale;

      this.wallpapersScroll.update(deltaSeconds);

      Render2D.pushScissor(context, vpX, vpY, vpW, vpH);

      List<WallpaperManager.WallpaperEntry> list = WallpaperManager.getWallpapers();
      int cols = 3;
      float gap = 10.0F * sceneScale;
      float cardW = (vpW - (cols - 1) * gap) / (float)cols;
      float previewH = 50.0F * sceneScale;
      float cardH = previewH + 18.0F * sceneScale;

      String currentWp = WallpaperManager.getCurrentWallpaper();

      for (int i = 0; i < list.size(); i++) {
         WallpaperManager.WallpaperEntry entry = list.get(i);
         int col = i % cols;
         int row = i / cols;
         float cardX = vpX + col * (cardW + gap);
         float cardY = vpY + row * (cardH + gap) - this.wallpapersScroll.getScroll();

         if (cardY + cardH >= vpY - 4.0F && cardY <= vpY + vpH + 4.0F) {
            boolean isSelected = entry.texturePath().equals(currentWp);
            boolean isHovered = GuiVisuals.containsPoint(mouseX, mouseY, cardX, cardY, cardW, cardH)
               && GuiVisuals.containsPoint(mouseX, mouseY, vpX, vpY, vpW, vpH);

            // Preview Thumbnail
            Render2D.drawRoundedImage(entry.texturePath(), cardX, cardY, cardW, previewH, 4.5F * sceneScale, 0xFFFFFFFF);

            // Outline around thumbnail
            if (isSelected) {
               Render2D.drawOutline(cardX, cardY, cardW, previewH, 4.5F * sceneScale, 1.0F * sceneScale, ClickGuiTheme.accentOpaque());
               float badge = 9.0F * sceneScale;
               float badgeX = cardX + cardW - badge - 3.0F * sceneScale;
               float badgeY = cardY + 3.0F * sceneScale;
               Render2D.fillRoundedRectangle(badgeX, badgeY, badge, badge, badge / 2.0F, ClickGuiTheme.accentOpaque());
               Render2D.drawRoundedImage(ClickGuiTheme.CHECK_ICON, badgeX + 2.0F * sceneScale, badgeY + 2.0F * sceneScale, badge - 4.0F * sceneScale, badge - 4.0F * sceneScale, 0.0F, ClickGuiTheme.onAccent());
            } else if (isHovered) {
               Render2D.drawOutline(cardX, cardY, cardW, previewH, 4.5F * sceneScale, 0.5F * sceneScale, ClickGuiTheme.accent(0xAA));
            } else {
               Render2D.drawOutline(cardX, cardY, cardW, previewH, 4.5F * sceneScale, 0.5F * sceneScale, 0x33FFFFFF);
            }

            // Name below thumbnail
            int labelColor = isSelected ? ClickGuiTheme.accentOpaque() : (isHovered ? 0xFFFFFFFF : 0xFFC5CDC8);
            String title = entry.displayName();
            if (entry.isCustom()) {
               title = "★ " + title;
            }
            Render2D.pushScissor(context, cardX, cardY + previewH, cardW, cardH - previewH);
            GuiTextRenderer.draw(GuiFont.SEMI, title, cardX + 2.0F * sceneScale, cardY + previewH + 4.0F * sceneScale, 5.6F * sceneScale, labelColor);
            Render2D.popScissor(context);

            if (isHovered) {
               this.actionRegions.add(new MenuActionRegion(cardX, cardY, cardW, cardH, () -> WallpaperManager.setCurrentWallpaper(entry.texturePath())));
            }
         }
      }

      Render2D.popScissor(context);

      int totalRows = (list.size() + cols - 1) / cols;
      float totalContentH = totalRows * (cardH + gap);
      this.wallpapersScroll.maxScroll = Math.max(0.0F, totalContentH - vpH + 10.0F * sceneScale);
   }

   private float hoverProgress(String key, boolean hovered) {
      ScalarAnimation animation = this.hoverAnimations.computeIfAbsent(key, k -> new ScalarAnimation(150L));
      animation.setTarget(hovered ? 1.0 : 0.0);
      return (float)animation.value();
   }

   private void toggleAccounts() {
      this.accountsOpen = !this.accountsOpen;
      if (this.accountsOpen) {
         this.cosmeticsOpen = false;
         this.wallpapersOpen = false;
         this.accountsPanel.open(this::toggleAccounts);
      } else {
         this.accountsPanel.blurInput();
      }
   }

   private void toggleCosmetics() {
      this.cosmeticsOpen = !this.cosmeticsOpen;
      if (this.cosmeticsOpen) {
         this.accountsOpen = false;
         this.wallpapersOpen = false;
         this.cosmeticsPanel.open(this::toggleCosmetics);
      } else {
         this.cosmeticsPanel.clearInputState();
      }
   }

   private void toggleWallpapers() {
      this.wallpapersOpen = !this.wallpapersOpen;
      if (this.wallpapersOpen) {
         this.accountsOpen = false;
         this.cosmeticsOpen = false;
         WallpaperManager.refreshWallpapers();
         this.wallpapersScroll.target = 0.0F;
         this.wallpapersScroll.current = 0.0F;
      }
   }

   @Override
   public boolean mouseClicked(Click click, boolean doubleClick) {
      if (click.button() == 0) {
         float coordinateMultiplier = coordinateScale();
         float scaledMouseX = (float)click.x() * coordinateMultiplier;
         float scaledMouseY = (float)click.y() * coordinateMultiplier;

         if (this.accountsOpen && this.accountsAnimation.value() > 0.6 && this.accountsPanel.mouseClicked(scaledMouseX, scaledMouseY)) {
            return true;
         }

         if (this.cosmeticsOpen && this.cosmeticsAnimation.value() > 0.6 && this.cosmeticsPanel.mouseClicked(scaledMouseX, scaledMouseY)) {
            return true;
         }

         for (int i = this.actionRegions.size() - 1; i >= 0; i--) {
            MenuActionRegion r = this.actionRegions.get(i);
            if (GuiVisuals.containsPoint(scaledMouseX, scaledMouseY, r.x(), r.y(), r.getWidth(), r.getHeight())) {
               r.action().run();
               return true;
            }
         }
      }

      return super.mouseClicked(click, doubleClick);
   }

   @Override
   public boolean mouseReleased(Click click) {
      return this.cosmeticsOpen && click.button() == 0 && this.cosmeticsPanel.mouseReleased() ? true : super.mouseReleased(click);
   }

   @Override
   public boolean mouseDragged(Click click, double deltaX, double deltaY) {
      return this.cosmeticsOpen && click.button() == 0 && this.cosmeticsPanel.mouseDragged((float)click.x() * coordinateScale(), (float)click.y() * coordinateScale())
         ? true
         : super.mouseDragged(click, deltaX, deltaY);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalScroll, double verticalScroll) {
      float coordinateMultiplier = coordinateScale();
      float scaledMouseX = (float)mouseX * coordinateMultiplier;
      float scaledMouseY = (float)mouseY * coordinateMultiplier;
      float sceneScale = Math.min((this.width * coordinateMultiplier) / 960.0F, (this.height * coordinateMultiplier) / 540.0F);

      if (this.accountsOpen && this.accountsPanel.mouseScrolled(scaledMouseX, scaledMouseY, (float)verticalScroll)) {
         return true;
      }

      if (this.cosmeticsOpen && this.cosmeticsPanel.mouseScrolled(scaledMouseX, scaledMouseY, (float)verticalScroll)) {
         return true;
      }

      if (this.wallpapersOpen) {
         this.wallpapersScroll.target = ClickGuiContext.clamp(
            this.wallpapersScroll.target - (float)verticalScroll * 25.0F * sceneScale,
            0.0F,
            this.wallpapersScroll.maxScroll
         );
         return true;
      }

      return super.mouseScrolled(mouseX, mouseY, horizontalScroll, verticalScroll);
   }

   @Override
   public boolean keyPressed(KeyInput keyInput) {
      if (keyInput.key() == 256) {
         if (this.wallpapersOpen) {
            this.toggleWallpapers();
            return true;
         }
         if (this.accountsOpen) {
            this.toggleAccounts();
            return true;
         }
         if (this.cosmeticsOpen) {
            this.toggleCosmetics();
            return true;
         }
      }

      if (this.accountsOpen && this.accountsPanel.keyPressed(keyInput.key())) {
         return true;
      }

      if (this.cosmeticsOpen && this.cosmeticsPanel.keyPressed(keyInput.key())) {
         return true;
      }

      return super.keyPressed(keyInput);
   }

   @Override
   public boolean charTyped(CharInput input) {
      if (this.accountsOpen && this.accountsPanel.charTyped(input.codepoint())) {
         return true;
      }
      if (this.cosmeticsOpen && this.cosmeticsPanel.charTyped(input.codepoint())) {
         return true;
      }
      return super.charTyped(input);
   }

   private record MenuActionRegion(float x, float y, float width, float height, Runnable action) {
      public float getHeight() {
         return this.height;
      }

      public float getWidth() {
         return this.width;
      }
   }
}
