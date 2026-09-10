# FreeChat ProGuard Rules
# 修复真机启动 VerifyError 闪退：AppStrings 是 300+ 字段的 data class，三语实例
# 各一个巨型构造调用，R8 的 shrink/optimize 重写这个构造后字节码损坏（D8 纯 dex 正常）。
# 处理：keep 整个 i18n 包不让 R8 动它 + 关闭优化。
-dontoptimize
-keep class com.freechat.i18n.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.freechat.model.** { *; }
-keep class com.freechat.data.** { *; }
