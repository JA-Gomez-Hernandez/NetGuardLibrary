# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /home/marcel/Android/Sdk/tools/proguard/proguard-android.txt
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

#Line numbers
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

#AMon
-keepnames class es.ugr.mdsm.amon.** { *; }
-keep class es.ugr.mdsm.application.** { *; }
-keep class es.ugr.mdsm.connectivity.** { *; }
-keep class es.ugr.mdsm.ecosystem.** { *; }
-keep class es.ugr.mdsm.hardware.** { *; }
-keep class es.ugr.mdsm.amon.Version { *; }

#NetGuard
-keepnames class eu.faircode.netguard.** { *; }

#JNI
-keepclasseswithmembernames class * {
    native <methods>;
}

#JNI callbacks
-keep class eu.faircode.netguard.Allowed { *; }
-keep class eu.faircode.netguard.Packet { *; }
-keep class eu.faircode.netguard.ResourceRecord { *; }
-keep class eu.faircode.netguard.Usage { *; }
-keep class eu.faircode.netguard.ServiceSinkhole {
    void nativeExit(java.lang.String);
    void nativeError(int, java.lang.String);
    void logPacket(eu.faircode.netguard.Packet);
    void dnsResolved(eu.faircode.netguard.ResourceRecord);
    boolean isDomainBlocked(java.lang.String);
    eu.faircode.netguard.Allowed isAddressAllowed(eu.faircode.netguard.Packet);
    void accountUsage(eu.faircode.netguard.Usage);
    void captureFlow(eu.faircode.netguard.Flow);
}

#AndroidX
-keep class androidx.appcompat.widget.** { *; }
-keep class androidx.appcompat.app.AppCompatViewInflater { <init>(...); }
-keepclassmembers class * implements android.os.Parcelable { static ** CREATOR; }

#Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep enum com.bumptech.glide.** {*;}
#-keep public enum com.bumptech.glide.load.resource.bitmap.ImageHeaderParser$** {
#    **[] $VALUES;
#    public *;
#}

#AdMob
-dontwarn com.google.android.gms.internal.**

