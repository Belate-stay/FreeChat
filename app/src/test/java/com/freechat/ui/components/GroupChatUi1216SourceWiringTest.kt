package com.freechat.ui.components

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source contracts complement, rather than replace, device gesture and layout checks. */
class GroupChatUi1216SourceWiringTest {
    private fun source(path: String): String = File("src/main/java/com/freechat/$path")
        .takeIf { it.exists() }?.readText().orEmpty()
    private fun chat() = source("ui/screens/ChatScreen.kt")
    private fun settings() = source("ui/screens/GroupSettingsScreen.kt")

    @Test fun groupHeaderUsesDedicatedSettingsAndGroupRemark() {
        val screen = chat()
        assertTrue(screen.contains("onOpenGroupSettings: () -> Unit"))
        assertTrue(screen.contains("if (groupChat != null) onOpenGroupSettings()"))
        assertTrue(screen.contains("groupChat != null -> currentConversationTitle"))
        assertTrue("Groups must not expose external WeChat binding",
            screen.contains("groupChat == null && companionMode && dialogueMode"))
    }

    @Test fun messageIdentityRetainsAvatarsAndStableGestureTargets() {
        val bubble = source("ui/components/ChatBubble.kt")
        assertTrue(bubble.contains("GroupMessageIdentity.resolve(message, groupChat)"))
        assertTrue(bubble.contains("if (groupChat.showMemberNames)"))
        assertTrue(bubble.contains("onLongClick = { onMentionGroupMember?.invoke(memberId) }"))
        assertTrue(bubble.contains("onDoubleClick = nudgeMember"))
        assertFalse("A single avatar tap must not nudge", bubble.contains("onClick = { onNudgeGroupMember"))
        val avatar = source("ui/components/GroupAvatar.kt")
        assertTrue(avatar.contains("it.id == message.groupMemberId"))
        assertTrue(avatar.contains("message.groupSenderName"))
        assertTrue(avatar.contains("message.groupSenderAvatar"))
    }

    @Test fun publicGroupEventsRenderAsSystemRows() {
        val bubble = source("ui/components/ChatBubble.kt")
        assertTrue(bubble.contains("message.role == Role.SYSTEM && message.groupEventType != null"))
        assertTrue(bubble.contains("GroupSystemRow("))
        assertTrue(chat().contains("viewModel.mentionGroupMember(id)"))
        assertTrue(chat().contains("mentionInsertion = MentionComposerInsertion"))
        assertTrue(chat().contains("onNudgeGroupMember = viewModel::nudgeGroupMember"))
    }

    @Test fun compositeAvatarIsSharedAcrossGroupSurfaces() {
        assertTrue(source("ui/components/GroupAvatar.kt").contains("group.avatarPath.isNotBlank()"))
        assertFalse("Group Chat header should not repeat the group avatar", chat().contains("GroupAvatar(group = groupChat"))
        assertFalse("The group stop button belongs outside the header", chat().contains("Icons.Filled.StopCircle"))
        assertTrue(source("ui/components/DrawerContent.kt").contains("GroupAvatar(group = group"))
        assertTrue(settings().contains("GroupAvatar(group = group"))
    }

    @Test fun groupSettingsKeepDisplayControlsAvailableAndAutonomousPreviewDisabled() {
        val screen = settings()
        assertFalse(screen.contains("s.pinConversation"))
        assertFalse(screen.contains("onTogglePin"))
        val visibility = screen.substringAfter("SettingsItem(Icons.Filled.Badge").substringBefore("\n                    }")
        assertFalse(visibility.contains("!isBusy"))
        assertTrue(screen.contains("s.groupSettingLockedDuringReply"))
        assertTrue(screen.contains("s.groupProactivePreview"))
        assertTrue(screen.contains("s.groupProactivePreviewDesc"))
        assertTrue(screen.contains("s.groupInternalTest"))
        assertFalse(screen.contains("proactiveEnabled"))
    }

    @Test fun groupEditorsUseExplicitTargetAndConfirmedSaveCallbacks() {
        val screen = settings()
        assertTrue(screen.contains("conversationId: String"))
        assertTrue(screen.contains("onSaveGroup: (String, GroupChatProfile) -> Unit"))
        assertTrue(screen.contains("onRename: (String, String) -> Unit"))
        assertTrue(screen.contains("onEditMember: (String) -> Unit"))
        assertTrue(screen.contains("onChooseMembers: () -> Unit"))
        assertTrue(screen.contains("onSearch: () -> Unit"))
        assertTrue(screen.contains("AvatarCropDialog("))
        assertTrue(screen.contains("if (!isBusy) onSaveGroup(conversationId"))
        assertTrue(screen.contains("onRename(conversationId"))
    }

    @Test fun groupModelsAndReplyOptionsStayAtGroupLevel() {
        val screen = settings()
        assertTrue(screen.contains("ModelType.LANGUAGE"))
        assertTrue(screen.contains("ModelType.VISION"))
        assertTrue(screen.contains("group.languageModelId"))
        assertTrue(screen.contains("group.visionModelId"))
        assertTrue(screen.contains("replyBufferEnabled"))
        assertTrue(screen.contains("deepThinking"))
        assertFalse("Groups have no proactive setting", screen.contains("proactiveEnabled"))
        assertFalse("Group settings must not mutate a source library card", screen.contains("CharacterLibraryStore.update"))
    }

    @Test fun failedGroupStepsRetryIndividuallyIncludingPlannerSystemRows() {
        assertTrue(chat().contains("viewModel.retryGroupMessage(msgItem.msg.id)"))
        val bubble = source("ui/components/ChatBubble.kt")
        val systemRow = bubble.substringAfter("private fun GroupSystemRow(")
            .substringBefore("internal fun MessageAttribution")
        assertTrue(systemRow.contains("onRegenerate"))
        assertTrue(systemRow.contains("MultiSelectAction.DELETE"))
    }

    @Test fun groupDraftCollectsExternalMentionUpdatesAndRetainsSharedComposer() {
        assertTrue(chat().contains("viewModel.inputDrafts.collectAsState()"))
        assertTrue(chat().contains("inputDrafts[currentConvId.orEmpty()].orEmpty()"))
        assertTrue(chat().contains("onDraftChanged = { txt -> viewModel.saveDraft(currentConvId, txt) }"))
        assertTrue(chat().contains("viewModel.stopGeneration()"))
    }
}
