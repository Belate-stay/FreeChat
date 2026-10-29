package com.freechat.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2026-10-19 工单：五个模型池页底部统一挂「如何获取API？」（蓝+斜体+下划线），
 * 点进「获取API教程」页——DeepSeek 从注册到拿到 API Key，再到 FreeChat 添加模型填必要信息为止。
 */
class ApiTutorialContractTest {

    @Test fun allModelPoolsLinkToTheApiTutorial() {
        val settings = File("src/main/java/com/freechat/ui/screens/SettingsScreen.kt").readText()
        val strings = File("src/main/java/com/freechat/i18n/AppStrings.kt").readText()
        // 2026-10-19 返修：链接挪到**屏幕底部悬浮**（模型多了列表成流也压不住），五池共用
        assertTrue("悬浮底部入口", settings.contains("Alignment.BottomCenter") && settings.contains("openSubScreen(SubScreen.API_TUTORIAL)"))
        assertTrue("列表内不再挂链接", !settings.contains("onApiTutorial ="))
        assertTrue("链接=linkInk 蓝 + 斜体 + 下划线",
            settings.contains("colors.linkInk") && settings.contains("FontStyle.Italic") && settings.contains("TextDecoration.Underline"))
        // 2026-10-19 返修二（工单点名）：不要卡片，直接镂空悬浮——入口不许再包任何底卡
        val linkAt = settings.indexOf("s.apiTutorialLink")
        assertTrue("入口文案存在", linkAt > 0)
        assertFalse("入口镂空悬浮不带底卡（apiLinkShape 已拆）", settings.contains("apiLinkShape"))
        assertFalse("入口不挂磨砂/玻璃卡", settings.substring(linkAt - 400, linkAt).contains("floatingSurface") ||
            settings.substring(linkAt - 400, linkAt).contains("frostedCard"))
        assertTrue("教程入口文案三语", strings.contains("如何获取API？") && strings.contains("如何獲取API？") && strings.contains("How to get an API?"))
        assertTrue("教程页标题进标题栏映射", settings.contains("SubScreen.API_TUTORIAL -> s.apiTutorialTitle"))
    }

    @Test fun tutorialCoversDeepseekUntilTheAddModelForm() {
        val page = File("src/main/java/com/freechat/ui/screens/ApiTutorialPage.kt").readText()
        assertTrue("DeepSeek 全流程：平台/密钥页/只显示一次/充值",
            page.contains("platform.deepseek.com") && page.contains("API keys") && page.contains("只完整显示一次") && page.contains("充值"))
        assertTrue("支付页提醒 1 元试水", page.contains("1 元起充") && page.contains("先充 1 元试水"))
        assertTrue("拿到 Key（sk- 开头）", page.contains("sk- 开头"))
        assertTrue("三项必要信息与添加模型四字段",
            page.contains("URL 地址") && page.contains("API Key") && page.contains("模型 ID") &&
                page.contains("deepseek-flash") && page.contains("deepseek-v4-pro") && page.contains("api.deepseek.com"))
        assertTrue("其他模型通用找法", page.contains("API开放平台") && page.contains("注册 → 充值 → 申请 API Key"))
        assertTrue("教程正文固定中文先例说明", page.contains("固定中文不进语言包"))
        assertTrue("二级页不自带滚动（戒律9）", !page.contains("verticalScroll"))
    }

    @Test fun tutorialScreenshotsAndClickableLinks() {
        val page = File("src/main/java/com/freechat/ui/screens/ApiTutorialPage.kt").readText()
        // 六张截图按讲解顺序各就各位：支付→余额→菜单(蓝圈)→创建密钥→用量→价格表
        val order = listOf("api_tut_pay", "api_tut_balance", "api_tut_menu", "api_tut_create_key", "api_tut_usage", "api_tut_pricing")
        var last = -1
        for (name in order) {
            val at = page.indexOf(name)
            assertTrue("$name 必须在场且顺序不乱", at > last)
            last = at
        }
        assertTrue("截图居中限宽（不喧宾夺主）", page.contains("Arrangement.Center") && page.contains("widthIn(max = 288.dp)"))
        assertTrue("链接可点+蓝+斜体+下划线", page.contains("LinkAnnotation.Url") &&
            page.contains("colors.linkInk") && page.contains("FontStyle.Italic") && page.contains("TextDecoration.Underline"))
        // 2026-10-28 统一顶距：首段起点由容器统一落在模糊带下缘，10-19 的 28dp 二次下移补丁已退役
        assertFalse("旧首段下沉补丁不得复活（统一顶距后归零）", page.contains("Spacer(Modifier.height(28.dp))"))
        assertTrue("标题加大加粗", page.contains("FontWeight.Bold") && page.contains("titleMedium"))
        assertTrue("费用透明句在用量节尾", page.contains("所有用费均为您提供AI服务") &&
            page.indexOf("所有用费均为您提供AI服务") > page.indexOf("api_tut_usage"))
    }
}
