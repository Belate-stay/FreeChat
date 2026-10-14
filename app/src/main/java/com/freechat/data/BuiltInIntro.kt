package com.freechat.data

import com.freechat.model.ChatMode
import com.freechat.model.Conversation

/**
 * 内置对话「FreeChat简介说明」（1.1.29，用户工单①）。
 *
 * 和「Claude风格助理」一样的出生方式：首启种入、默认置顶、删除即永不再来（`intro_deleted`
 * 闩随设置同步，跨设备不复活）——但**不修改新规则、不内置提示词**：它就是一条普通对话，
 * 预置了作者那条「FreeChat 简介」问答的 12 条聊天记录，照常参与同步（内置同步给所有用户）。
 *
 * 消息用**固定 ID + 固定时间戳**原样内嵌（reasoning/searchSources/modelName 等字段全保留，
 * 完全还原作者视角下的显示效果）。固定 ID 让多设备播种天然幂等：同 id 合并，不生重复。
 */
object BuiltInIntro {
    /** 固定对话 id（多设备播种同一对象，合并收敛） */
    const val CONV_ID = "a04aac25-9397-49ca-a93c-49c2a1bce1c6"

    const val TITLE = "FreeChat简介说明"

    /** 种子消息（与作者视角逐字节一致的存档 JSON；裸数组 = 消息文件格式） */
    val MESSAGES_JSON: String = """
[
 {
  "content": "给我简单介绍下FreeChat并指出其特点。",
  "failed": false,
  "favorited": false,
  "id": "63767206-5da8-45f9-92b7-5fb2f2468b59",
  "imagePaths": [],
  "imageUrls": [],
  "isStreaming": false,
  "mode": "STANDARD",
  "reasoningContent": "",
  "role": "USER",
  "sceneVisualization": false,
  "searchSources": [],
  "thinkingTimeMs": 0,
  "timestamp": 1791830062126
 },
 {
  "answerLinksRequested": false,
  "content": "**一句话结论**：FreeChat 是作者 *++Belate++* 一个人开发的、完全免费开源无广告的 AI 聊天软件，最大特点是「拟人模式」——用自定义角色卡和 AI 角色沉浸式聊天。\n\n## 它是什么\n\n一款个人开发的 AI 聊天应用，分两条主线：**拟人模式**（主打特色，带角色卡，可自定人设与 AI 角色沉浸式对话）和**标准问答**（就是普通 AI 助手：问答、联网搜索、生图识图、文档生成）。\n\n## 主要特点\n\n1. **免费、开源、无广告**\n    不商用不盈利，开源仓库公开可查，这是它和商业产品的本质差别。\n\n2. **模型可控**\n    能力来自第三方大模型（如小米 MiMo、DeepSeek、豆包等），用户可以自己填 API Key，成本透明、隐私在自己手里。\n\n3. **开箱即用**\n    同时也内置官方模型，**装完不用任何配置就能聊**。\n\n4. **门槛低**\n    作者做它的初衷就是给 AI 文游玩家找「中间地带」：比极客工具（如 TavernSilly 一类）好上手，又比大众产品的智能体角色还原得更好。\n\n## 平台与获取\n\n安卓客户端为主力，另有网页版（freechater.com，登录后云同步）和 Windows 版。源码在 GitHub - Belate-stay/FreeChat。\n\n作者 **Belate**（原名胡勃阳）是软件工程专业的大学生，**一个人，没有团队，也没有自研大模型**。有 bug 或建议可以直接找他，微信号 **belate_coder**。",
  "failed": false,
  "favorited": false,
  "id": "3c264f5b-a497-427b-9959-3d12d637a321",
  "imagePaths": [],
  "imageUrls": [],
  "isStreaming": false,
  "mode": "STANDARD",
  "modelName": "DeepSeek-V4.1-Flash",
  "reasoningContent": "",
  "role": "ASSISTANT",
  "sceneVisualization": false,
  "searchSources": [
   {
    "title": "GitHub - Belate-stay/FreeChat",
    "url": "https://github.com/Belate-stay/FreeChat"
   }
  ],
  "thinkingTimeMs": 0,
  "timestamp": 1791830065560
 },
 {
  "content": "作者的建项初衷是什么？",
  "failed": false,
  "favorited": false,
  "id": "e7000cbc-77f6-4dc3-a733-5374d15f55c9",
  "imagePaths": [],
  "imageUrls": [],
  "isStreaming": false,
  "mode": "STANDARD",
  "reasoningContent": "",
  "role": "USER",
  "sceneVisualization": false,
  "searchSources": [],
  "thinkingTimeMs": 0,
  "timestamp": 1791830172449
 },
 {
  "answerLinksRequested": false,
  "content": "作者之所以自己动手，是因为他觉得市面上的两类产品都不合适，想做「中间地带」的那一款。\n\n**具体来说：**\n\n1. **导火索是他的身份**——作者本身是 AI 文游（角色扮演聊天）玩家，是这个需求的真实用户。\n\n2. **两类现成方案各有短板**\n    - 极客工具（如 TavernSilly 一类）：功能强，但**门槛太高**，普通人折腾不动。\n    - 大众产品（如豆包智能体一类）：上手容易，但**角色效果差**，还原不到位。\n\n3. **于是他自己定了几条标准**\n    **设置项足够丰富**、**角色还原足够好**、**门槛又低**，还要**便携、随时能聊**。\n\n此外他也一直坚持「模型可控」——让用户自配 API Key，把成本和隐私握在自己手里；免费开源无广告，则是他与商业产品的本质区别。",
  "failed": false,
  "favorited": false,
  "id": "b27915f2-f661-4d71-b8b8-b97273abaaaa",
  "imagePaths": [],
  "imageUrls": [],
  "isStreaming": false,
  "mode": "STANDARD",
  "modelName": "DeepSeek-V4.1-Flash",
  "reasoningContent": "",
  "role": "ASSISTANT",
  "sceneVisualization": false,
  "searchSources": [],
  "thinkingTimeMs": 0,
  "timestamp": 1791830175063
 },
 {
  "content": "如何将FreeChat接入微信？",
  "failed": false,
  "favorited": false,
  "id": "493df483-e336-4651-a33b-6903e59470c1",
  "imagePaths": [],
  "imageUrls": [],
  "isStreaming": false,
  "mode": "STANDARD",
  "reasoningContent": "",
  "role": "USER",
  "sceneVisualization": false,
  "searchSources": [],
  "thinkingTimeMs": 0,
  "timestamp": 1791830234335
 },
 {
  "answerLinksRequested": false,
  "content": "FreeChat 的「微信聊天」模式可以让角色接入你的微信，好友在微信里发消息，角色会自动回复；**手机不需要常驻后台**。\n\n## 前提条件\n\n1. 对话模式必须是拟人模式下的「**微信聊天**」——「动作演绎」「剧情补足」不支持微信接入，这个在建角色时就要选好。\n2. 需要先**注册并登录 FreeChat 账号**（消息经云端中转，得由账号明确消息主体）。\n\n## 操作步骤\n\n1. 新建对话 → 选择**拟人模式**\n2. 对话模式选「**微信聊天**」→ 创建\n3. 点进该对话，在聊天页**标题栏点绿色的对话图标**（连接微信）\n4. 按提示**保存弹出的二维码**\n5. 打开微信**扫一扫**识别该二维码\n6. 按微信的步骤完成**授权确认**\n\n绑定成功后，微信好友发来的消息就会由云端的角色自动回复。\n\n## 几点说明\n\n- **原理**：走的是微信**官方机器人通道**授权接入，模型商、服务器与微信三端直连，收消息、生成回复、发消息都在云端完成。\n- **记录不同步**：微信只作消息桥梁，FreeChat 里的历史聊天记录**不会同步到微信**。\n- **设置仍在 FreeChat 内调**：角色的模拟设置要去 FreeChat 改，不在微信里改。\n- **一个对话限制**：同一时间只允许绑定一个对话；**解绑也在同一个入口**。\n- **配套机制**：有「**回复缓冲**」（连发的几条消息会缓存几秒再一并理解回复，更像真人、也省 token，时长可调）和「**主动智能**」（角色会主动发消息，目前是体验性质，受安卓后台机制影响可能不及时）。",
  "failed": false,
  "favorited": false,
  "id": "511d140d-c6d5-41fd-8b80-5eefe7c9fc58",
  "imagePaths": [],
  "imageUrls": [],
  "isStreaming": false,
  "mode": "STANDARD",
  "modelName": "DeepSeek-V4.1-Flash",
  "reasoningContent": "",
  "role": "ASSISTANT",
  "sceneVisualization": false,
  "searchSources": [],
  "thinkingTimeMs": 0,
  "timestamp": 1791830238170
 },
 {
  "content": "如何使用生成场景图功能？",
  "failed": false,
  "favorited": false,
  "id": "335c8028-1a1b-4cd5-92f4-5cfc8844cb12",
  "imagePaths": [],
  "imageUrls": [],
  "isStreaming": false,
  "mode": "STANDARD",
  "reasoningContent": "",
  "role": "USER",
  "sceneVisualization": false,
  "searchSources": [],
  "thinkingTimeMs": 0,
  "timestamp": 1791830354390
 },
 {
  "answerLinksRequested": false,
  "content": "这个功能是把当前对话场景（上下文 + 人物设定 + 角色记忆）交给生图模型，生成一张和剧情同步的插图，用来做视觉补足。\n\n## 操作路径\n\n1. 在聊天输入框点**图片 / 加号菜单**\n2. 选择「**生成当前场景图**」\n3. 生成完成后，每张场景图都可以**重新生成**或**删除**\n\n## 使用前提\n\n只支持拟人模式下的**动作演绎 / 剧情补足**（也就是叙事类对话模式）。这两类以外的模式用不了。\n\n## 可调项\n\n角色的**模拟设置 → 图片预设**里可以设：\n\n- **画幅**：1:1、4:3、3:2 等\n- **主体偏好**：全景构图 / 主体优先\n\n这两项会随每次场景图请求生效，也就是改完下次生成就按新设定走。\n\n## 一点重要区别\n\n场景图是「**视觉行**」——**只看不进剧情**，不会影响故事走向和角色记忆，可以放心生成。另外生成时会自动带上角色的**形象参考图**，尽量保证人物长相前后一致。",
  "failed": false,
  "favorited": false,
  "id": "8936ac77-925c-40a9-8e07-ad629751b635",
  "imagePaths": [],
  "imageUrls": [],
  "isStreaming": false,
  "mode": "STANDARD",
  "modelName": "DeepSeek-V4.1-Flash",
  "reasoningContent": "",
  "role": "ASSISTANT",
  "sceneVisualization": false,
  "searchSources": [],
  "thinkingTimeMs": 0,
  "timestamp": 1791830356902
 },
 {
  "content": "如何获取最新版本？",
  "failed": false,
  "favorited": false,
  "id": "926e7234-25de-4633-9b5e-53f306b928d9",
  "imagePaths": [],
  "imageUrls": [],
  "isStreaming": false,
  "mode": "STANDARD",
  "reasoningContent": "",
  "role": "USER",
  "sceneVisualization": false,
  "searchSources": [],
  "thinkingTimeMs": 0,
  "timestamp": 1791830405936
 },
 {
  "answerLinksRequested": false,
  "content": "在 App 里就能查更新，路径是：\n\n1. 此页面**右滑**打开侧滑页\n2. 点下方「**设置**」\n3. 滑到底部「**关于FreeChat**」\n4. 点「**检查更新**」\n\n同一个页面里还有「**更新日志**」，可以看每个版本具体改了什么。\n\n如果想全新下载或换设备安装，直接去下载页 **freechater.com**；安卓版的源码和发行包也在 GitHub 仓库 Belate-stay/FreeChat。",
  "failed": false,
  "favorited": false,
  "id": "0d5544ac-6fdd-4af6-92f3-3e6d0470f7de",
  "imagePaths": [],
  "imageUrls": [],
  "isStreaming": false,
  "mode": "STANDARD",
  "modelName": "DeepSeek-V4.1-Flash",
  "reasoningContent": "",
  "role": "ASSISTANT",
  "sceneVisualization": false,
  "searchSources": [
   {
    "title": "Belate-stay/FreeChat",
    "url": "https://github.com/Belate-stay/FreeChat"
   }
  ],
  "thinkingTimeMs": 0,
  "timestamp": 1791830407864
 },
 {
  "content": "用户隐私保障？",
  "failed": false,
  "favorited": false,
  "id": "39a79a7d-72a6-4928-b325-d6e443144c8a",
  "imagePaths": [],
  "imageUrls": [],
  "isStreaming": false,
  "mode": "STANDARD",
  "reasoningContent": "",
  "role": "USER",
  "sceneVisualization": false,
  "searchSources": [],
  "thinkingTimeMs": 0,
  "timestamp": 1791830589438
 },
 {
  "answerLinksRequested": false,
  "content": "FreeChat 的隐私保障主要落在四点：**开源可查、不登录数据全在本机、敏感配置不上云、数据可彻底删除**。更细的技术细节（如传输加密方式、服务器所在地、数据留存期限）产品说明里没有写明，这部分我不确定，需要的话可以直接问作者。\n\n## 能确认的几点\n\n1. **开源透明**\n    代码完全公开（GitHub - Belate-stay/FreeChat），不是黑盒，可自行查看审查。\n\n2. **本地优先**\n    ++不登录也能照常用++，此时数据全部保存在本机，不上传。\n\n3. **敏感配置不上云**\n    即使注册登录并开启云同步，同步的只是对话、消息、记忆、新规则、设置这类内容；**API Key 等敏感配置不会上传服务器。**\n\n4. **模型可控**\n    支持自行接入 API Key，用哪个模型、成本多少都由自己决定，这也是作者强调的「隐私安全」的核心思路——数据不经由他统一中转。\n\n5. **删除是彻底的**\n    - 删除对话 = 云端一并删除，不留残余；\n    - 删除聊天消息会同步作废由它衍生的记忆，不会出现「删了还记着」；\n    - 设置 →「AI记忆」里可以查看和删除全局记忆。\n\n6. **搜索源可选**\n    联网搜索可用免费匿名搜索（AnySearch 等），也可以填自己的搜索 API Key。\n\n## 需要留意的两点\n\n1. **内容会经过模型服务**\n    回复由第三方大模型生成（内置官方模型或你自己的 Key），这中间的调用链涉及模型商；微信接入时消息还会经云端中转。这是功能本身的机制，不是额外收集。\n\n2. **细节未公开**\n    具体的数据处理与合规细节没有对外说明，我无法给出确切结论。作者是一个人开发，有疑问直接微信 **belate_coder** 问最准。",
  "failed": false,
  "favorited": false,
  "id": "cf48e6e9-d968-4a73-b295-f76ee49390d8",
  "imagePaths": [],
  "imageUrls": [],
  "isStreaming": false,
  "mode": "STANDARD",
  "modelName": "DeepSeek-V4.1-Flash",
  "reasoningContent": "",
  "role": "ASSISTANT",
  "sceneVisualization": false,
  "searchSources": [],
  "thinkingTimeMs": 0,
  "timestamp": 1791830596269
 }
]""".trim()

    /** 种入的对话对象：标准档、默认置顶、计数对齐消息条数 */
    fun conversation(): Conversation = Conversation(
        id = CONV_ID,
        title = TITLE,
        createdAt = 1791830062129L,
        updatedAt = 1791830596271L,
        messageCount = 12,
        isPinned = true,
        mode = ChatMode.STANDARD
    )
}
