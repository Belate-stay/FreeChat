plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.10" apply false
    // freechat-core：Android 与服务器 JVM 共用的纯逻辑模块（拟人机制同码保证「完全还原」）
    id("org.jetbrains.kotlin.jvm") version "2.0.10" apply false
}
