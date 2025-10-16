import java.util.Properties
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
}
private val _java = libs.versions.java.get()
private val _javaVersion = JavaVersion.toVersion(_java)
private val _jvmTarget = JvmTarget.fromTarget(_java)
private val _props = Properties()
private val _propFile = File(project.rootDir, "local.properties")
if (_propFile.exists()) {
    _props.load(_propFile.inputStream())
}
private val _apiKey = _props.getProperty("API_KEY") ?: error("Missing API_KEY in local.properties")

kotlin {
    jvmToolchain(_java.toInt())

    androidTarget {
        compilerOptions {
            jvmTarget.set(_jvmTarget)
        }
    }
    
    jvm()
    
    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(layout.buildDirectory.dir("generated/src"))
        }
        commonMain.dependencies {
            implementation(libs.compose.material)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

android {
    namespace = libs.versions.application.namespace.get()
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    signingConfigs {
        create("release") {
            storeFile = _props.getProperty("RELEASE_KEYSTORE_FILE")?.let { file(it) }
                ?: error("Missing RELEASE_KEYSTORE_FILE in local.properties")
            storePassword = _props.getProperty("RELEASE_KEYSTORE_PASSWORD")
                ?: error("Missing RELEASE_KEYSTORE_PASSWORD in local.properties")
            keyAlias = _props.getProperty("RELEASE_KEY_ALIAS")
                ?: error("Missing RELEASE_KEY_ALIAS in local.properties")
            keyPassword = _props.getProperty("RELEASE_KEY_PASSWORD")
                ?: error("Missing RELEASE_KEY_PASSWORD in local.properties")
        }
    }
    defaultConfig {
        applicationId = libs.versions.application.namespace.get()
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = libs.versions.application.version.code.get().toInt()
        versionName = libs.versions.application.version.name.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        val appName = libs.versions.application.name.get()
        getByName("release") {
             isMinifyEnabled = true
             isShrinkResources = true
             proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "../proguard-rules.pro")
             signingConfig = signingConfigs.getByName("release")
             resValue("String", "app_name", appName)
         }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            resValue("string", "app_name", "$appName debug")
        }
    }
    compileOptions {
        sourceCompatibility = _javaVersion
        targetCompatibility = _javaVersion
    }
}

compose.desktop {
    application {
        mainClass = "io.github.sd155.aiadvent3.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = libs.versions.application.namespace.get()
            packageVersion = libs.versions.application.version.name.get()
        }
    }
}

tasks.register("generateApiKey") {
    val outputDir = layout.buildDirectory.dir("generated/src")
    inputs.property("apiKey", _apiKey)
    outputs.dir(outputDir)

    doLast {
        val file = outputDir.get().asFile.resolve("io/github/sd155/aiadvent3/build/ApiKey.kt")
        file.parentFile.mkdirs()
        file.writeText("""
            package io.github.sd155.aiadvent3.build

            internal const val API_KEY = "$_apiKey"
        """.trimIndent())
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn("generateApiKey")
}
