plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    kotlin("android") version "1.9.23" 
}

android {
    namespace = "com.example.snapshot"
    compileSdk = 35

    defaultConfig {
        manifestPlaceholders += mapOf()
        applicationId = "com.example.snapshot"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Google Maps API 키 설정
        val mapsApiKey = project.findProperty("MAPS_API_KEY") as String? ?: ""
        externalNativeBuild {
            cmake {
                cppFlags += ""
            }
        }

        // 매니페스트에서 사용할 API 키 설정
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        viewBinding = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // 폰트 다운로드 설정
    dependencies {
        implementation("androidx.core:core:1.12.0")
    }
}

dependencies {
    // 다운로드 가능한 폰트를 사용하기 위한 의존성
    implementation("androidx.core:core:1.12.0")
    
    implementation(platform("com.google.firebase:firebase-bom:32.8.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.maps.android:android-maps-utils:2.3.0")

    // Google Play Services Tasks (Tasks API 제공)
    implementation("com.google.android.gms:play-services-tasks:18.1.0")
    
    // Kotlin 기본 의존성
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.23")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Gemini API 의존성
    implementation("com.google.ai.client.generativeai:generativeai:0.2.2")
    
    // Google Maps
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.1.0")
    
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    
    // CircleImageView 의존성
    implementation("de.hdodenhof:circleimageview:3.1.0")
    
    // SwipeRefreshLayout 의존성
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    
    // GeoFirestore 의존성
    implementation("com.github.imperiumlabs:GeoFirestore-Android:v1.5.0")
    
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata:2.7.0")
    implementation("androidx.navigation:navigation-fragment:2.7.7")
    implementation("androidx.navigation:navigation-ui:2.7.7")
    
    // 스플래시 스크린 의존성
    // implementation("androidx.core:core-splashscreen:1.0.1")
    
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}