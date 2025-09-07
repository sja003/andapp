plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // TensorFlow Lite 최적화
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module"
            )
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            buildConfigField("boolean", "AI_DEBUG", "true")
        }
    }

    configurations.all {
        exclude(group = "com.android.support", module = "support-compat")
        exclude(group = "org.tensorflow", module = "tensorflow-lite-metadata")

        // ✅ Google API 충돌 해결을 위한 제외 설정
        exclude(group = "com.google.guava", module = "listenablefuture")
        exclude(group = "org.apache.httpcomponents", module = "httpclient")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
        mlModelBinding = true
        buildConfig = true
    }

    // TensorFlow Lite 모델 최적화
    aaptOptions {
        noCompress += "tflite"
        noCompress += "lite"
    }
}

dependencies {
    // ✅ AndroidX 라이브러리 (안정 버전)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // ✅ Navigation 컴포넌트
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.6")

    // ✅ UI 라이브러리
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.annotation:annotation:1.7.1")
    implementation("com.google.android.material:material:1.11.0")

    // ✅ Firebase (BOM 사용으로 버전 통일)
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    // ✅ Google Sign-In
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // ✅ Google API 라이브러리 (호환되는 버전으로 수정)
    implementation("com.google.api-client:google-api-client-android:1.32.1") {
        exclude(group = "org.apache.httpcomponents", module = "httpclient")
        exclude(group = "com.google.guava", module = "listenablefuture")
    }
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.32.1")
    implementation("com.google.http-client:google-http-client-android:1.42.3")
    implementation("com.google.http-client:google-http-client-gson:1.42.3")

    // ✅ Google Calendar API (호환되는 버전)
    implementation("com.google.apis:google-api-services-calendar:v3-rev20220715-2.0.0") {
        exclude(group = "com.google.guava", module = "listenablefuture")
        exclude(group = "org.apache.httpcomponents", module = "httpclient")
    }

    // ✅ 네트워킹 (Retrofit + OkHttp)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")

    // ✅ 차트 라이브러리
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // ✅ TensorFlow Lite (안정 버전)
    implementation("org.tensorflow:tensorflow-lite:2.13.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // ✅ ML Kit
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:text-recognition-korean:16.0.0")

    // ✅ 수학 연산 라이브러리
    implementation("org.apache.commons:commons-math3:3.6.1")

    // ✅ 테스트 라이브러리
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}