-dontwarn com.google.errorprone.annotations.MustBeClosed
# These are rules to enable instrumentation test to run while main app is optimized
 # TODO(b/324097623) Remove the keep rules once test won't be affected by obfuscation
-keep class kotlin.**
-keep class com.google.common.util.concurrent.ListenableFuture {
    <methods>;
}