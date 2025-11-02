plugins {
    java
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.lombok) apply false
}

group = "it.ohalee.redis-bridge"
version = "1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    dependencies {
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
