# Packet Bastion ProGuard/R8 rules.

# kotlinx.serialization: keep generated serializers for our @Serializable models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.cyopstd.game.** {
    *** Companion;
}
-keepclasseswithmembers class com.cyopstd.game.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.cyopstd.game.save.**$$serializer { *; }
-keep class com.cyopstd.game.save.** { *; }

# Compose keeps what it needs through its own consumer rules; nothing extra required.
