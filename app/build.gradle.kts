plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.benimaru.official"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.benimaru.official"
        minSdk = 24
        targetSdk = 37
        versionCode = 35
        versionName = "4.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation("dev.rikka.shizuku:api:13.1.0")
    implementation("dev.rikka.shizuku:provider:13.1.0")
    implementation("com.github.GrenderG:Toasty:1.5.2")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.unity3d.ads-mediation:mediation-sdk:9.5.0")
    implementation("com.google.android.gms:play-services-appset:16.0.0")
    implementation("com.google.android.gms:play-services-ads-identifier:18.1.0")
    implementation("com.google.android.gms:play-services-basement:18.1.0")
    // Only if you want Unity Ads as one of the networks LevelPlay fills from:
    implementation("com.unity3d.ads-mediation:unityads-adapter:5.15.0")
}
