plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

// Kotlin の警告が出たら, ビルドを失敗させる (core と同じ)
kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

android {
    namespace = "jp.tspapp.app"
    // build-tools は, Docker のイメージに入っている版に固定する (SDK は読み取り専用で, 既定の版 (36.0.0) は, 入っていない)
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        // applicationId は, 仮の値 (リリースの前に決める)
        applicationId = "jp.tspapp.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    // 警告も, エラーにする
    lint {
        warningsAsErrors = true
        abortOnError = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core)
    // Material 3 の部品とテーマ (Compose は使わない)
    implementation(libs.material)
    implementation(libs.androidx.appcompat)
    // Dispatchers.Main (画面のスレッド) と, 解く処理の裏のスレッド
    implementation(libs.coroutines.android)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
    testRuntimeOnly(libs.junit.launcher)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}
