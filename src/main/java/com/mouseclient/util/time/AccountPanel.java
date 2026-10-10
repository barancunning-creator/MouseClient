package com.mouseclient.util.time;

import com.mouseclient.gui.Render2D;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.render.AccountSkinRenderer;
import java.util.ArrayList;
import java.util.HashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

public final class AccountPanel {
   private AltAccountManager accounts = AltAccountManager.getInstance();
   private AccountPanel.AccountInputMode inputMode = AccountPanel.AccountInputMode.NONE;
   private String searchQuery = "";
   private String inputText = "";
   private boolean selectAll;
   private NicknameGenerator nicknameGenerator = new NicknameGenerator();
   private HashMap<String, ScalarAnimation> stateAnimations = new HashMap<>();
   private float scrollTarget;
   private float scrollPosition;
   private float maxScroll;
   private long lastFrameMillis;
   private long openedAtMillis;
   private long selectionChangedAtMillis;
   private String lastSelectedUsername;
   private Runnable onBack;
   private float listTop;
   private float listBottom;
   private float scale;
   private ArrayList<AccountPanel.AccountActionRegion> actionRegions;
   private ArrayList<AltAccount> visibleAccounts;

   public void render(DrawContext context, float panelX, float scale, float top, float mouseX, float mouseY) {
      this.scale = scale;
      float deltaSeconds = Math.min((0.05F), (float)(System.nanoTime() / (1000000L) - this.lastFrameMillis) / (1000.0F));
      this.lastFrameMillis = System.nanoTime() / (1000000L);
      this.actionRegions.clear();
      AltAccount activeAccount = this.accounts.activeAccount();
      String activeUsername = activeAccount == null ? null : activeAccount.username();
      if (activeUsername != null && !activeUsername.equals(this.lastSelectedUsername)) {
         this.lastSelectedUsername = activeUsername;
         this.selectionChangedAtMillis = System.currentTimeMillis();
      }

      float contentX = panelX + (51.5F) * scale;
      this.drawHeader(contentX, top, mouseX, mouseY);
      this.drawInput(contentX, top);
      this.drawAccountList(context, contentX, top, mouseX, mouseY, deltaSeconds);
      this.drawSelectedAccount(contentX, top, mouseX, mouseY, activeAccount);
      this.drawCreateButtons(contentX, top, mouseX, mouseY);
   }

   private float revealProgress(int sectionIndex) {
      return GuiVisuals.easeOutQuad(GuiVisuals.elapsedProgress(this.openedAtMillis, (40L) + sectionIndex * (55L), (420L)));
   }

