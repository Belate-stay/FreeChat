package com.freechat.data

import com.freechat.core.MemoryExtractor
import com.freechat.core.MemoryLogic
import com.freechat.i18n.buildStrings
import com.freechat.model.*
import org.junit.Assert.*
import org.junit.Test

class Beta96RegressionTest {
    private fun builtIn(type: ModelType = ModelType.LANGUAGE) = ModelInfo(
        id = "fixed-model", displayName = "Built-in", provider = Provider.XIAOMI,
        description = "Fixed definition", modelType = type, isBuiltIn = true,
        supportsDeepThinking = type == ModelType.LANGUAGE,
        supportsNativeSearch = type == ModelType.LANGUAGE
    )

    @Test fun everyBuiltInTypeIsNotEditable() {
        ModelType.entries.forEach { type ->
            assertFalse(type.name, ModelAccessPolicy.canEdit(builtIn(type)))
        }
    }

    @Test fun addingAndEditingCustomModelsRemainAvailable() {
        assertTrue(ModelAccessPolicy.canEdit(null))
        ModelType.entries.forEach { type ->
            assertTrue(ModelAccessPolicy.canEdit(builtIn(type).copy(isBuiltIn = false, provider = Provider.CUSTOM)))
        }
    }

    @Test fun legacyOverridesCannotChangeAnyBuiltInDefinitionField() {
        ModelType.entries.forEach { type ->
            val base = builtIn(type)
            val legacy = base.copy(displayName = "Changed", description = "Changed", provider = Provider.CUSTOM,
                apiBaseUrl = "https://example.invalid", apiKey = "fixture-key", isBuiltIn = false,
                supportsThinking = false, supportsWebSearch = false, supportsDeepThinking = false,
                supportsNativeSearch = false, genAspectRatio = "16:9", genResolution = "9999", genStyle = "anime")
            assertEquals(base, ModelAccessPolicy.withUsagePreference(base, legacy))
        }
    }

    @Test fun globalReasoningUsageSwitchStillWorksWithoutEditingDefinition() {
        val base = builtIn()
        val on = ModelAccessPolicy.withUsagePreference(base, base.copy(deepThinkingDefault = true,
            supportsDeepThinking = false, supportsNativeSearch = false))
        assertEquals(base.copy(deepThinkingDefault = true), on)
        assertEquals(base, ModelAccessPolicy.withUsagePreference(on, base.copy(deepThinkingDefault = false)))
        assertFalse(ModelAccessPolicy.canEdit(on))
    }

    @Test fun unsupportedModelCannotAcquireReasoningFromLegacyPreference() {
        val image = builtIn(ModelType.VISUAL)
        assertEquals(image, ModelAccessPolicy.withUsagePreference(image, image.copy(
            supportsDeepThinking = true, deepThinkingDefault = true)))
    }

    @Test fun wrongModelPreferenceAndMissingPreferenceAreIgnored() {
        val base = builtIn()
        assertEquals(base, ModelAccessPolicy.withUsagePreference(base, null))
        assertEquals(base, ModelAccessPolicy.withUsagePreference(base,
            base.copy(id = "another", deepThinkingDefault = true)))
        assertEquals(base, ModelAccessPolicy.withUsagePreference(base,
            base.copy(modelType = ModelType.VISUAL, deepThinkingDefault = true)))
    }

    @Test fun customModelIsNotRewrittenByBuiltInPreferencePolicy() {
        val custom = builtIn().copy(isBuiltIn = false, provider = Provider.CUSTOM, apiBaseUrl = "https://example.invalid")
        assertEquals(custom, ModelAccessPolicy.withUsagePreference(custom, builtIn().copy(deepThinkingDefault = true)))
    }

    private fun entry(summary: String, timestamp: Long = 1L, kind: String = "detail") =
        MemoryEntry(id = "memory-$timestamp", summary = summary, keywords = emptyList(),
            timestamp = timestamp, kind = kind, eventDate = "2026年10月1日")

    @Test fun memoryWithoutLiteralKeywordsIsStillAvailableToModel() {
        val fact = "三年前在渡口第一次相识，雨很大，借给她一把伞。"
        val context = ModelManagedMemory.buildContext(listOf(entry(fact)))
        assertTrue(context.contains(fact))
        assertTrue(context.contains("由模型判断关联"))
        assertTrue(context.contains("同义表达与指代"))
        assertTrue(context.contains("不要强行提起"))
        assertTrue(context.contains("不要补写"))
    }

