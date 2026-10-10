package com.mouseclient.render.font;

import com.mouseclient.render.avatar.FontAtlasPage;
import com.mouseclient.render.avatar.LayoutGlyph;
import java.util.List;

public final class TextLayout {
   public static final TextLayout EMPTY = new TextLayout(List.of(), 0.0F, 0.0F);
   private final List<TextLayout.PageGlyphs> pages;
   private final float width;
   private final float height;

   public boolean isEmpty() {
      return this.pages.isEmpty();
   }

   public float getHeight() {
      return this.height;
   }

   public TextLayout(List<TextLayout.PageGlyphs> pages, float width, float height) {
      this.pages = List.copyOf(pages);
      this.width = width;
      this.height = height;
   }

   public float getWidth() {
      return this.width;
   }

   public List<TextLayout.PageGlyphs> getPages() {
      return this.pages;
   }

   public record PageGlyphs(FontAtlasPage page, List<LayoutGlyph> glyphs) {
      public PageGlyphs(FontAtlasPage page, List<LayoutGlyph> glyphs) {
         glyphs = List.copyOf(glyphs);
         this.page = page;
         this.glyphs = glyphs;
      }
   }
}
