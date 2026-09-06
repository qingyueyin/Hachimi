# Debug proguard: shrink & optimize, but keep source/line info for crash reports
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keep class com.qing.hachimi.** { *; }
-dontwarn com.qing.hachimi.**