    @Test fun normalMemoryIsBoundedAndKeepsChronologicalDates() {
        // Disjoint characters keep these genuinely distinct rather than near-duplicate facts.
        val entries = (1L..80L).map { n -> entry((0x4e00 + n.toInt()).toChar().toString().repeat(6), n) }
        val context = ModelManagedMemory.buildContext(entries.reversed())
        val factLines = context.lines().filter { it.startsWith("- [") }
        assertEquals(MemoryLogic.NORMAL_TOTAL_CAP, factLines.size)
        assertTrue(factLines.first().contains(entries[50].summary))
        assertTrue(factLines.last().contains(entries.last().summary))
        assertTrue(factLines.all { it.contains("2026年10月1日") })
    }

    @Test fun highQualityMemoryKeepsOriginalTextAndTimelineRules() {
        val original = "她说：“请记得，明年春天我们还在这里见面。”"
        val context = ModelManagedMemory.buildContext(listOf(entry(original, kind = "plot")), highQuality = true)
        assertTrue(context.contains(original))
        assertTrue(context.contains("完整档案"))
        assertTrue(context.contains("按时间顺序"))
        assertTrue(context.contains("以最新记录为准"))
    }

    @Test fun emptyAndLegacyNullMemoryAreHandledWithoutFabrication() {
        assertEquals("", ModelManagedMemory.buildContext(emptyList()))
        assertEquals("", ModelManagedMemory.buildContext(listOf(entry(" "))))
        val legacy = AppJson.gson.fromJson("{\"summary\":\"他喜欢乌龙茶\",\"keywords\":null,\"eventDate\":null}", MemoryEntry::class.java)
        assertTrue(ModelManagedMemory.buildContext(listOf(legacy)).contains("他喜欢乌龙茶"))
    }

    @Test fun extractionNoLongerConsumesLegacyAssociationLevelInAndroidMode() {
        listOf(false, true).forEach { highQuality ->
            val low = MemoryExtractor.buildMessages("我喜欢猫", "我记得", highQuality,
                assocLevel = 1f, nowMillis = 1_700_000_000_000L, modelManagedAssociation = true)
            val high = MemoryExtractor.buildMessages("我喜欢猫", "我记得", highQuality,
                assocLevel = 10f, nowMillis = 1_700_000_000_000L, modelManagedAssociation = true)
            assertEquals(low, high)
            val system = low.first().getValue("content")
            assertTrue(system.contains("自主选择"))
            assertTrue(system.contains("不要猜测、过度联想"))
            assertFalse(system.contains("必含同义词"))
            assertFalse(system.contains("2-3 个原词"))
            assertFalse(system.contains("例如「猫、喵星人、宠物」"))
        }
    }

    @Test fun extractionStillProtectsNarrativeInformationBoundary() {
        val prompt = MemoryExtractor.buildMessages("他在心里想起旧友", "她没有听见", true,
            plotMode = true, modelManagedAssociation = true).first().getValue("content")
        assertTrue(prompt.contains("信息边界"))
        assertTrue(prompt.contains("不在场、听不到"))
        assertTrue(prompt.contains("绝不改写"))
    }

    @Test fun capabilityCopyDistinguishesDeclarationFromGlobalUsageInAllLocales() {
        listOf("zh-CN", "zh-TW", "en").forEach { locale ->
            val strings = buildStrings(locale)
            assertTrue(strings.modelCapabilitiesLabel.isNotBlank())
            assertTrue(strings.modelCapabilitiesDesc.contains("API"))
            assertTrue(strings.nativeSearchLabel.isNotBlank())
            assertTrue(strings.modelDeepThinkingLabel.isNotBlank())
        }
        val zh = buildStrings("zh-CN")
        assertTrue(zh.modelCapabilitiesDesc.contains("不会直接开启"))
        assertTrue(zh.modelCapabilitiesDesc.contains("全局设置"))
        assertTrue(zh.nativeSearchLabel.contains("支持"))
        assertTrue(zh.nativeSearchDesc.contains("实际能力"))
    }
}
