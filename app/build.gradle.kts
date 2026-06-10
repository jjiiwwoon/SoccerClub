plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.jjw.soccerclub"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jjw.soccerclub"
        // [변경] minSdk 33 → 26
        //   33 은 Android 13(Tiramisu) 이상만 지원하여 전체 기기의 약 30% 만 커버.
        //   26(Android 8.0 Oreo) 으로 낮추면 약 95% 커버.
        //   이 앱에서 사용하는 API 중 minSdk 26 미만이 필요한 것은 없다.
        //   (EdgeToEdge, WindowInsetsCompat 등은 AndroidX compat 라이브러리가 처리)
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // [변경] isMinifyEnabled false → true
            //   R8 이 미사용 코드 제거 + 난독화 + 최적화를 수행한다.
            //   Firebase/Glide/네이버지도 등 리플렉션 기반 라이브러리는
            //   proguard-rules.pro 에 keep 규칙을 추가해야 런타임 크래시를 방지한다.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // [변경] viewBinding 블록 제거
    //   viewBinding = true 가 선언돼 있었지만, 실제 코드는 전부 findViewById 를 사용.
    //   활성화만 하고 쓰지 않으면 빌드마다 *Binding 클래스가 불필요하게 생성된다.
    //   향후 Kotlin 전환 시 viewBinding 또는 Compose 를 도입할 계획이므로
    //   그때 다시 활성화한다.
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.annotation)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)

    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")

    implementation("com.naver.maps:map-sdk:3.21.0")

    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    implementation("com.facebook.shimmer:shimmer:0.5.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}