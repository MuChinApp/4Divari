# Add project specific ProGuard rules here.
# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class ir.chardivari.** {
    *** Companion;
}
-keepclasseswithmembers class ir.chardivari.** {
    kotlinx.serialization.KSerializer serializer(...);
}
