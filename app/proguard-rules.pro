# Keep rules for RootPad Bridge.
# The app uses kotlinx.serialization for profile JSON.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.rootpad.bridge.core.** {
    *** Companion;
}
-keepclasseswithmembers class com.rootpad.bridge.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}
