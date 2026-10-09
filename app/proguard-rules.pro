# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# com.kazumaproject.core.domain.extensions.hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep class com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.user_template.database.UserTemplate { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.** { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export.** { *; }
-keep class com.kazumaproject.custom_keyboard.data.** { *; }
-keep enum com.kazumaproject.custom_keyboard.data.KeyboardLayoutUsageMode { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ClipboardHistoryItem { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.custom_romaji.database.RomajiMapEntity { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWord { *; }
# Gson reads these fields reflectively when importing both old and new NG word backups.
-keep class com.kazumaproject.markdownhelperkeyboard.ng_word.NgWordBackup$NgWordBackupEntry { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.setting_activity.backup.PrefBackup { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.setting_activity.backup.PrefEntry { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.database.SystemUserDictionaryEntry { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.ngram_rule.ui.NgramRuleBackup { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.ngram_rule.ui.NgramRuleBackupItem { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.ngram_rule.ui.LegacyNgramRuleBackup { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.ngram_rule.ui.TwoNodeRuleBackup { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.ngram_rule.ui.ThreeNodeRuleBackup { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.ngram_rule.ui.NodeFeatureInput { *; }

# Keep Gson generic type metadata and annotations used by backup import/export parsing.
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Legacy data compatibility: Pair fields may still appear in old JSON payloads.
-keep class kotlin.Pair { *; }

# Mochi API / device-local records are read reflectively by Gson in release builds.
-keep class com.kazumaproject.markdownhelperkeyboard.mochi.MochiImage { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.mochi.MochiAsset { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.mochi.MochiSearchPage { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.mochi.MochiPostImages { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.mochi.SavedImage { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.mochi.MochiSavedImages$State { *; }

# Gemma LiteRT-LM is loaded entirely via reflection from GemmaTranslationManager.
# Keep class names, members, and singleton fields such as NativeLibraryLoader.INSTANCE
# so release builds behave the same as debug builds.
-keep class com.google.ai.edge.litertlm.** { *; }

# LiteRT-LM references kotlin-reflect helper APIs from code paths we do not use.
-dontwarn kotlin.reflect.full.KClasses

# Release builds must not log editor/composing data from inherited diagnostics.
-assumenosideeffects class timber.log.Timber {
    public static void v(...);
    public static void d(...);
    public static void i(...);
    public static void w(...);
    public static void e(...);
    public static void wtf(...);
    public static void log(...);
}
-assumenosideeffects class timber.log.Timber$Tree {
    public void v(...);
    public void d(...);
    public void i(...);
    public void w(...);
    public void e(...);
    public void wtf(...);
    public void log(...);
}
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}
