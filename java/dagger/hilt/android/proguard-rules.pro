# Keep for the reflective cast done in EntryPoints.
# See b/183070411#comment4 for more info.
-keep,allowobfuscation,allowshrinking @dagger.hilt.android.EarlyEntryPoint class *
-assumenosideeffects class dagger.hilt.android.internal.managers.TestInjectInterceptor { *; }