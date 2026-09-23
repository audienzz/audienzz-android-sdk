# GMA request targeting isolation locates fields by type and object identity, not their names.
# Do not inline or eliminate the state holder / targeting Bundle during application shrinking.
-keepclassmembers,allowobfuscation class com.google.android.gms.ads.AdRequest {
    <fields>;
}
-keepclassmembers,allowobfuscation class com.google.android.gms.ads.internal.client.** {
    android.os.Bundle *;
}
