plugins {
    kotlin("jvm")
}

kotlin { jvmToolchain(11) }

dependencies { testImplementation(kotlin("test")) }
