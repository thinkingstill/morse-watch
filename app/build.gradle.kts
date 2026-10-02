import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wearmos"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.wearmos"
        minSdk = 30
        targetSdk = 30
        versionCode = 1
        versionName = "0.1.0"

        // 设备为 32 位 ARMv7（OWW212 只有 armeabi-v7a）。
        // 当前无 native 代码，此处为将来引入 .so 时的护栏。
        ndk {
            abiFilters += "armeabi-v7a"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // 仅测试期依赖，不会进 APK。App 本身保持零第三方依赖。
    testImplementation("junit:junit:4.13.2")
}
