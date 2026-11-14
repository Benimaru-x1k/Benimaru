plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.benimaru.official"
    compileSdk {
        version = release(37)
    }

    signingConfigs {
        create("release") {
            storeFile = file("/home/debian/Downloads/Skeli.jks")   // your keystore
            storePassword = "October2003"
            keyAlias = "Skeli"
            keyPassword = "October2003"

            enableV1Signing = true   // forced on (optional with minSdk 24)
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true // optional, only for faster ADB incremental installs
        }
    }

    defaultConfig {
        applicationId = "com.benimaru.official"
        minSdk = 24
        targetSdk = 37
        versionCode = 40
        versionName = "5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
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
    implementation("com.unity3d.ads:unity-ads:4.16.6")
    implementation("com.google.android.gms:play-services-ads-identifier:18.1.0")
}
