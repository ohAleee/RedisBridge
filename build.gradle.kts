plugins {
    java
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.lombok) apply false
    id("com.vanniktech.maven.publish") version "0.34.0" apply false
}

group = "com.ohalee.redis-bridge"
version = "1.0.4"

allprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "com.vanniktech.maven.publish")

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
        publishToMavenCentral()

        signAllPublications()

        coordinates(rootProject.group.toString(), project.name, rootProject.version.toString())

        pom {
            name.set(project.name)
            description.set("A class based messaging bridge with Redis pub/sub.")
            url.set("https://github.com/ohAleee/RedisBridge")

            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }

            developers {
                developer {
                    id.set("ohalee")
                    name.set("ohAlee")
                    email.set("business@ohalee.com")
                }
            }

            scm {
                connection.set("scm:git:git://github.com/ohAleee/RedisBridge.git")
                developerConnection.set("scm:git:ssh://github.com:ohAleee/RedisBridge.git")
                url.set("https://github.com/ohAleee/RedisBridge")
            }

            issueManagement {
                system.set("GitHub")
                url.set("https://github.com/ohAleee/RedisBridge/issues")
            }
        }
    }
}