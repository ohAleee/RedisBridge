plugins {
    `java-library`
    alias(libs.plugins.lombok)
}

dependencies {
    api(libs.lettuce)
    api(libs.gson)
    api(libs.caffeine)
    api(libs.apache.commons.pool2)
    compileOnly(libs.jetbrains.annotations)
}