# R8 keep rules for the ShelfScan release build.
#
# Most dependencies (Ktor/OkHttp, kotlinx-serialization, ML Kit via Play
# services) ship their own consumer rules; the rules here cover the gaps —
# chiefly the reflection-based serializer lookup for our own @Serializable
# DTOs (OpenLibrary response DTOs and the export MediaItemDto in :shared).

# --- kotlinx-serialization ---------------------------------------------------
# Keep generated serializers and Companion serializer() lookups for app DTOs.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

-keepclassmembers @kotlinx.serialization.Serializable class com.shelfscan.** {
    *** Companion;
}
-keepclasseswithmembers class com.shelfscan.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.shelfscan.**$$serializer { *; }

# --- Ktor / coroutines -------------------------------------------------------
# Ktor references optional logging/management classes that are absent at
# runtime; silence the missing-class warnings rather than keep them.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**

# --- ML Kit ------------------------------------------------------------------
# ML Kit text recognition loads implementation classes reflectively.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
