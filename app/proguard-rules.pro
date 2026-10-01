# Gson model classes are serialised reflectively – keep their fields and names.
-keepclassmembers,allowobfuscation class com.example.pinboardkeyboard.data.** {
    <fields>;
    <init>(...);
}
-keep class com.example.pinboardkeyboard.data.PinItem { *; }
-keep class com.example.pinboardkeyboard.data.PinCategory { *; }

# Gson generic type tokens
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-dontwarn sun.misc.**
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Tink / security-crypto
-dontwarn com.google.api.client.http.**
-dontwarn org.joda.time.**
