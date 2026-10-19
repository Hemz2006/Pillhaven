plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

import java.util.Properties

// The Play upload key: path and passwords live in the git-ignored keystore.properties (see docs/play-store-launch.md).
val keystoreFile: File = rootProject.file("keystore.properties")
val keystore = Properties().apply { if (keystoreFile.exists()) keystoreFile.inputStream().use(::load) }

android {
    namespace = "com.medtrack"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hemzzdevv.pillhaven"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // 64-bit ARM only (every current Android phone): the text-recognition library's native code is ~11 MB per
        // processor type, so shipping all four would quadruple the app.
        ndk { abiFilters += "arm64-v8a" }
    }
    signingConfigs {
        if (keystoreFile.exists()) {
            create("upload") {
                storeFile = file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            // Optimised and not debuggable: much faster than the debug build, and its data can't be read over USB.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the Play upload key; a release build without it fails (below) rather than using the debug key.
            signingConfig = signingConfigs.findByName("upload")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

// A release must be signed with the upload key and carry the brand list (git-ignored, built by tools/build_brand_index.py);
// without it brand search would silently find nothing for every Play user. Debug builds need neither.
val brandIndex: File = file("src/main/assets/brands.db")
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        if (!keystoreFile.exists()) {
            throw GradleException("keystore.properties is missing: release builds need the upload key (see docs/play-store-launch.md)")
        }
        if (!brandIndex.exists()) {
            throw GradleException("app/src/main/assets/brands.db is missing: run tools/build_brand_index.py first")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // On-device text recognition for reading prescriptions (model bundled: works offline, no Google Play services).
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit-ktx:1.2.1")
}
