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
    }
}
private val expectedReleaseKeyAlias = "notes_escape_upload"
private val requiredReleaseSigningKeys = listOf("storeFile", "keyAlias", "storePassword", "keyPassword")
private val releaseSigningConfigured =
    externalSigningFile.isFile &&
        requiredReleaseSigningKeys.all { !externalSigningProperties.getProperty(it).isNullOrBlank() }

android {
    namespace = "com.notesescape.sdocx"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.notesescape.sdocx"
        minSdk = 27
        targetSdk = 36
        versionCode = 14
        versionName = "1.2.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
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
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
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

tasks.register("verifyReleaseSigning") {
    inputs.property("releaseSigningConfigured", releaseSigningConfigured)
    inputs.property("releaseKeyAlias", externalSigningProperties.getProperty("keyAlias").orEmpty())
    inputs.property("expectedReleaseKeyAlias", expectedReleaseKeyAlias)
    inputs.property("signingFilePath", externalSigningFile.absolutePath)
    doLast {
        val configured = inputs.properties["releaseSigningConfigured"] as Boolean
        val alias = inputs.properties["releaseKeyAlias"] as String
        val expectedAlias = inputs.properties["expectedReleaseKeyAlias"] as String
        val signingFilePath = inputs.properties["signingFilePath"] as String
        check(configured) {
            "Notes Escape release signing requires a complete secure file at $signingFilePath"
        }
        check(alias == expectedAlias) {
            "Notes Escape release signing alias must be $expectedAlias, found $alias"
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn("verifyReleaseSigning")
}

dependencies {
    implementation(project(":sdocx-core"))
    implementation(project(":export-core"))
    implementation("com.android.billingclient:billing:9.1.0")
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
