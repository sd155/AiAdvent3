plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

private val _java = libs.versions.java.get()

kotlin {
    jvmToolchain(_java.toInt())

    jvm("desktop")
    
    sourceSets {        
        commonMain.dependencies {
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.compose.viewmodel)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.negotiation)
            implementation(libs.ktor.client.serialization)
            implementation(libs.slf4j.simple)
            implementation(projects.frontend.features.utils)
            implementation("com.aallam.ktoken:ktoken:0.4.0")
        }
    }
} 
