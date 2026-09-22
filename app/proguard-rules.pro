# Packet Bastion ProGuard/R8 rules.

# kotlinx.serialization: keep generated serializers for our @Serializable models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.packetbastion.asciidefense.** {
    *** Companion;
}
-keepclasseswithmembers class com.packetbastion.asciidefense.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.packetbastion.asciidefense.save.**$$serializer { *; }
-keep class com.packetbastion.asciidefense.save.** { *; }

# Compose keeps what it needs through its own consumer rules; nothing extra required.
