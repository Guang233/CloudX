import com.android.build.api.variant.BuildConfigField
import java.io.ByteArrayOutputStream
import java.util.UUID

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("kotlin-parcelize")
}

val appVersion = "0.0.3"
val currentBuildUuid = UUID.randomUUID().toString()
val currentBuildTimestamp = System.currentTimeMillis()


android {
    namespace = "com.guang.cloudx"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.guang.cloudx"
        minSdk = 24
        targetSdk = 36
        versionCode = getGitCommitCount()
        versionName = appVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        BuildConfigField("String", "BUILD_UUID", "\"$currentBuildUuid\"")
        BuildConfigField("long", "BUILD_TIMESTAMP", "$currentBuildTimestamp")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("com.google.android.material:material:1.14.0-alpha05")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.github.Adonai:jaudiotagger:2.3.15")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation(platform("androidx.compose:compose-bom:2025.09.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.retrofit2:converter-scalars:3.0.0")
    implementation("com.squareup.okhttp3:okhttp-brotli:5.1.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.github.bumptech.glide:glide:5.0.5")
    implementation("androidx.activity:activity:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    annotationProcessor("com.github.bumptech.glide:compiler:5.0.5")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.09.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}


fun getGitCommitCount(): Int {
    return try {
        val stdout = ByteArrayOutputStream()
        @Suppress("DEPRECATION") exec {
            commandLine("git", "rev-list", "--count", "main")
            standardOutput = stdout
        }
        stdout.toString().trim().toInt()
    } catch (e: Exception) {
        3
    }
}

val adb: String = androidComponents.sdkComponents.adb.get().asFile.absolutePath