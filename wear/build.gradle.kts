plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sanluca.still.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sanluca.still"
        minSdk = 28 // Pixel Watch e dispositivi Wear OS 2.0+
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// Rinomina l'APK generato per l'orologio in still-wear.apk
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("still-wear.apk")
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Wear OS & Google Play Services Wearable Data Layer
    implementation("androidx.wear.compose:compose-material3:1.0.0-alpha28")
    implementation("androidx.wear.compose:compose-foundation:1.4.1")
    implementation("androidx.wear:wear:1.3.0")
    implementation("androidx.fragment:fragment-ktx:1.8.1")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")

    // Wear OS Tiles & Protolayout
    implementation("androidx.wear.tiles:tiles:1.4.0")
    implementation("androidx.wear.protolayout:protolayout:1.2.0")
    implementation("androidx.wear.protolayout:protolayout-material:1.2.0")
    implementation("com.google.guava:guava:33.2.1-android")

    debugImplementation(libs.androidx.ui.tooling)
}