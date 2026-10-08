# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
# Apache MINA SSHD references these optional desktop-Java/JMX types.
# javax.management is not provided by the Android runtime.
-dontwarn javax.management.MBeanException
-dontwarn javax.management.ReflectionException

# Keep methods invoked by WebView's reflection-based JavaScript bridge in release builds.
-keepclassmembers class com.erikraft.drop.JavaScriptInterface {
    @android.webkit.JavascriptInterface <methods>;
}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# Apache Mina SSHD and Apache FtpServer use service loading/reflection internally.
# Keep their runtime implementation classes intact in minified release builds.
-keep class org.apache.sshd.** { *; }
-keep class org.apache.ftpserver.** { *; }

# Apache MINA's NIO transport discovers processor implementations reflectively.
# Preserve implementation names and public constructors in minified release builds.
-keep class org.apache.mina.** { *; }

# Bouncy Castle is used by FTPS certificate generation and Apache SSHD.
# Android also ships a platform crypto provider, so do not let R8 rewrite
# the bundled provider classes used by the app.
-keep class org.bouncycastle.** { *; }
-dontwarn org.apache.sshd.**
-dontwarn org.apache.ftpserver.**
-dontwarn org.bouncycastle.**

