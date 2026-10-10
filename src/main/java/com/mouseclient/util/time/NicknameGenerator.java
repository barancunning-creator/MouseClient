package com.mouseclient.util.time;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Locale;

public final class NicknameGenerator {
   private static final NicknameGenerator.SyllableSet[] SYLLABLE_SETS = new NicknameGenerator.SyllableSet[]{
      new NicknameGenerator.SyllableSet(
         new String[]{
            ("b"),
            ("d"),
            ("f"),
            ("g"),
            ("h"),
            ("k"),
            ("l"),
            ("m"),
            ("n"),
            ("p"),
            ("r"),
            ("s"),
            ("t"),
            ("v"),
            ("w"),
            ("br"),
            ("cl"),
            ("cr"),
            ("dr"),
            ("fl"),
            ("gr"),
            ("pl"),
            ("sl"),
            ("sn"),
            ("st"),
            ("tr"),
            ("th"),
            ("sh"),
            ("ch"),
            ("wh")
         },
         new String[]{
            ("a"),
            ("e"),
            ("i"),
            ("o"),
            ("u"),
            ("ea"),
            ("ee"),
            ("oo"),
            ("ai"),
            ("ay"),
            ("ow")
         },
         new String[]{
            "",
            "",
            ("n"),
            ("r"),
            ("l"),
            ("s"),
            ("t"),
            ("ck"),
            ("ng"),
            ("st"),
            ("nd"),
            ("rk"),
            ("sh"),
            ("ll"),
            ("x")
         }
      ),
      new NicknameGenerator.SyllableSet(
         new String[]{
            ("b"),
            ("d"),
            ("g"),
            ("k"),
            ("l"),
            ("m"),
            ("n"),
            ("p"),
            ("r"),
            ("s"),
            ("t"),
            ("v"),
            ("z"),
            ("zh"),
            ("sh"),
            ("ch"),
            ("kr"),
            ("gr"),
            ("dr"),
            ("st"),
            ("sk"),
            ("sv"),
            ("tr"),
            ("pr"),
            ("br")
         },
         new String[]{
            ("a"),
            ("e"),
            ("i"),
            ("o"),
            ("u"),
            ("ya"),
            ("yo"),
            ("yu"),
            ("ei"),
            ("ai")
         },
         new String[]{
            "",
            "",
            "",
            ("n"),
            ("r"),
            ("l"),
            ("k"),
            ("s"),
            ("v"),
            ("m"),
            ("sh"),
            ("ch"),
            ("x"),
            ("y")
         }
      ),
      new NicknameGenerator.SyllableSet(
         new String[]{
            ("k"),
            ("z"),
            ("x"),
            ("v"),
            ("r"),
            ("n"),
            ("q"),
            ("j"),
            ("kr"),
            ("zr"),
            ("vr"),
            ("sk"),
            ("dr"),
            ("th"),
            ("ph"),
            ("y")
         },
         new String[]{("a"), ("e"), ("i"), ("o"), ("y"), ("ae"), ("io")},
         new String[]{
            "",
            ("x"),
            ("z"),
            ("k"),
            ("n"),
            ("r"),
            ("th"),
            ("rn"),
            ("ks"),
            ("q"),
            ("sh")
         }
      ),
      new NicknameGenerator.SyllableSet(
         new String[]{
            ("l"),
            ("m"),
            ("n"),
            ("r"),
            ("s"),
            ("t"),
            ("v"),
            ("d"),
            ("k"),
            ("b"),
            ("p"),
            ("f"),
            ("c"),
            ("g")
         },
         new String[]{
            ("a"),
            ("e"),
            ("i"),
            ("o"),
            ("u"),
            ("ia"),
            ("io"),
            ("ea"),
            ("ie")
         },
         new String[]{"", "", "", "", ("n"), ("l"), ("r"), ("s"), ("m")}
      )
   };
   private final SecureRandom random = new SecureRandom();
   private final HashSet<String> usedNames = new HashSet<>();

   private String pickString(String[] choices) {
      return choices[this.random.nextInt(choices.length)];
   }

   private int weightedIndex(int... weights) {
      int totalWeight = 0;

      for (int weight : weights) {
         totalWeight += weight;
      }

      int draw = this.random.nextInt(totalWeight);

      for (int index = 0; index < weights.length; index++) {
         draw -= weights[index];
         if (draw < 0) {
            return index;
         }
      }

      return weights.length - 1;
   }

   private String shortName() {
      StringBuilder textBuilder = new StringBuilder();
      int length = 3 + this.random.nextInt(3);

      for (int index = 0; index < length; index++) {
         textBuilder.append(this.pickCharacter(index % 2 == 1 ? ("aeiouy") : ("bdfgklmnprstvxz")));
      }

      return textBuilder + (this.chance(50) ? String.valueOf(this.random.nextInt(100)) : "");
   }

