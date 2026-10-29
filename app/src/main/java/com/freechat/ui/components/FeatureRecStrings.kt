package com.freechat.ui.components

import com.freechat.i18n.AppStrings

private fun AppStrings.featureRecText(zh: String, tw: String, en: String) = when (localeCode) {
    "en" -> en
    "zh-TW" -> tw
    else -> zh
}

internal val AppStrings.featureRecGroupBody get() = featureRecText(
    "从角色池邀请多位角色，\n在一个微信群里各自回应、自然接话。",
    "從角色池邀請多位角色，\n在一個微信群裡各自回應、自然接話。",
    "Bring characters from your library into one WeChat-style group. Each member responds in their own voice.",
)

internal val AppStrings.featureRecStoryBody get() = featureRecText(
    "让多位角色走进同一个世界，\n以连贯的第三人称文本共同演绎故事。",
    "讓多位角色走進同一個世界，\n以連貫的第三人稱文本共同演繹故事。",
    "Bring an ensemble cast into one world, with continuous third-person narration following the unfolding story.",
)

internal val AppStrings.featureRecNext get() = featureRecText("下一页", "下一頁", "Next")
