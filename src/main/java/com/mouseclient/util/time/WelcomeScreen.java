package com.mouseclient.util.time;

import com.mouseclient.gui.Render2D;
import com.mouseclient.gui.MainMenuScreen;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.modules.impl.render.HudSettingsModule;
import com.mouseclient.render.AccountSkinRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import com.mouseclient.internal.ProfileDisplayName;

public class WelcomeScreen extends Screen {
   private final long createdAt = System.currentTimeMillis();
   private long welcomeStartedAt;
   private long menuTransitionStartedAt;
   private final ScalarAnimation buttonHoverAnimation;
   private float buttonX;
   private float buttonY;
   private float buttonWidth;
   private float buttonHeight;

   @Override
   public boolean mouseClicked(Click click, boolean doubleClick) {
      if (click.button() == 0) {
         long now = System.currentTimeMillis();
         if (this.welcomeStartedAt == 0L) {
            if (now - this.createdAt > (400L)) {
               this.welcomeStartedAt = now;
            }

            return true;
         }

         float coordinateScale = spaceFactor();
         if (this.menuTransitionStartedAt == 0L
            && GuiVisuals.containsPoint((float)click.x() * coordinateScale, (float)click.y() * coordinateScale, this.buttonX, this.buttonY, this.buttonWidth, this.buttonHeight)) {
            this.menuTransitionStartedAt = now;
            return true;
         }
      }

      return super.mouseClicked(click, doubleClick);
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
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
   }

   @Override
   protected void init() {
      super.init();
      MinecraftClient mc = this.client != null ? this.client : MinecraftClient.getInstance();
      if (mc != null) {
         mc.setScreen(new MainMenuScreen(false));
      }
   }

   private static float spaceFactor() {
      return GuiScale.getWindowScale() / GuiScale.getBaseScale();
   }

   private void drawSessionChip(float x, float y, float scale) {
      MinecraftClient client = this.client != null ? this.client : MinecraftClient.getInstance();
      String sessionName = client != null && client.getSession() != null ? client.getSession().getUsername() : ("Player");
      String skinPath = AccountSkinRenderer.skinTextures(sessionName, AltAccount.offlineUuid(sessionName)).body().texturePath().toString();
      Render2D.drawNearestImageRegion(
         skinPath,
         x,
         y,
         (15.0F) * scale,
         (15.0F) * scale,
         (5.0F) * scale,
         1.0F,
         (0.125F),
         (0.125F),
         (0.25F),
         (0.25F),
         GuiVisuals.white
      );
      Render2D.drawNearestImageRegion(
         skinPath,
         x,
         y,
         (15.0F) * scale,
         (15.0F) * scale,
         (5.0F) * scale,
         1.0F,
         (0.625F),
         (0.125F),
         (0.75F),
         (0.25F),
         GuiVisuals.white
      );
      GuiVisuals.drawCenteredImage(("assets/mouseclient/textures/gui/mainmenu/icons/person.png"), x + (22.2F) * scale, y + (3.2F) * scale, (3.4F) * scale, (4.5F) * scale, GuiVisuals.menuDescriptionColor);
      GuiTextRenderer.draw(GuiFont.SEMI, ("Активная сессия"), x + (26.5F) * scale, y + (0.5F) * scale, (5.0F) * scale, GuiVisuals.menuDescriptionColor);
      GuiTextRenderer.draw(
         GuiFont.SEMI_BOLD,
         ProfileDisplayName.get(sessionName),
         x + (20.0F) * scale,
         y + (7.5F) * scale,
         (6.5F) * scale,
         GuiVisuals.white
      );
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      HudSettingsModule.getInstance().syncThemeColor();
      float screenWidth = this.width * spaceFactor();
      float screenHeight = this.height * spaceFactor();
      float sceneScale = Math.min(screenWidth / (960.0F), screenHeight / (540.0F));
      float verticalOffset = (screenHeight - (540.0F) * sceneScale) / 2.0F;
      long now = System.currentTimeMillis();
      GuiVisuals.loadingSceneEnabled = true;
      Render2D.begin(context);
      if (this.welcomeStartedAt == 0L && now - this.createdAt >= (2700L)) {
         this.welcomeStartedAt = now;
      }

      float welcomeProgress = this.welcomeStartedAt == 0L ? 0.0F : GuiVisuals.easeOutQuad(GuiVisuals.elapsedProgress(this.welcomeStartedAt, 0L, (450L)));
      GuiVisuals.drawCoverBackground(("assets/mouseclient/textures/gui/mainmenu/background/loading.png"), screenWidth, screenHeight, 1.0F);
      if (welcomeProgress > (0.001F)) {
         GuiVisuals.drawCoverBackground(("assets/mouseclient/textures/gui/mainmenu/background/welcome.png"), screenWidth, screenHeight, welcomeProgress);
      }

      if (welcomeProgress < (0.999F)) {
         Render2D.pushAlpha(1.0F - welcomeProgress);
         GuiVisuals.drawLoadingScene(this.createdAt, screenWidth, screenHeight);
         Render2D.popAlpha();
      }

      if (welcomeProgress > (0.001F)) {
         float menuProgress = this.menuTransitionStartedAt == 0L ? 0.0F : GuiVisuals.easeOutQuad(GuiVisuals.elapsedProgress(this.menuTransitionStartedAt, 0L, (320L)));
         Render2D.pushAlpha(welcomeProgress * (1.0F - menuProgress));
         this.drawWelcomePhase(screenHeight, sceneScale, verticalOffset, mouseX * spaceFactor(), mouseY * spaceFactor(), menuProgress * (26.0F) * sceneScale);
         Render2D.popAlpha();
      }

      Render2D.end();
   }

