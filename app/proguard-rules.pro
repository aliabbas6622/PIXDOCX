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

# --- PixDocx optimization rules (see OPTIMIZE_PERFORMANCE.md §7) ---

# Keep Room entities: reflection-free but keeps field names readable in
# crash reports; Room's generated code matches on column names.
-keep class com.example.data.model.** { *; }

# Moshi codegen uses the generated adapter; keep model classes it reflects on.
-keep class kotlin.Metadata { *; }

# Preserve coroutine internals on release (avoid crashed continuations).
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