   private void drawCreateButtons(float x, float top, float mouseX, float mouseY) {
      float reveal = this.revealProgress(4);
      Render2D.pushAlpha(reveal);
      float buttonX = x - (1.0F - reveal) * (14.0F) * this.scale;
      float buttonY = top + (411.5F) * this.scale;
      float generateHover = this.stateProgress(("altGenerate"), GuiVisuals.containsPoint(mouseX, mouseY, buttonX, buttonY, (109.5F) * this.scale, (19.5F) * this.scale));
      Render2D.fillRoundedRectangle(
         buttonX,
         buttonY,
         (109.5F) * this.scale,
         (19.5F) * this.scale,
         (6.0F) * this.scale,
         GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.031F) + (0.05F) * generateHover)
      );
      Render2D.drawOutline(
         buttonX,
         buttonY,
         (109.5F) * this.scale,
         (19.5F) * this.scale,
         (6.0F) * this.scale,
         (0.5F),
         GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.078F) + (0.09F) * generateHover)
      );
      GuiVisuals.drawCenteredImage(
         ("assets/mouseclient/textures/gui/mainmenu/icons/dice.png"),
         buttonX + (13.75F) * this.scale,
         buttonY + (9.75F) * this.scale,
         (7.5F) * this.scale,
         (7.5F) * this.scale,
         GuiVisuals.lerpColor(GuiVisuals.menuRowTextColor, GuiVisuals.white, generateHover)
      );
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         ("Сгенерировать аккаунт"),
         buttonX + (21.5F) * this.scale,
         buttonY + (6.25F) * this.scale,
         (7.0F) * this.scale,
         GuiVisuals.lerpColor(GuiVisuals.menuRowTextColor, GuiVisuals.white, generateHover)
      );
      this.actionRegions
         .add(
            new AccountPanel.AccountActionRegion(
               buttonX,
               buttonY,
               (109.5F) * this.scale,
               (19.5F) * this.scale,
               false,
               () -> this.accounts.switchSession(this.accounts.addOfflineAccount(this.nicknameGenerator.generate()))
            )
         );
      float createX = buttonX + (117.0F) * this.scale;
      float createHover = this.stateProgress(("altCreate"), GuiVisuals.containsPoint(mouseX, mouseY, createX, buttonY, (109.5F) * this.scale, (19.5F) * this.scale));
      float createActive = this.stateProgress(("altCreateActive"), this.inputMode == AccountPanel.AccountInputMode.CREATE);
      Render2D.fillRoundedRectangle(
         createX,
         buttonY,
         (109.5F) * this.scale,
         (19.5F) * this.scale,
         (6.0F) * this.scale,
         GuiVisuals.lerpColor(GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.031F) + (0.05F) * createHover), GuiVisuals.multiplyAlpha(GuiVisuals.accentColor, (0.85F)), createActive)
      );
      Render2D.drawOutline(
         createX,
         buttonY,
         (109.5F) * this.scale,
         (19.5F) * this.scale,
         (6.0F) * this.scale,
         (0.5F),
         GuiVisuals.lerpColor(GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.078F) + (0.09F) * createHover), GuiVisuals.accentOutlineColor, createActive)
      );
      GuiVisuals.drawCenteredImage(
         ("assets/mouseclient/textures/gui/mainmenu/icons/plus.png"),
         createX + (25.75F) * this.scale,
         buttonY + (9.75F) * this.scale,
         (6.5F) * this.scale,
         (6.5F) * this.scale,
         GuiVisuals.lerpColor(GuiVisuals.menuRowTextColor, GuiVisuals.white, Math.max(createHover, createActive))
      );
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         this.inputMode == AccountPanel.AccountInputMode.CREATE ? ("Подтвердить") : ("Создать аккаунт"),
         createX + (32.5F) * this.scale,
         buttonY + (6.25F) * this.scale,
         (7.0F) * this.scale,
         GuiVisuals.lerpColor(GuiVisuals.menuRowTextColor, GuiVisuals.white, Math.max(createHover, createActive))
      );
      this.actionRegions.add(new AccountPanel.AccountActionRegion(createX, buttonY, (109.5F) * this.scale, (19.5F) * this.scale, false, () -> {
         if (this.inputMode != AccountPanel.AccountInputMode.CREATE) {
            this.inputMode = AccountPanel.AccountInputMode.CREATE;
            this.inputText = "";
            this.selectAll = false;
         } else {
            this.submitInput();
         }
      }));
      Render2D.popAlpha();
   }

   private void drawAccountList(DrawContext context, float x, float top, float mouseX, float mouseY, float deltaSeconds) {
      float reveal = this.revealProgress(2);
      Render2D.pushAlpha(reveal);
      float listX = x - (1.0F - reveal) * (14.0F) * this.scale;
      this.listTop = top + (223.5F) * this.scale;
      this.listBottom = this.listTop + (138.5F) * this.scale;
      java.util.ArrayList<com.mouseclient.util.time.AltAccount> filteredAccounts = this.accounts.filteredAccounts(this.searchQuery);
      this.visibleAccounts = filteredAccounts;
      this.maxScroll = Math.max(0.0F, filteredAccounts.size() * (22.5F) - (4.5F) - (138.5F));
      this.scrollTarget = clamp(this.scrollTarget, 0.0F, this.maxScroll);
      this.scrollPosition = easeScroll(this.scrollPosition, this.scrollTarget, deltaSeconds);
      Render2D.pushScissor(context, listX, this.listTop, (227.0F) * this.scale, (138.5F) * this.scale);
      if (filteredAccounts.isEmpty()) {
         String emptyMessage = this.searchQuery.isEmpty() ? ("Аккаунтов пока нет") : ("Ничего не найдено");
         GuiTextRenderer.draw(
            GuiFont.SEMI,
            emptyMessage,
            listX + ((227.0F) * this.scale - GuiTextRenderer.getWidth(GuiFont.SEMI, emptyMessage, (7.0F) * this.scale)) / 2.0F,
            this.listTop + (64.0F) * this.scale,
            (7.0F) * this.scale,
            GuiVisuals.placeholderColor
         );
      }

      float rowY = this.listTop - this.snapScrollOffset(this.scrollPosition) * this.scale;
      int rowIndex = 0;

      for (AltAccount account : filteredAccounts) {
         if (rowY + (16.5F) * this.scale >= this.listTop - (6.0F) * this.scale
            && rowY <= this.listBottom + (6.0F) * this.scale) {
            this.drawAccountRow(account, listX, rowY, rowIndex, mouseX, mouseY);
         }

         rowY += (22.5F) * this.scale;
         rowIndex++;
      }

      Render2D.popScissor(context);
      if (this.maxScroll > (0.5F)) {
         float thumbHeight = Math.max((16.619999F), (138.5F) * ((138.5F) / (filteredAccounts.size() * (22.5F) - (4.5F)))) * this.scale;
         Render2D.fillRoundedRectangle(
            listX + (226.5F) * this.scale,
            this.listTop,
            (0.5F) * this.scale,
            (138.5F) * this.scale,
            (0.25F) * this.scale,
            GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.05F))
         );
         Render2D.fillRoundedRectangle(
            listX + (226.5F) * this.scale,
            this.listTop + this.scrollPosition / this.maxScroll * ((138.5F) * this.scale - thumbHeight),
            (0.5F) * this.scale,
            thumbHeight,
            (0.25F) * this.scale,
            GuiVisuals.listDividerColor
         );
      }

      Render2D.popAlpha();
   }

   private float stateProgress(String key, boolean active) {
      ScalarAnimation animation = this.stateAnimations.computeIfAbsent(key, animationKey -> new ScalarAnimation((140L)));
      animation.setTarget(active ? 1.0 : 0.0);
      return (float)animation.value();
   }

   private float snapScrollOffset(float offset) {
      float pixelScale = this.scale * GuiScale.getBaseScale() / GuiScale.getWindowScale();
      return pixelScale > (1.0E-4F) ? Math.round(offset * pixelScale) / pixelScale : offset;
   }

   public boolean mouseClicked(float mouseX, float mouseY) {
      boolean hadFocus = this.inputMode != AccountPanel.AccountInputMode.NONE;
      this.inputMode = this.inputMode == AccountPanel.AccountInputMode.SEARCH
         ? AccountPanel.AccountInputMode.NONE
         : this.inputMode;

      for (int regionIndex = this.actionRegions.size() - 1; regionIndex >= 0; regionIndex--) {
         AccountPanel.AccountActionRegion region = this.actionRegions.get(regionIndex);
         if ((!region.clipped() || !(mouseY < this.listTop) && !(mouseY > this.listBottom))
            && GuiVisuals.containsPoint(mouseX, mouseY, region.x(), region.y(), region.width(), region.height())) {
            region.action().run();
            return true;
         }
      }

      if (hadFocus) {
         this.inputMode = AccountPanel.AccountInputMode.NONE;
         this.selectAll = false;
         return true;
      } else {
         return false;
      }
   }

   public void blurInput() {
      this.inputMode = AccountPanel.AccountInputMode.NONE;
      this.selectAll = false;
   }

   public boolean mouseScrolled(float mouseX, float mouseY, float amount) {
      if (mouseY >= this.listTop - (30.0F) * this.scale && mouseY <= this.listBottom + (10.0F) * this.scale) {
         this.scrollTarget = clamp(this.scrollTarget - amount * (22.5F), 0.0F, this.maxScroll);
         return true;
      } else {
         return false;
      }
   }

   private static float easeScroll(float current, float target, float deltaSeconds) {
      current += (target - current) * (1.0F - (float)Math.exp(-deltaSeconds * (22.0F)));
      return Math.abs(target - current) < (0.1F) ? target : current;
   }

   public boolean keyPressed(int keyCode) {
      if (this.inputMode == AccountPanel.AccountInputMode.NONE) {
         return false;
      }

      boolean searching = this.inputMode == AccountPanel.AccountInputMode.SEARCH;
      if (GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), 341) == 1
         || GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), 345) == 1) {
         if (keyCode == 65) {
            this.selectAll = true;
            return true;
         }

         if (keyCode == 67) {
            String clipboardText = searching ? this.searchQuery : this.inputText;
            MinecraftClient.getInstance().keyboard.setClipboard(clipboardText);
            return true;
         }

         if (keyCode == 86) {
            String pastedText = MinecraftClient.getInstance().keyboard.getClipboard().replaceAll(("[\\r\\n\\t]"), "");
            if (this.selectAll) {
               if (searching) {
                  this.searchQuery = "";
               } else {
                  this.inputText = "";
               }

               this.selectAll = false;
            }

            if (searching) {
               this.searchQuery = (this.searchQuery + pastedText).length() > 24 ? (this.searchQuery + pastedText).substring(0, 24) : this.searchQuery + pastedText;
            } else {
               this.inputText = (this.inputText + pastedText).length() > 16 ? (this.inputText + pastedText).substring(0, 16) : this.inputText + pastedText;
            }

            return true;
         }
      }

      if (keyCode == 259) {
         if (this.selectAll) {
            if (searching) {
               this.searchQuery = "";
            } else {
               this.inputText = "";
            }

            this.selectAll = false;
            return true;
         } else {
            if (searching && !this.searchQuery.isEmpty()) {
               this.searchQuery = this.searchQuery.substring(0, this.searchQuery.length() - 1);
            } else if (!searching && !this.inputText.isEmpty()) {
               this.inputText = this.inputText.substring(0, this.inputText.length() - 1);
            }

            return true;
         }
      } else if (keyCode != 257 && keyCode != 335) {
         if (keyCode == 256) {
            this.inputMode = AccountPanel.AccountInputMode.NONE;
            this.selectAll = false;
            return true;
         } else {
            return false;
         }
      } else {
         if (searching) {
            this.inputMode = AccountPanel.AccountInputMode.NONE;
            this.selectAll = false;
         } else {
            this.submitInput();
         }

         return true;
      }
   }

   private static float clamp(float value, float minimum, float maximum) {
      return Math.max(minimum, Math.min(maximum, value));
   }

   public void open(Runnable onBack) {
      this.openedAtMillis = System.currentTimeMillis();
      this.onBack = onBack;
      this.inputMode = AccountPanel.AccountInputMode.NONE;
      this.selectAll = false;
      this.inputText = "";
      this.selectionChangedAtMillis = 0L;
      this.lastSelectedUsername = this.accounts.activeAccount() == null ? null : this.accounts.activeAccount().username();
   }

   public AccountPanel() {
      this.lastFrameMillis = System.nanoTime() / (1000000L);
      this.onBack = () -> {};
      this.scale = 1.0F;
      this.actionRegions = new ArrayList<>();
      this.visibleAccounts = new ArrayList<>();
   }

   private void drawSelectedAccount(float x, float top, float mouseX, float mouseY, AltAccount account) {
      float reveal = this.revealProgress(3);
      Render2D.pushAlpha(reveal);
      float cardX = x - (1.0F - reveal) * (14.0F) * this.scale;
      float cardY = top + (372.5F) * this.scale;
      if (account == null) {
         Render2D.fillRoundedRectangle(
            cardX,
            cardY,
            (226.5F) * this.scale,
            (32.0F) * this.scale,
            (6.0F) * this.scale,
            GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.031F))
         );
         Render2D.drawOutline(
            cardX, cardY, (226.5F) * this.scale, (32.0F) * this.scale, (6.0F) * this.scale, (0.5F), GuiVisuals.menuFieldOutlineColor
         );
         String emptyMessage = ("Выберите аккаунт из списка");
         GuiTextRenderer.draw(
            GuiFont.SEMI,
            emptyMessage,
            cardX + ((226.5F) * this.scale - GuiTextRenderer.getWidth(GuiFont.SEMI, emptyMessage, (7.0F) * this.scale)) / 2.0F,
            cardY + (12.5F) * this.scale,
            (7.0F) * this.scale,
            GuiVisuals.placeholderColor
         );
         Render2D.popAlpha();
      } else {
         float selectionProgress = this.selectionChangedAtMillis == 0L ? 1.0F : GuiVisuals.easeOutQuad(GuiVisuals.elapsedProgress(this.selectionChangedAtMillis, 0L, (340L)));
         Render2D.fillRoundedRectangle(
            cardX, cardY, (226.5F) * this.scale, (32.0F) * this.scale, (6.0F) * this.scale, ClickGuiTheme.surface(0.22F, 255)
         );
         Render2D.drawOutline(
            cardX, cardY, (226.5F) * this.scale, (32.0F) * this.scale, (6.0F) * this.scale, (0.5F), GuiVisuals.accentColor
         );
         Render2D.pushAlpha(selectionProgress);
         float contentOffsetY = (1.0F - selectionProgress) * (4.0F) * this.scale;
         GuiVisuals.drawCenteredImage(
            ("assets/mouseclient/textures/gui/mainmenu/icons/user.png"),
            cardX + (39.75F) * this.scale,
            cardY + (10.0F) * this.scale + contentOffsetY,
            (5.5F) * this.scale,
            (5.5F) * this.scale,
            GuiVisuals.multiplyAlpha(GuiVisuals.buttonTextColor, (0.7F))
         );
         GuiTextRenderer.draw(
            GuiFont.SEMI,
            ("Выбранный аккаунт"),
            cardX + (44.0F) * this.scale,
            cardY + (7.0F) * this.scale + contentOffsetY,
            (6.0F) * this.scale,
            GuiVisuals.multiplyAlpha(GuiVisuals.buttonTextColor, (0.7F))
         );
         GuiTextRenderer.draw(
            GuiFont.SEMI_BOLD,
            account.username(),
            cardX + (38.0F) * this.scale,
            cardY + (16.0F) * this.scale + contentOffsetY,
            (7.0F) * this.scale,
            GuiVisuals.buttonTextColor
         );
         Render2D.popAlpha();
         float renameHover = this.stateProgress(
            ("altRename"),
            GuiVisuals.containsPoint(
               mouseX,
               mouseY,
               cardX + (185.0F) * this.scale,
               cardY + (10.5F) * this.scale,
               (11.0F) * this.scale,
               (11.0F) * this.scale
            )
         );
         GuiVisuals.drawCenteredImage(
            ("assets/mouseclient/textures/gui/mainmenu/icons/rename.png"),
            cardX + (190.5F) * this.scale,
            cardY + (16.0F) * this.scale,
            (7.0F) * this.scale,
            (7.0F) * this.scale,
            GuiVisuals.multiplyAlpha(GuiVisuals.buttonTextColor, (0.72F) + (0.28F) * renameHover)
         );
         this.actionRegions
            .add(
               new AccountPanel.AccountActionRegion(
                  cardX + (185.0F) * this.scale,
                  cardY + (10.5F) * this.scale,
                  (11.0F) * this.scale,
                  (11.0F) * this.scale,
                  false,
                  () -> {
                     this.inputMode = AccountPanel.AccountInputMode.RENAME;
                     this.inputText = account.username();
                     this.selectAll = true;
                  }
               )
            );
         float deleteHover = this.stateProgress(
            ("altDelete"),
            GuiVisuals.containsPoint(
               mouseX,
               mouseY,
               cardX + (203.0F) * this.scale,
               cardY + (10.5F) * this.scale,
               (11.0F) * this.scale,
               (11.0F) * this.scale
            )
         );
         GuiVisuals.drawCenteredImage(
            ("assets/mouseclient/textures/gui/mainmenu/icons/delete.png"),
            cardX + (208.75F) * this.scale,
            cardY + (16.0F) * this.scale,
            (7.5F) * this.scale,
            (7.0F) * this.scale,
            GuiVisuals.lerpColor(GuiVisuals.multiplyAlpha(GuiVisuals.buttonTextColor, (0.72F)), GuiVisuals.dangerColor, deleteHover)
         );
         this.actionRegions
            .add(
               new AccountPanel.AccountActionRegion(
                  cardX + (203.0F) * this.scale,
                  cardY + (10.5F) * this.scale,
                  (11.0F) * this.scale,
                  (11.0F) * this.scale,
                  false,
                  () -> this.accounts.removeAccount(account)
               )
            );
         Render2D.popAlpha();
      }
   }

   private void drawHeader(float x, float top, float mouseX, float mouseY) {
      float reveal = this.revealProgress(0);
      Render2D.pushAlpha(reveal);
      float headerX = x - (1.0F - reveal) * (14.0F) * this.scale;
      float backHover = this.stateProgress(
         ("altBack"),
         GuiVisuals.containsPoint(
            mouseX,
            mouseY,
            headerX - (4.0F) * this.scale,
            top + (105.0F) * this.scale,
            (18.0F) * this.scale,
            (18.0F) * this.scale
         )
      );
      GuiVisuals.drawCenteredImage(
         ("assets/mouseclient/textures/gui/mainmenu/icons/back.png"),
         headerX + (4.75F) * this.scale - backHover * (1.5F) * this.scale,
         top + (113.75F) * this.scale,
         (9.5F) * this.scale,
         (9.5F) * this.scale,
         GuiVisuals.lerpColor(GuiVisuals.menuDescriptionColor, GuiVisuals.white, backHover)
      );
      this.actionRegions
         .add(
            new AccountPanel.AccountActionRegion(
               headerX - (4.0F) * this.scale,
               top + (105.0F) * this.scale,
               (18.0F) * this.scale,
               (18.0F) * this.scale,
               false,
               this.onBack
            )
         );
      GuiTextRenderer.draw(
         GuiFont.SEMI, ("маусклиент  /  Главная  /  Аккаунты"), headerX + (15.5F) * this.scale, top + (110.5F) * this.scale, (6.5F) * this.scale, GuiVisuals.menuDescriptionColor
      );
      GuiTextRenderer.draw(GuiFont.SEMI, ("Управление аккаунтами."), headerX, top + (124.5F) * this.scale, (20.0F) * this.scale, GuiVisuals.white);
      GuiTextRenderer.draw(GuiFont.SEMI, ("Создавайте и выбирайте игровые профили"), headerX, top + (159.5F) * this.scale, (7.5F) * this.scale, GuiVisuals.menuDescriptionColor);
      Render2D.popAlpha();
   }

   private void drawInput(float x, float top) {
      float reveal = this.revealProgress(1);
      Render2D.pushAlpha(reveal);
      float fieldX = x - (1.0F - reveal) * (14.0F) * this.scale;
      float fieldY = top + (184.5F) * this.scale;
      boolean focused = this.inputMode != AccountPanel.AccountInputMode.NONE;
      float focusProgress = this.stateProgress(("altFieldFocus"), focused);
      Render2D.fillRoundedRectangle(
         fieldX,
         fieldY,
         (226.5F) * this.scale,
         (19.5F) * this.scale,
         (6.0F) * this.scale,
         GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.031F))
      );
      Render2D.drawOutline(
         fieldX,
         fieldY,
         (226.5F) * this.scale,
         (19.5F) * this.scale,
         (6.0F) * this.scale,
         (0.5F),
         GuiVisuals.lerpColor(GuiVisuals.menuFieldOutlineColor, GuiVisuals.multiplyAlpha(GuiVisuals.accentOutlineColor, (0.8F)), focusProgress)
      );
      GuiVisuals.drawCenteredImage(
         this.inputMode == AccountPanel.AccountInputMode.CREATE
            ? ("assets/mouseclient/textures/gui/mainmenu/icons/plus.png")
            : (this.inputMode == AccountPanel.AccountInputMode.RENAME ? ("assets/mouseclient/textures/gui/mainmenu/icons/rename.png") : ("assets/mouseclient/textures/gui/mainmenu/icons/search.png")),
         fieldX + (8.5F) * this.scale,
         fieldY + (9.75F) * this.scale,
         (6.5F) * this.scale,
         (6.5F) * this.scale,
         GuiVisuals.lerpColor(GuiVisuals.placeholderColor, GuiVisuals.multiplyAlpha(GuiVisuals.accentOutlineColor, (0.95F)), focusProgress)
      );
      String displayText = this.inputMode != AccountPanel.AccountInputMode.SEARCH && this.inputMode != AccountPanel.AccountInputMode.NONE
         ? this.inputText
         : this.searchQuery;
      String placeholder = this.inputMode == AccountPanel.AccountInputMode.CREATE
         ? ("Введите ник нового аккаунта...")
         : (this.inputMode == AccountPanel.AccountInputMode.RENAME ? ("Новый ник аккаунта...") : ("Найти аккаунт..."));
      if (focused && this.selectAll && !displayText.isEmpty()) {
         Render2D.fillRoundedRectangle(
            fieldX + (19.0F) * this.scale,
            fieldY + (5.5F) * this.scale,
            GuiTextRenderer.getWidth(GuiFont.SEMI, displayText, (7.0F) * this.scale) + 1.0F * this.scale,
            (9.0F) * this.scale,
            2.0F * this.scale,
            GuiVisuals.multiplyAlpha(GuiVisuals.accentColor, (0.3F))
         );
      }

      GuiTextRenderer.draw(
         GuiFont.SEMI,
         (displayText.isEmpty() && !focused ? placeholder : displayText) + (focused && System.currentTimeMillis() % (1000L) < (500L) ? ("|") : ""),
         fieldX + (19.0F) * this.scale,
         fieldY + (6.25F) * this.scale,
         (7.0F) * this.scale,
         displayText.isEmpty() ? GuiVisuals.placeholderColor : GuiVisuals.white
      );
      if (displayText.isEmpty() && focused) {
         GuiTextRenderer.draw(
            GuiFont.SEMI,
            placeholder,
            fieldX
               + (19.0F) * this.scale
               + GuiTextRenderer.getWidth(GuiFont.SEMI, ("|"), (7.0F) * this.scale)
               + (1.5F) * this.scale,
            fieldY + (6.25F) * this.scale,
            (7.0F) * this.scale,
            GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.16F))
         );
      }

      this.actionRegions.add(new AccountPanel.AccountActionRegion(fieldX, fieldY, (226.5F) * this.scale, (19.5F) * this.scale, false, () -> {
         if (this.inputMode == AccountPanel.AccountInputMode.NONE) {
            this.inputMode = AccountPanel.AccountInputMode.SEARCH;
         }
      }));
      float labelY = top + (210.0F) * this.scale;
      GuiTextRenderer.draw(GuiFont.SEMI, ("Ваши аккаунты"), fieldX, labelY, (6.0F) * this.scale, GuiVisuals.menuDescriptionColor);
      Render2D.fillRoundedRectangle(
         fieldX + (51.5F) * this.scale,
         labelY + (3.5F) * this.scale,
         (175.0F) * this.scale,
         (0.5F) * this.scale,
         (0.25F) * this.scale,
         GuiVisuals.listDividerColor
      );
      Render2D.popAlpha();
   }

   private void submitInput() {
      if (this.inputMode == AccountPanel.AccountInputMode.CREATE && !this.inputText.isBlank()) {
         this.accounts.switchSession(this.accounts.addOfflineAccount(this.inputText));
      }

      if (this.inputMode == AccountPanel.AccountInputMode.RENAME && !this.inputText.isBlank()) {
         this.accounts.renameAccount(this.accounts.activeAccount(), this.inputText);
      }

      this.inputText = "";
      this.selectAll = false;
      this.inputMode = AccountPanel.AccountInputMode.NONE;
   }

   private void drawAccountRow(AltAccount account, float x, float y, int rowIndex, float mouseX, float mouseY) {
      float reveal = GuiVisuals.easeOutQuad(GuiVisuals.elapsedProgress(this.openedAtMillis, (200L) + Math.min(rowIndex, 8) * (45L), (380L)));
      float creationProgress = GuiVisuals.easeOutQuad(Math.min(1.0F, (float)(System.currentTimeMillis() - account.createdAtMillis()) / (260.0F)));
      Render2D.pushAlpha(reveal * creationProgress);
      float rowX = x + (1.0F - reveal) * (8.0F) * this.scale + (1.0F - creationProgress) * (8.0F) * this.scale;
      float activeProgress = this.stateProgress("altRowActive" + account.username(), this.accounts.isActive(account));
      float hoverProgress = this.stateProgress(
         "altRow" + account.username(),
         activeProgress < (0.5F)
            && GuiVisuals.containsPoint(mouseX, mouseY, rowX, y, (218.5F) * this.scale, (16.5F) * this.scale)
            && mouseY >= this.listTop
            && mouseY <= this.listBottom
      );
      Render2D.fillRoundedRectangle(
         rowX,
         y,
         (218.5F) * this.scale,
         (16.5F) * this.scale,
         (5.0F) * this.scale,
         GuiVisuals.lerpColor(GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.031F) + (0.045F) * hoverProgress), GuiVisuals.accentColor, activeProgress)
      );
      Render2D.drawOutline(
         rowX,
         y,
         (218.5F) * this.scale,
         (16.5F) * this.scale,
         (5.0F) * this.scale,
         (0.5F),
         GuiVisuals.lerpColor(GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.078F) + (0.09F) * hoverProgress), GuiVisuals.accentOutlineColor, activeProgress)
      );
      GuiTextRenderer.draw(
         activeProgress > (0.5F) ? GuiFont.SEMI_BOLD : GuiFont.SEMI,
         account.username(),
         rowX + (20.5F) * this.scale,
         y + (5.25F) * this.scale,
         (6.0F) * this.scale,
         GuiVisuals.lerpColor(GuiVisuals.lerpColor(GuiVisuals.menuRowTextColor, GuiVisuals.white, hoverProgress), GuiVisuals.white, activeProgress)
      );
      this.actionRegions
         .add(
            new AccountPanel.AccountActionRegion(rowX, y, (218.5F) * this.scale, (16.5F) * this.scale, true, () -> this.accounts.switchSession(account))
         );
      Render2D.popAlpha();
   }

   public boolean charTyped(int codePoint) {
      if (codePoint >= 32 && codePoint <= (65535) && this.inputMode != AccountPanel.AccountInputMode.NONE) {
         char character = (char)codePoint;
         if (this.inputMode == AccountPanel.AccountInputMode.SEARCH) {
            if (this.selectAll) {
               this.searchQuery = "";
               this.selectAll = false;
            }

            if (this.searchQuery.length() < 24) {
               this.searchQuery = this.searchQuery + character;
            }
         } else {
            if (this.selectAll) {
               this.inputText = "";
               this.selectAll = false;
            }

            if (this.inputText.length() < 16) {
               this.inputText = this.inputText + character;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public void renderSkins(DrawContext context, float panelX, float scale, float top) {
      float reveal = this.revealProgress(2);
      if (!(reveal < (0.05F))) {
         float guiScale = GuiScale.getWindowScale() / GuiScale.getBaseScale();
         float contentX = panelX + (51.5F) * scale;
         float listX = contentX - (1.0F - reveal) * (14.0F) * scale;
         float rowY = this.listTop - this.snapScrollOffset(this.scrollPosition) * scale;

         for (AltAccount account : this.visibleAccounts) {
            if (rowY + (16.5F) * scale >= this.listTop - 2.0F * scale && rowY <= this.listBottom + 2.0F * scale) {
               float clipTop = Math.max(rowY + (1.5F) * scale, this.listTop);
               float clipBottom = Math.min(rowY + (15.0F) * scale, this.listBottom);
               if (clipBottom - clipTop > 2.0F * scale) {
                  context.enableScissor(
                     GuiScale.toGuiPixels(listX + (3.5F) * scale),
                     GuiScale.toGuiPixels(clipTop),
                     GuiScale.toGuiPixels(listX + (19.5F) * scale),
                     GuiScale.toGuiPixels(clipBottom)
                  );

                  try {
                     AccountSkinRenderer.render(context, account, guiScale, listX + (11.5F) * scale, rowY + (16.5F) * scale, (19.0F) * scale * reveal);
                  } catch (RuntimeException error) {
                  }

                  context.disableScissor();
               }
            }

            rowY += (22.5F) * scale;
         }

         AltAccount selectedAccount = this.accounts.activeAccount();
         if (selectedAccount != null) {
            float selectionReveal = this.revealProgress(3);
            if (!(selectionReveal < (0.05F))) {
               float selectionProgress = this.selectionChangedAtMillis == 0L ? 1.0F : GuiVisuals.easeOutQuad(GuiVisuals.elapsedProgress(this.selectionChangedAtMillis, 0L, (340L)));
               float cardX = contentX - (1.0F - selectionReveal) * (14.0F) * scale;
               float cardY = top + (372.5F) * scale;
               float skinHeight = (34.0F) * scale * selectionReveal * ((0.75F) + (0.25F) * selectionProgress);
               context.enableScissor(
                  GuiScale.toGuiPixels(cardX + (4.5F) * scale),
                  GuiScale.toGuiPixels(cardY + 1.0F * scale),
                  GuiScale.toGuiPixels(cardX + (33.5F) * scale),
                  GuiScale.toGuiPixels(cardY + (31.5F) * scale)
               );

               try {
                  AccountSkinRenderer.render(context, selectedAccount, guiScale, cardX + (19.0F) * scale, cardY + 2.0F * scale + skinHeight * (0.8F), skinHeight);
               } catch (RuntimeException skinError) {
               }

               context.disableScissor();
            }
         }
      }
   }

   public enum AccountInputMode {
      NONE,
      SEARCH,
      CREATE,
      RENAME;
   }

   private record AccountActionRegion(float x, float y, float width, float height, boolean clipped, Runnable action) {
      public float height() {
         return this.height;
      }

      public float width() {
         return this.width;
      }
   }
}
