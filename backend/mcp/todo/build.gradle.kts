import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinLibrary)
    alias(libs.plugins.ktor)
    application
    alias(libs.plugins.kotlinSerialization)
}

private val _java = libs.versions.java.get()
private val _javaVersion = JavaVersion.toVersion(_java)
private val _jvmTarget = JvmTarget.fromTarget(_java)

group = libs.versions.application.namespace.get()
version = libs.versions.application.version.name.get()

application {
    mainClass.set(libs.versions.application.namespace.get() + ".mcp.todo.MainTodoMcpServerKt")
}
java {
    sourceCompatibility = _javaVersion
    targetCompatibility = _javaVersion
}
kotlin {
    compilerOptions {
        jvmTarget = _jvmTarget
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.serializationJson)
    implementation(libs.slf4j.simple)
    implementation(libs.mcp.sdk)
}