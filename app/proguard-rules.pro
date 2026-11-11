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
#
# --- SportRecorder -------------------------------------------------------------------------
# R8 runs on release builds (see app/build.gradle.kts) and this app needs no rules of its own
# today: everything it reaches by reflection — Room's generated database, the WorkManager worker,
# the manifest components, the kotlinx-serialization serializers — is kept by the rules the
# libraries ship and by AAPT's manifest rules. The commented-out -keepattributes and
# -renamesourcefileattribute lines above stay off on purpose: R8's mapping file already carries
# source-file and line information, so a release stack trace retraces without them.
#
# Add a rule here only after a minified build is seen to break, and keep it as narrow as the
# class that broke. A package-wide -keep drags down the shrinking / obfuscation / optimization
# percentages that Google Play's "DEX code optimization" vital checks.
