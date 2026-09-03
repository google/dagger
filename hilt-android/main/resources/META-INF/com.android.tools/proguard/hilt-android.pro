# Keep for the reflective cast done in EntryPoints.
# See b/183070411#comment4 for more info.
-keep,allowobfuscation,allowshrinking @dagger.hilt.internal.ComponentEntryPoint class *
-keep,allowobfuscation,allowshrinking @dagger.hilt.internal.GeneratedEntryPoint class *
-keep,allowobfuscation,allowshrinking @dagger.hilt.android.EarlyEntryPoint class *

# Prevent R8 full mode from vertically merging Hilt_* base classes with
# @AndroidEntryPoint user classes. The bytecode transform rewrites user classes
# to extend Hilt_*; vertical class merging breaks this hierarchy.
-keep,allowobfuscation,allowshrinking class **.Hilt_*