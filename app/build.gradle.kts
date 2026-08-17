import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

private val externalSigningFile = project.file(System.getProperty("user.home"))
    .resolve(".config/notes-escape/signing.properties")
private val externalSigningProperties = Properties().also { properties ->
    if (externalSigningFile.isFile) {
        FileInputStream(externalSigningFile).use { properties.load(it) }
        val required = listOf("storeFile", "keyAlias", "storePassword", "keyPassword")
        require(required.all { !properties.getProperty(it).isNullOrBlank() }) {
            "External release signing configuration is incomplete"
        }
    }
}

android {
    namespace = "com.notesescape.sdocx"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.notesescape.sdocx"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (externalSigningFile.isFile) {
            create("release") {
                storeFile = file(externalSigningProperties.getProperty("storeFile"))
                storePassword = externalSigningProperties.getProperty("storePassword")
                keyAlias = externalSigningProperties.getProperty("keyAlias")
                keyPassword = externalSigningProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = true
            }
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
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

dependencies {
    implementation(project(":sdocx-core"))
    implementation(project(":export-core"))
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.graphics:graphics-path:1.1.0")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
