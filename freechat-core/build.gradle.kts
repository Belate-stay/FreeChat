/**
 * freechat-core —— Android 与服务器 JVM 共用的纯逻辑模块。
 *
 * 纪律（1.0.91 M1 地基约定，动这个模块前先读 FreeChat_1.0.91_设计方案.md）：
 *  · 这里**只放纯 Kotlin**：不许 import android.* / androidx.*，不许碰 Context/文件/UI 状态。
 *    「完全还原」靠两端跑同一份代码保证，不靠翻译——机制改动只动这一处，两端同进。
 *  · 存储/网络/时钟经接口或参数注入（TimeState.nowMillis 这类），服务器侧才能复现与回归。
 *  · 需要 Android 才能做的事（通知、AlarmManager、Bitmap、ContentResolver）留在 app 模块。
 */
plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // 与 app 模块同版本（CharacterOriginalLearning 的 JSON 数据块防注入编码用）
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}
