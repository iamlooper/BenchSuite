# Protobuf lite: the runtime resolves message fields (e.g. ringBufferCapacity_) by
# their exact Java field name via Class.getDeclaredField(). R8 would rename those
# fields, causing NoSuchFieldException at runtime. Keep all members of every
# GeneratedMessageLite subclass and the lite runtime itself.
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keep class com.google.protobuf.** { *; }
-keepclassmembers class * implements com.google.protobuf.Internal.EnumLite {
    public static **[] values();
    public static ** forNumber(int);
}

# JNI bridge: Rust calls BridgeCallback.onEnvelope() by name via GetMethodID().
# R8 renaming that method causes a silent JNI lookup failure → 30s init timeout.
# Keep the interface, all its implementors, and NativeBridge's native declarations.
-keep interface io.github.iamlooper.benchsuite.engine.BridgeCallback { *; }
-keep class * implements io.github.iamlooper.benchsuite.engine.BridgeCallback { *; }
-keep class io.github.iamlooper.benchsuite.engine.NativeBridge { *; }

# kotlinx.serialization – official R8/ProGuard rules
# (https://github.com/Kotlin/kotlinx.serialization/blob/master/rules/common.pro)
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.internal.ClassValueReferences

# Keep Companion objects of @Serializable classes.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep named companions.
-if @kotlinx.serialization.internal.NamedCompanion class *
-keepclassmembers class * {
    static <1> *;
}

# Keep serializer() on companions (default and named).
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep INSTANCE.serializer() on serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep the descriptor field in generated serializers.
-keepclassmembers public class **$$serializer {
    private ** descriptor;
}

# Keep serializable SupabaseApi response models (LeaderboardRow) and their fields.
-keep @kotlinx.serialization.Serializable class io.github.iamlooper.benchsuite.data.remote.** { *; }

# supabase-kt: the SDK uses reflection for plugin resolution and Ktor engine bootstrapping.
# Keep the SDK's internal class hierarchy intact so R8 doesn't break plugin lookups.
-keep class io.github.jan.supabase.** { *; }

# Ktor: OkHttp engine internals and HTTP pipeline classes must survive R8.
-keep class io.ktor.** { *; }
-keep class kotlinx.serialization.** { *; }

# Ktor references JVM-only java.lang.management classes for IntelliJ debug detection
# (IntellijIdeaDebugDetector). These classes are not present on Android and will never
# be reached at runtime, but R8 flags them as missing during whole-program analysis.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean

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
# hide the original source file name.
#-renamesourcefileattribute SourceFile