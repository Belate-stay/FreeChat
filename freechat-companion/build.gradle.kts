/**
 * freechat-companion —— 服务器大脑（M2）：读同步库跑 freechat-core 的同一份拟人机制，
 * 用内置 mimo-v2.6-flash 代调生成回复并写回（文本+记忆摘录），供 M3 微信通道 / M4 收口调用。
 *
 * 纪律（FreeChat_1.0.91_设计方案.md）：
 *  · 机制逻辑**不许在这里重写**——一律走 freechat-core（CompanionPrompts/RequestBuilder/ReplyParser/
 *    MemoryLogic/MemoryExtractor）。同码保证「完全还原」。
 *  · 读写同步库必须复刻 sync.js 的写语义（rev+1 / nextSeq / touchWrite / size_raw 配额），
 *    否则客户端同步会坏。烟测 test/ 覆盖这条。
 *  · 只监听 127.0.0.1；对外暴露是 nginx/后续 M3 的事。
 */
plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.freechat.companion.MainKt")
}

dependencies {
    implementation(project(":freechat-core"))
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    // 服务器 SQLite（node:sqlite 写、本服务读写；WAL 多进程安全）
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
