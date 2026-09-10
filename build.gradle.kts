plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

ext["compileSdk"] = 36
ext["minSdk"] = 34
ext["targetSdk"] = 36
ext["jvmTarget"] = "17"