   @Override
   public void tick() {
      MinecraftClient client = this.client != null ? this.client : MinecraftClient.getInstance();
      if (client != null) {
         client.setScreen(new MainMenuScreen(false));
      }
   }

   public WelcomeScreen() {
      super(Text.literal(("маусклиент")));
      this.buttonHoverAnimation = new ScalarAnimation((160L));
   }

   private void drawWelcomePhase(float screenHeight, float scale, float verticalOffset, float mouseX, float mouseY, float slideOffset) {
      long phaseStartedAt = this.welcomeStartedAt;
      float contentX = (51.5F) * scale - slideOffset;
      float logoProgress = GuiVisuals.easeOutQuad(GuiVisuals.elapsedProgress(phaseStartedAt, 0L, (420L)));
      Render2D.pushAlpha(logoProgress);
      Render2D.drawRoundedImage(
         ("assets/mouseclient/textures/gui/mainmenu/icons/logo.png"), contentX - (1.0F - logoProgress) * (14.0F) * scale, (40.0F) * scale, (13.5F) * scale, (10.5F) * scale, 0.0F, GuiVisuals.accentColor
      );
      Render2D.popAlpha();
      float titleProgress = GuiVisuals.easeOutQuad(GuiVisuals.elapsedProgress(phaseStartedAt, (90L), (460L)));
      Render2D.pushAlpha(titleProgress);
      float titleX = contentX - (1.0F - titleProgress) * (18.0F) * scale;
      GuiTextRenderer.draw(GuiFont.SEMI, ("Добро пожаловать в"), titleX, verticalOffset + (209.0F) * scale, (20.0F) * scale, GuiVisuals.white);
      Render2D.drawRoundedImage(("assets/mouseclient/textures/gui/mainmenu/icons/mouseclient_serif.png"), titleX, verticalOffset + (236.0F) * scale, (68.04957F) * scale, (15.4F) * scale, 0.0F, GuiVisuals.accentColor);
      Render2D.popAlpha();
      float descriptionProgress = GuiVisuals.easeOutQuad(GuiVisuals.elapsedProgress(phaseStartedAt, (170L), (460L)));
      Render2D.pushAlpha(descriptionProgress);
      float descriptionY = verticalOffset + (268.5F) * scale;

      for (String descriptionLine : GuiVisuals.wrapWords(GuiFont.SEMI, ("Всё готово для комфортной игры. Настройте клиент под себя, используйте любимые функции и откройте больше возможностей вместе с маусклиент."), (7.5F) * scale, (246.0F) * scale)) {
         GuiTextRenderer.draw(GuiFont.SEMI, descriptionLine, contentX - (1.0F - descriptionProgress) * (18.0F) * scale, descriptionY, (7.5F) * scale, GuiVisuals.menuDescriptionColor);
         descriptionY += (9.0F) * scale;
      }

      Render2D.popAlpha();
      float buttonProgress = GuiVisuals.easeOutQuad(GuiVisuals.elapsedProgress(phaseStartedAt, (250L), (460L)));
      Render2D.pushAlpha(buttonProgress);
      this.buttonX = contentX - (1.0F - buttonProgress) * (18.0F) * scale;
      this.buttonY = verticalOffset + (311.0F) * scale;
      this.buttonWidth = (231.0F) * scale;
      this.buttonHeight = (19.5F) * scale;
      this.buttonHoverAnimation
         .setTarget(this.menuTransitionStartedAt == 0L && GuiVisuals.containsPoint(mouseX, mouseY, this.buttonX, this.buttonY, this.buttonWidth, this.buttonHeight) ? 1.0 : 0.0);
      float hoverProgress = (float)this.buttonHoverAnimation.value();
      Render2D.fillRoundedRectangle(
         this.buttonX,
         this.buttonY,
         this.buttonWidth,
         this.buttonHeight,
         (6.0F) * scale,
         GuiVisuals.lerpColor(GuiVisuals.accentColor, GuiVisuals.accentLightColor, hoverProgress * (0.65F))
      );
      Render2D.drawOutline(this.buttonX, this.buttonY, this.buttonWidth, this.buttonHeight, (6.0F) * scale, (0.5F), GuiVisuals.accentOutlineColor);
      GuiVisuals.drawCenteredImage(
         ("assets/mouseclient/textures/gui/mainmenu/icons/arrow.png"),
         this.buttonX + (9.5F) * scale + hoverProgress * (1.5F) * scale,
         this.buttonY + this.buttonHeight / 2.0F,
         (6.0F) * scale,
         (6.0F) * scale,
         GuiVisuals.buttonTextColor
      );
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         ("Перейти в главное меню"),
         this.buttonX + (19.5F) * scale,
         this.buttonY + this.buttonHeight / 2.0F - (3.5F) * scale,
         (7.0F) * scale,
         GuiVisuals.buttonTextColor
      );
      Render2D.popAlpha();
      float sessionProgress = GuiVisuals.easeOutQuad(GuiVisuals.elapsedProgress(phaseStartedAt, (330L), (460L)));
      Render2D.pushAlpha(sessionProgress);
      this.drawSessionChip(contentX, screenHeight - (51.0F) * scale + (1.0F - sessionProgress) * (10.0F) * scale, scale);
      Render2D.popAlpha();
   }
}
