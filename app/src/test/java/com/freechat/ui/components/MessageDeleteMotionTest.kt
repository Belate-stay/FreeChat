package com.freechat.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MessageDeleteMotionTest {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun deleteExitIsOrchestratedBeforeRemovalAndCoversAllPaths() {
        val chat = source("ui/screens/ChatScreen.kt")
        assertTrue("Exiting rows are tracked before the real removal",
            chat.contains("exitingIds"))
        assertTrue("One shared playExitThen orchestrates exit-then-delete",
            chat.contains("playExitThen"))
        assertTrue("Multi-select delete goes through the exit choreography",
            chat.contains("playExitThen(removed)"))
        // 1.1.29.8 工单⑤翻新：重生成家族只走粒子粉碎（VM shatterThenDelete），不再叠播方向滑出；
        // 用户删除/多选删除仍是 exit-then-delete 编排
        assertTrue("Regeneration shatters via the VM (no slide-out orchestration)",
            chat.contains("viewModel.regenerate(aiIndex)") &&
                source("viewmodel/ChatViewModel.kt").contains("shatterThenDelete"))
        assertTrue("Scene image regenerate shatters too; scene DELETE keeps the choreography",
            chat.contains("viewModel.regenerateSceneImage(sceneId)") && chat.contains("viewModel.deleteSceneImage(sceneId)"))
        assertTrue("Reduced motion deletes immediately",
            chat.contains("if (targets.isEmpty() || !motionOn)"))
    }

    @Test fun exitDirectionIsRoleBasedAndAppliedPerRow() {
        val chat = source("ui/screens/ChatScreen.kt")
        val policy = source("ui/animation/MotionPolicy.kt")
        assertTrue("Direction comes from the pure role mapping (USER right / ASSISTANT left)",
            chat.contains("MotionPolicy.exitDirection(msgItem.msg.role == Role.USER)"))
        assertTrue("User messages exit toward +X (screen right)",
            policy.contains("fun exitDirection(isUser: Boolean): Float = if (isUser) 1f else -1f"))
    }

    @Test fun exitUsesTweenSpecAndAgslTrailWithFallback() {
        val chat = source("ui/screens/ChatScreen.kt")
        val anim = source("ui/animation/FreeChatAnimation.kt")
        val shader = source("ui/components/MessageExitShader.kt")
        assertTrue("Exit travel uses the shared dismiss tween spec",
            chat.contains("FreeChatAnimation.messageExitTween") && anim.contains("val messageExitTween"))
        assertTrue("AGSL directional trail is attached only mid-flight",
            shader.contains("RenderEffect.createRuntimeShaderEffect") &&
                shader.contains("p > 0.001f && p < 0.999f"))
        assertTrue("API<33 or shader failure keeps translation+fade",
            shader.contains("Build.VERSION.SDK_INT >= 33") &&
                shader.contains("translationX = p * size.width * direction"))
    }

    @Test fun regenerateMarksReplaySoEntranceAndTypewriterReturn() {
        val vm = source("viewmodel/ChatViewModel.kt")
        val entrance = source("ui/animation/MotionPolicy.kt")
        val typewriter = source("ui/components/TypewriterReveal.kt")
        assertTrue("Standard regenerate marks the fresh reply id",
            vm.contains("AnimationReplay.mark(replyId)"))
        assertTrue("Companion regenerate marks every new segment id",
            vm.contains("if (regenerating) AnimationReplay.mark(segId)"))
        assertTrue("Entrance claim honors the replay bypass after seen.add",
            entrance.contains("if (AnimationReplay.consume(id)) return true"))
        assertTrue("Typewriter claim honors the same bypass",
            typewriter.contains("AnimationReplay.consume(id)"))
    }

    @Test fun exitShaderSourceStaysRestrained() {
        val agsl = MessageExitShaderSource.Agsl
        assertTrue("Multi-tap directional blur, not particles",
            agsl.contains("for (int i = 0; i < 5; i++)") && !agsl.contains("fract(sin"))
        assertTrue("No fwidth — AGSL has no derivatives here",
            !agsl.contains("fwidth"))
    }
}
