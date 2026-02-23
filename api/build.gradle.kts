plugins {
    `java-library`
    alias(libs.plugins.lombok)
}

dependencies {
    api(libs.lettuce)
    api(libs.gson)
    api(libs.apache.commons.pool2)
    compileOnlyApi(libs.jetbrains.annotations)
}