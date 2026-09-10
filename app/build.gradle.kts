import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 从本地 secrets.properties 读取内置模型 key（该文件不提交 git，见 .gitignore）
val secretsProps = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secretKey(name: String): String = secretsProps.getProperty(name) ?: ""

android {
    namespace = "com.freechat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.freechat"
        minSdk = 26
        targetSdk = 34
        versionCode = 118
        versionName = "1.0.18"

        buildConfigField("String", "XIAOMI_API_KEY", "\"${secretKey("XIAOMI_API_KEY")}\"")
        buildConfigField("String", "XIAOMI_ULTRASPEED_API_KEY", "\"${secretKey("XIAOMI_ULTRASPEED_API_KEY")}\"")
        buildConfigField("String", "DOUBAO_API_KEY", "\"${secretKey("DOUBAO_API_KEY")}\"")
        buildConfigField("String", "MIMO_TTS_KEY", "\"${secretKey("MIMO_TTS_KEY")}\"")
        buildConfigField("String", "SERPAPI_API_KEY", "\"${secretKey("SERPAPI_API_KEY")}\"")
    }

    buildTypes {
        release {
            // 关闭 R8 混淆：AppStrings 是 300+ 字段 data class，R8 编译其巨型构造会生成
            // 非法字节码（VerifyError 真机闪退），纯 D8 正常。代价是 APK ~30MB（原 R8 12MB）。
            // 想恢复瘦身需：升级 AGP/R8 版本，或重构 AppStrings 拆分巨型构造。
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(composeBom)

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    // Activity & Lifecycle
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Gson
    implementation("com.google.code.gson:gson:2.11.0")

    // Coil — 图片加载
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Haze — 毛玻璃背景模糊（液态玻璃）
    implementation("dev.chrisbanes.haze:haze:1.6.10")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
