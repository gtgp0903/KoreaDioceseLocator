plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "kr.catholic.dioceselocator"
    compileSdk = 37

    defaultConfig {
        applicationId = "kr.catholic.dioceselocator"
        minSdk = 26
        targetSdk = 37
        versionCode = 6
        versionName = "0.6.0"
    }

    val remoteDataUrl = providers.gradleProperty("KDL_REMOTE_DATA_URL")
        .orElse("https://raw.githubusercontent.com/gtgp0903/KoreaDioceseLocator/main/data/remote_data.json")
        .get()
    defaultConfig {
        buildConfigField(
            "String",
            "REMOTE_DATA_URL",
            "\"${remoteDataUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.activity:activity-compose:1.12.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.google.android.gms:play-services-location:21.4.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
