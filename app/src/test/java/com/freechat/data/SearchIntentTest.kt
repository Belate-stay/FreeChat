package com.freechat.data

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class SearchIntentTest {
    @Test fun aNegativeDecisionNeverInitiatesSearchEvenWithQueries() {
        assertNull(SearchIntent.fromDecision("""{"search":false,"queries":["河南 软件 招聘"]}"""))
        assertNull(SearchIntent.fromDecision("""{"queries":["河南 软件 招聘"]}"""))
        assertNull(SearchIntent.fromDecision("""{"search":"true","queries":["河南 软件 招聘"]}"""))
        assertNull(SearchIntent.fromDecision("not json"))
    }
    @Test fun positiveDecisionKeepsLocalityIntentAndDoesNotInventRecency() {
        val intent = SearchIntent.fromDecision("""```json
            {"search":true,"queries":["河南 软件开发 校招 本科","郑州 软件公司 招聘"],"required_groups":[["河南","郑州","洛阳"],["软件","开发","测试"]],"recent_days":0,"news":false}
            ```""")!!
        assertEquals(2, intent.queries.size)
        assertEquals(listOf("河南", "郑州", "洛阳"), intent.requiredGroups[0])
        assertNull(intent.recentDays)
        assertFalse(intent.news)
    }
    @Test fun planningIsBoundedAndMalformedParametersAreRejected() {
        assertNull(SearchIntent.fromArguments(JsonParser.parseString("""{"queries":[]}""").asJsonObject))
        val intent = SearchIntent.fromArguments(JsonParser.parseString("""{"queries":["a","b","c","d"],"recent_days":999}""").asJsonObject)!!
        assertEquals(listOf("a", "b", "c"), intent.queries)
        assertNull(intent.recentDays)
    }
    @Test fun employmentQueriesDoNotRouteThroughNewsFeeds() {
        assertFalse(SearchPipeline.plan("河南公办二本软件工程专业 在河南有什么合适的岗位或者企业推荐").news)
        assertTrue(SearchPipeline.plan("DeepSeek 最近新闻").news)
    }
    @Test fun screenshotNoiseIsRejectedAndHenanSoftwareEmploymentIsRetained() {
        val question = "河南公办二本软件工程专业,在河南内部有什么合适的岗位或者企业推荐？"
        val noise = listOf(
            "央视曝光二手平台低价机票陷阱，一男子被骗近30万元",
            "桌面 AI 超算新选择：英伟达 NVIDIA DGX Spark 内存版发布",
            "Meta 预热雷朋 Display 眼镜更新：改进续航、通知推送",
            "新加坡推出面向公务员的约会软件 FirstDate",
            "Meta 旗下 AI 智能体 Muse 可代替用户完成任务",
            "CNNIC 称中国生成式 AI 用户超7亿",
            "HKC 神盾 25G0B3 显示器发布",
            "我国深地油气开发实现关键跨越",
            "河南机票软件推荐：抢票平台上线"
        ).mapIndexed { index, title -> SearchEntry(title, "最新产品资讯", "https://noise.test/$index", publishedAt = System.currentTimeMillis()) }
        val local = SearchEntry("郑州软件企业校园招聘", "本科毕业生 Java 后端开发、软件测试岗位，官网介绍及岗位要求", "https://local.test/jobs")
        val other = SearchEntry("洛阳软件开发公司人才招聘", "软件工程本科生校招职位", "https://local.test/luoyang")
        val ranked = SearchPipeline.rank(noise + local + other, question, null)
        assertEquals(setOf(local, other), ranked.toSet())
        assertTrue(SearchPipeline.rank(noise, question, 7).isEmpty())
    }
    @Test fun modelSynonymGroupsAllHaveToMatch() {
        val entries = listOf(
            SearchEntry("郑州 Java 后端校招", "本科生软件研发岗位", "https://local.test/java"),
            SearchEntry("上海 Java 后端校招", "本科生软件研发岗位", "https://other.test/java"),
            SearchEntry("河南开发新能源项目", "企业招聘工程师", "https://other.test/oil")
        )
        val groups = listOf(listOf("河南", "郑州"), listOf("软件", "java", "后端"), listOf("招聘", "校招", "岗位"))
        assertEquals(listOf(entries.first()), SearchPipeline.rank(entries, "河南 软件 招聘 郑州 Java 后端 校招", null, requiredGroups = groups))
    }
    @Test fun englishTokensCannotMatchInsideUnrelatedWords() {
        val entries = listOf(SearchEntry("Paid vacation", "Sailing trip", "https://noise.test/"))
        assertTrue(SearchPipeline.rank(entries, "AI", null).isEmpty())
    }
    @Test fun publicHtmlResultsDecodeOriginalUrlsAndNeverIncludeCaptchaOrNavigation() {
        val html = """<a href="/settings">河南软件</a><a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.org%2Fjobs%3Fa%3D1%26b%3D2&amp;rut=abc">河南 <b>软件</b> 招聘</a><a class="result__snippet" href="#">本科生 <b>开发</b> 岗位</a><a class="result__a" href="https://example.org/other">郑州软件公司</a>"""
        val results = SearchSource.parseDuckDuckGo(html)
        assertEquals(2, results.size)
        assertEquals("河南 软件 招聘", results.first().title)
        assertEquals("本科生 开发 岗位", results.first().snippet)
        assertEquals("https://example.org/jobs?a=1&b=2", results.first().link)
        assertTrue(SearchSource.parseDuckDuckGo("<html><title>Captcha</title><a href='/'>Home</a></html>").isEmpty())
    }
    @Test fun bingNaturalResultsDecodeOriginalUrlsExcludeAdsAndKeepSnippetDate() {
        val original = "https://example.edu.cn/jobs?a=1&b=2"
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(original.toByteArray())
        val html = """<li class="b_ad"><h2><a href="https://ad.test/">广告</a></h2></li>
            <li class="b_algo" data-id="1"><div>site</div><h2 class=""><a target="_blank" href="https://www.bing.com/ck/a?!&amp;u=a1$encoded&amp;ntb=1">河南 <strong>软件</strong> 校招</a></h2><div class="b_caption"><p class="b_lineclamp2"><span class="news_dt">Sep 22, 2026</span>&nbsp;本科开发岗位</p></div></li>
            <li class='b_algo'><h2><a href='https://jobs.test/local'>郑州软件企业</a></h2><p>软件开发招聘</p></li>"""
        val results = SearchSource.parseBingWeb(html)
        assertEquals(2, results.size)
        assertEquals(original, results.first().link)
        assertEquals("河南 软件 校招", results.first().title)
        assertTrue(results.first().snippet.contains("本科开发岗位"))
        assertEquals("Sep 22, 2026", results.first().date)
        assertNotNull(results.first().publishedAt)
    }
    @Test fun bingMalformedOrNonWebTargetsAndNavigationAreIgnored() {
        val html = """<li class="b_algo"><h2><a href="https://www.bing.com/ck/a?u=a1BAD">无效跳转</a></h2></li>
            <li class="b_algo"><h2><a href="javascript:alert(1)">无效协议</a></h2></li>
            <li class="b_algo"><h2><a href="https://secret@jobs.test/">含认证信息</a></h2></li>
            <li><h2><a href="https://bing.com/settings">导航</a></h2></li>"""
        assertTrue(SearchSource.parseBingWeb(html).isEmpty())
    }
}
