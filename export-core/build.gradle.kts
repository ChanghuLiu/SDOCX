plugins {
    kotlin("jvm")
}

kotlin { jvmToolchain(11) }

dependencies { implementation(project(":sdocx-core")); testImplementation(kotlin("test")) }
