package com.mouseclient.util.time;

public class TextSetting extends Setting<TextSetting> {
   private String value = "";
   private String placeholder = "";
   private int minLength;
   private int maxLength = 256;
   private int cursor;
   private int selectionAnchor;
   public boolean focused;

   public String getSelectedText() {
      return this.value.substring(this.getSelectionStart(), this.getSelectionEnd());
   }

   public void backspace() {
      if (this.hasSelection()) {
         this.replaceRange(this.getSelectionStart(), this.getSelectionEnd(), "");
      } else if (this.cursor > 0) {
         this.replaceRange(this.cursor - 1, this.cursor, "");
      }
   }

   public void moveToStart(boolean extendSelection) {
      this.cursor = 0;
      if (!extendSelection) {
         this.selectionAnchor = 0;
      }
   }

   private void replaceRange(int start, int end, String replacement) {
      this.cursor = this.selectionAnchor = start + replacement.length();
      this.setValue(this.value.substring(0, start) + replacement + this.value.substring(end));
   }

   public String getPlaceholder() {
      return this.placeholder;
   }

   public TextSetting(String name, String description) {
      super(name, description);
   }

   public int getCursor() {
      return this.cursor;
   }

   public TextSetting withPlaceholder(String placeholder) {
      this.placeholder = placeholder == null ? "" : placeholder;
      return this;
   }

   public int getMaxLength() {
      return this.maxLength;
   }

   public int getMinLength() {
      return this.minLength;
   }

   public boolean hasSelection() {
      return this.cursor != this.selectionAnchor;
   }

   public void insertText(String text) {
      this.replaceRange(this.getSelectionStart(), this.getSelectionEnd(), text == null ? "" : text.replaceAll(("[\\r\\n\\t]"), ""));
   }

   public void appendCharacter(char character) {
      if (this.value.length() < this.maxLength) {
         this.setValue(this.value + character);
      }
   }

   public void removeLastCharacter() {
      if (!this.value.isEmpty()) {
         this.setValue(this.value.substring(0, this.value.length() - 1));
      }
   }

   public void deleteForward() {
      if (this.hasSelection()) {
         this.replaceRange(this.getSelectionStart(), this.getSelectionEnd(), "");
      } else if (this.cursor < this.value.length()) {
         this.replaceRange(this.cursor, this.cursor + 1, "");
      }
   }

   public String getValue() {
      return this.value;
   }

   public void selectAll() {
      this.selectionAnchor = 0;
      this.cursor = this.value.length();
   }

   public int getSelectionEnd() {
      return Math.max(this.cursor, this.selectionAnchor);
   }

   public TextSetting withLengthLimits(int minLength, int maxLength) {
      this.minLength = Math.max(0, minLength);
      this.maxLength = Math.max(this.minLength, maxLength);
      if (this.value.length() > this.maxLength) {
         this.setValue(this.value.substring(0, this.maxLength));
      }

      return this;
   }

   public int getSelectionStart() {
      return Math.min(this.cursor, this.selectionAnchor);
   }

   protected TextSetting(String name, String description, String value, int maxLength) {
      super(name, description);
      this.maxLength = Math.max(0, maxLength);
      this.setValue(value);
   }

   public TextSetting setValue(String value) {
      String normalizedValue = value == null ? "" : value;
      if (normalizedValue.length() > this.maxLength) {
         normalizedValue = normalizedValue.substring(0, this.maxLength);
      }

      if (!normalizedValue.equals(this.value)) {
         this.value = normalizedValue;
         this.notifyChanged();
      }

      this.cursor = Math.min(this.cursor, this.value.length());
      this.selectionAnchor = Math.min(this.selectionAnchor, this.value.length());
      return this;
   }

   public String getText() {
      return this.value;
   }

   public void moveToEnd(boolean extendSelection) {
      this.cursor = this.value.length();
      if (!extendSelection) {
         this.selectionAnchor = this.cursor;
      }
   }

   public void moveCursor(int offset, boolean extendSelection) {
      this.cursor = Math.max(0, Math.min(this.value.length(), this.cursor + offset));
      if (!extendSelection) {
         this.selectionAnchor = this.cursor;
      }
   }
}
