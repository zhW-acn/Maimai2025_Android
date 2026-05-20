# 保留行号信息（便于崩溃日志定位）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 保留注解
-keepattributes *Annotation*

# 保留枚举
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留 Parcelable
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# 保留 Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
}

# DataBinding 保护
-keep class androidx.databinding.** { *; }
-keep class android.databinding.** { *; }
-keep class **.databinding.* { *; }

# 如果你的包名是 com.example.app
-keep class com.okaca.maimai.android.databinding.** { *; }

# ViewBinding 通常不需要特殊配置
# 但如果遇到问题，添加：
#-keep class androidx.viewbinding.** { *; }

# Hilt/Dagger 保护
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }

# 保持生成的组件类
-keep class * extends dagger.hilt.internal.GeneratedComponent
-keep class * implements dagger.hilt.internal.GeneratedComponent

# 保持 @Inject 构造函数
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
}

# 保持 @Provides 方法
-keepclassmembers class * {
    @dagger.Provides <methods>;
}

# Koin 保护
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# Kotlin Coroutines 保护
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# 如果使用了 Flow
-keep class kotlinx.coroutines.flow.** { *; }

# ViewModel 保护
-keep class * extends androidx.lifecycle.ViewModel {
    <init>();
}
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <methods>;
}

# LiveData 通常不需要特殊配置
# 但如果遇到问题：
-keep class androidx.lifecycle.** { *; }

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keep interface * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# SLF4J 的具体日志绑定在 Android 里不是必需类，R8 扫到引用时忽略警告即可。
-dontwarn org.slf4j.impl.StaticLoggerBinder
