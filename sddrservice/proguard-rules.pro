# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /home/tslilyai/Android/Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

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

-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# for Java lambdas
-dontwarn java.lang.invoke.**

-keep class org.mpisws.sddrservice.** { *; }

# for extending BaseActivity
-keepclasseswithmembers class com.microsoft.embeddedsocial.ui.activity.base.BaseActivity {
  public <methods>;
}

# for error reporting
-keepclasseswithmembers class com.microsoft.embeddedsocial.server.model.view.TopicView {
  public <methods>;
}

# for exception types
-keep class com.microsoft.embeddedsocial.server.exception.** { *; }

# for search
-keep public class com.microsoft.embeddedsocial.provider.AbstractEmbeddedSocialSearchSuggestionProvider { *; }

# dont obfuscate enums
-keepclassmembers enum com.microsoft.embeddedsocial.** { *; }

# for xml
-keepattributes Signature
-keepnames class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.databind.**
-keep class org.codehaus.** { *; }
-keepclassmembers public final enum org.codehaus.jackson.annotate.JsonAutoDetect$Visibility {
    public static final org.codehaus.jackson.annotate.JsonAutoDetect$Visibility *;
}

# for otto event bus
-keepattributes *Annotation*
-keepclassmembers class ** {
    @com.squareup.otto.Subscribe public *;
    @com.squareup.otto.Produce public *;
}

# ormlite
-keep class com.j256.**
-keepclassmembers class com.j256.** { *; }
-keep enum com.j256.**
-keepclassmembers enum com.j256.** { *; }
-keep interface com.j256.**
-keepclassmembers interface com.j256.** { *; }
-keepclassmembers class * {
  public <init>(android.content.Context);
 }