   private String decorate(String base) {
      String decorated = base;
      if (this.chance(22) && Character.isLetter(decorated.charAt(decorated.length() - 1))) {
         decorated = decorated + this.optionalUnderscore() + this.numberSuffix();
      }

      if (this.chance(7)) {
         decorated = this.randomLeet(decorated);
      }

      if (this.chance(6)) {
         decorated = this.duplicateLetter(decorated);
      }
      decorated = switch (this.weightedIndex(50, 30, 17, decorated.length() <= 8 ? 3 : 0)) {
         case 0 -> decorated.toLowerCase(Locale.ROOT);
         case 1 -> Character.toUpperCase(decorated.charAt(0)) + decorated.substring(1).toLowerCase(Locale.ROOT);
         case 2 -> this.titleCase(decorated);
         default -> decorated.toUpperCase(Locale.ROOT);
      };
      if (this.chance(3)) {
         decorated = "x" + decorated + "x";
      }

      decorated = decorated.replaceAll(("[^A-Za-z0-9_]"), "").replaceAll(("_+"), ("_")).replaceAll(("^_|_$"), "");
      return decorated.length() > 16 ? decorated.substring(0, 16).replaceAll(("_$"), "") : decorated;
   }

   private String duplicateLetter(String name) {
      int index = this.random.nextInt(name.length());
      return Character.isLetter(name.charAt(index)) ? name.substring(0, index + 1) + name.charAt(index) + name.substring(index + 1) : name;
   }

   private String baseName() {
      NicknameGenerator.SyllableSet syllables = SYLLABLE_SETS[this.random.nextInt(SYLLABLE_SETS.length)];

      return switch (this.weightedIndex(30, 20, 18, 12, 10, 10)) {
         case 0 -> this.syllabicName(syllables, 2 + this.random.nextInt(2));
         case 1 -> this.syllabicName(syllables, 1 + this.random.nextInt(2)) + this.optionalUnderscore() + this.numberSuffix();
         case 2 -> this.syllabicName(syllables, 1 + this.random.nextInt(2))
            + this.optionalUnderscore()
            + this.syllabicName(SYLLABLE_SETS[this.random.nextInt(SYLLABLE_SETS.length)], 1 + this.random.nextInt(2));
         case 3 -> this.shortName();
         case 4 -> this.syllabicName(syllables, 1) + this.syllabicName(syllables, 1) + this.random.nextInt(10);
         default -> this.syllabicName(syllables, 2) + this.pickCharacter(("xyzkqo"));
      };
   }

   private String randomLeet(String name) {
      StringBuilder textBuilder = new StringBuilder();

      for (char character : name.toCharArray()) {
         char replacement = switch (Character.toLowerCase(character)) {
            case 'a' -> '4';
            case 'e' -> '3';
            case 'i' -> '1';
            case 'o' -> '0';
            case 's' -> '5';
            default -> character;
         };
         textBuilder.append(this.chance(45) ? replacement : character);
      }

      return textBuilder.toString();
   }

   private String optionalUnderscore() {
      return this.chance(25) ? ("_") : "";
   }

   private String syllabicName(NicknameGenerator.SyllableSet syllables, int syllableCount) {
      StringBuilder textBuilder;
      do {
         textBuilder = new StringBuilder();
         if (this.chance(8)) {
            textBuilder.append(this.pickCharacter(("aeiou")));
         }

         for (int syllableIndex = 0; syllableIndex < syllableCount; syllableIndex++) {
            textBuilder.append(this.pickString(syllables.onsets())).append(this.chance(75) ? String.valueOf(this.pickCharacter(("aeiou"))) : this.pickString(syllables.vowels()));
            if (syllableIndex == syllableCount - 1 || this.chance(15)) {
               textBuilder.append(this.pickString(syllables.codas()));
            }
         }
      } while (textBuilder.length() < 3 || textBuilder.length() > 3 + syllableCount * 2);

      return textBuilder.toString();
   }

   private String numberSuffix() {
      return switch (this.weightedIndex(28, 26, 18, 16, 12)) {
         case 0 -> String.valueOf(2003 + this.random.nextInt(13));
         case 1 -> String.valueOf(this.random.nextInt(100));
         case 2 -> String.format(Locale.ROOT, ("%02d"), this.random.nextInt(32));
         case 3 -> String.valueOf(100 + this.random.nextInt(900));
         default -> String.valueOf(this.random.nextInt(10)).repeat(2 + this.random.nextInt(2));
      };
   }

   private String titleCase(String name) {
      StringBuilder textBuilder = new StringBuilder();
      boolean capitalizeNext = true;

      for (char character : name.toCharArray()) {
         if (!Character.isLetter(character)) {
            capitalizeNext = character == '_';
            textBuilder.append(character);
         } else {
            textBuilder.append(capitalizeNext ? Character.toUpperCase(character) : Character.toLowerCase(character));
            capitalizeNext = false;
         }
      }

      return textBuilder.toString();
   }

   private boolean chance(int percent) {
      return this.random.nextInt(100) < percent;
   }

   public String generate() {
      String nickname;
      do {
         nickname = this.decorate(this.baseName());
      } while (nickname.length() < 3 || nickname.length() > 16 || !this.usedNames.add(nickname.toLowerCase(Locale.ROOT)));

      return nickname;
   }

   private char pickCharacter(String characters) {
      return characters.charAt(this.random.nextInt(characters.length()));
   }

   private record SyllableSet(String[] onsets, String[] vowels, String[] codas) {
   }
}
