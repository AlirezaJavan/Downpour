# Room generated implementations are referenced reflectively.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Database class * { *; }
-dontwarn androidx.room.paging.**

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Public API of the library must be retained for consumers.
-keep class io.github.alirezajavan.downpour.api.** { *; }

# Components declared in the merged manifest are instantiated by the framework.
-keep class io.github.alirezajavan.downpour.internal.service.DownloadService { <init>(); }
-keep class io.github.alirezajavan.downpour.internal.work.* { <init>(...); }
