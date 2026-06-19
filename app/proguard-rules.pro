# SidebedLight ProGuard / R8 rules.
#
# AndroidX, Compose, Kotlin and DataStore ship their own consumer rules, so the
# default optimized config is enough for this app. Components referenced from the
# manifest (Service, BroadcastReceivers, Activities) are kept automatically by R8.
#
# Keep line numbers for readable crash stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
