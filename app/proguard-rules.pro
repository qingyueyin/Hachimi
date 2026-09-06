# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class kotlinx.serialization.** { *; }

# jaudiotagger
-keep class org.jaudiotagger.** { *; }
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**

# Google Error Prone annotations (transitive from Tink/crypto libs)
-dontwarn com.google.errorprone.annotations.**
