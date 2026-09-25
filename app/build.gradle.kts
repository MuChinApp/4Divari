plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ir.chardivari.app"
    compileSdk = rootProject.extra["compileSdk"] as Int

    defaultConfig {
        applicationId = "ir.chardivari.app"
        minSdk = rootProject.extra["minSdk"] as Int
        targetSdk = rootProject.extra["targetSdk"] as Int
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "APP_ENVIRONMENT",
            "\"${(project.findProperty("4divari.environment") as String?) ?: "DEVELOPMENT"}\"",
        )
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${(project.findProperty("4divari.apiBaseUrl") as String?) ?: "https://api.4divari.ir/v1/"}\"",
        )
        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${(project.findProperty("4divari.supabaseUrl") as String?) ?: ""}\"",
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"${(project.findProperty("4divari.supabaseAnonKey") as String?) ?: ""}\"",
        )
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "APP_ENVIRONMENT", "\"DEVELOPMENT\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField(
                "String",
                "APP_ENVIRONMENT",
                "\"${(project.findProperty("4divari.releaseEnvironment") as String?) ?: "PRODUCTION"}\"",
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
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Module graph
    implementation(project(":core:common"))
    implementation(project(":core:environment"))
    implementation(project(":core:network"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    implementation(project(":core:analytics"))
    implementation(project(":core:auth"))
    implementation(project(":core:marketplace"))
    implementation(project(":feature:home"))
    implementation(project(":feature:search"))
    implementation(project(":feature:saved"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:property"))
    implementation(project(":feature:seller"))
    implementation(project(":feature:agent"))
    implementation(project(":feature:messaging"))

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)

    // DI
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
