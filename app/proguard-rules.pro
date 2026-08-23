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

# ═══════════ زاد — قواعد R8 ═══════════
# تتبع الأخطاء في Crashlytics/اللوجات محتاج أرقام الأسطر
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization (كل الـ DTOs بتاعة Supabase)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.example.**$$serializer { *; }
-keepclassmembers class com.example.** { *** Companion; }
-keepclasseswithmembers class com.example.** { kotlinx.serialization.KSerializer serializer(...); }

# Supabase KT (gotrue/postgrest/realtime بتعتمد reflection على النماذج)
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**
-keep interface io.ktor.** { *; }
-dontwarn io.ktor.**

# gson (لو مستخدم في أي مسار قديم)
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Room entities (أسماء الأعمدة في @ColumnInfo لازم تفضل)
-keep class com.example.data.Zad* { *; }
-keep class com.example.data.local.** { *; }

# SQLCipher
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# grammY/Deno side مش هنا، ده للأندرويد فقط

# OkHttp/Okio platform warnings
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
