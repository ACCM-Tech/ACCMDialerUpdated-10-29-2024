plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.testapp2"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.testapp2"
        minSdk = 21
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 28 // Update this to the latest stable SDK
        versionCode = 1
        versionName = "1.0.4"

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    // Optional: If you are using Kotlin, consider adding this

}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Logging library
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("com.android.volley:volley:1.2.1")
}