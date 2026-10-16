import java.util.Properties
import java.io.FileInputStream
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "scam.detector.ketu"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    packaging{
        resources{
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    defaultConfig {
        applicationId = "scam.detector.ketu"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

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
    val properties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        properties.load(FileInputStream(localPropertiesFile))
    }
    signingConfigs {
        create("release") {
            storeFile = file(properties.getProperty("RELEASE_STORE_FILE") ?: "")
            storePassword = properties.getProperty("RELEASE_STORE_PASSWORD") ?: ""
            keyAlias = properties.getProperty("RELEASE_KEY_ALIAS") ?: ""
            keyPassword = properties.getProperty("RELEASE_KEY_PASSWORD") ?: ""
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            
            //Tell Gradle to sign the release build using the config above
            signingConfig = signingConfigs.getByName("release")
        }
    }
    
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation("androidx.activity:activity:1.9.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
}
