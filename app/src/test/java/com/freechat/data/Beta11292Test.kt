package com.freechat.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Beta 1.1.29.2 契约：②按住说话识别修复——内置 MiMo ASR 端点形态与静默失败治理。
 * 实测（2026-10-13）：api.xiaomimimo.com 的 /v1/audio/transcriptions 是 404，
 * 识别只认 /v1/chat/completions + input_audio，且 model 只认小写 API id。
 */
class Beta11292Test {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun builtInAsrTalksChatCompletionsWithInputAudio() {
        val vm = source("viewmodel/ChatViewModel.kt")
        assertTrue("内置识别走 chat/completions + input_audio，不再调 404 的 transcriptions",
            vm.contains("transcribeAudioMiMo") &&
                vm.contains("\"input_audio\"") &&
                vm.contains("BuiltInVoiceModel.ASR_API_ID"))
        assertTrue("自定义模型仍走 OpenAI 标准 multipart（/v1/audio/transcriptions）",
            vm.contains("transcribeAudioOpenAi") && vm.contains("audio/transcriptions"))
        assertTrue("按内置与否分流", vm.contains("if (asr.isBuiltIn) transcribeAudioMiMo(asr, wav) else transcribeAudioOpenAi(asr, wav)"))
    }

    @Test fun recognitionFailureIsNoLongerSilent() {
        val screen = source("ui/screens/ChatScreen.kt")
        assertTrue("有模型但没识别出内容 → 失败提示，不再静默装死",
            screen.contains("s.voiceRecognizeFailed"))
        assertTrue("无模型提示保留", screen.contains("showNoAsrDialog = true"))
    }

    @Test fun knowledgeScopeIsIntroConvPlusExplicitOnly() {
        val vm = source("viewmodel/ChatViewModel.kt")
        assertTrue("拟人档不再注入知识库（方案B）",
            vm.contains("return base") && !vm.contains("isAppKnowledgeQuery"))
        assertTrue("「FreeChat简介说明」对话常驻注入",
            vm.contains("convId == com.freechat.data.BuiltInIntro.CONV_ID) return true"))
        assertTrue("删过简介对话=知识库整体停用",
            vm.contains("introDeletedLatch") && vm.contains("if (introDeletedLatch) return false"))
        assertTrue("旧「疑问句+功能词」组合触发退役（「微信」误伤源清掉）",
            !vm.contains("question.any") && !vm.contains("\"微信\", \"场景图\""))
        assertTrue("标准档只认身份类提问+显式点名",
            vm.contains("isAppIdentityQuery(lastUserMsg) || isExplicitFreeChatMention(lastUserMsg)"))
    }

    @Test fun personaRewriteKeepsPositiveTraitsFully() {
        val vm = source("viewmodel/ChatViewModel.kt")
        assertTrue("改写管道加了肯定式特质对称条款（不得淡化/打折扣/稀释）",
            vm.contains("肯定式特质") && vm.contains("不得淡化、不得打折扣"))
    }
}
