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

# AI 관련 ProGuard 규칙 추가
# proguard-rules.pro 파일에 추가할 내용

# === TensorFlow Lite 관련 규칙 ===

# TensorFlow Lite 핵심 클래스 보호
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-keep class org.tensorflow.lite.support.** { *; }

# TensorFlow Lite 네이티브 라이브러리 보호
-keep class org.tensorflow.lite.nnapi.** { *; }
-dontwarn org.tensorflow.lite.**

# === ML Kit 관련 규칙 ===

# Google ML Kit 텍스트 인식 보호
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**

# === AI 모델 관련 클래스 보호 ===

# 우리 AI 패키지의 모든 클래스 보호
-keep class com.example.myapplication.ai.** { *; }

# 예측 결과 데이터 클래스들 보호
-keep class com.example.myapplication.ai.*$PredictionResult { *; }
-keep class com.example.myapplication.ai.*$MonthlyPrediction { *; }
-keep class com.example.myapplication.ai.*$CategoryPrediction { *; }

# Firebase Timestamp 관련 (AI에서 사용)
-keep class com.google.firebase.Timestamp { *; }

# === 수학 라이브러리 보호 ===

# Apache Commons Math (AI 계산용)
-keep class org.apache.commons.math3.** { *; }
-dontwarn org.apache.commons.math3.**

# === 일반적인 최적화 규칙 ===

# 로깅 제거 (릴리즈 빌드에서)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Kotlin 관련
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# === 직렬화 관련 ===

# Gson 사용 클래스들 (AI 설정 저장용)
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }

# 우리 데이터 클래스들의 필드명 보호
-keepclassmembers class com.example.myapplication.ai.** {
    !transient <fields>;
}

# Enum 클래스 보호
-keepclassmembers enum com.example.myapplication.ai.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# === 네이티브 메서드 보호 ===

# JNI 관련 (TensorFlow Lite에서 사용)
-keepclasseswithmembernames class * {
    native <methods>;
}

# === 리플렉션 사용 클래스 보호 ===

# 리플렉션으로 접근되는 클래스들 보호
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# === 성능 최적화 ===

# 사용하지 않는 코드 제거
-dontshrink
-dontoptimize
-dontobfuscate

# 디버그 정보 제거 (릴리즈 빌드)
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# === 경고 무시 ===

# 불필요한 경고들 무시
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**