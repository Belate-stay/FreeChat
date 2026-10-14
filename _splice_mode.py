import re, sys

path = 'app/src/main/java/com/freechat/ui/screens/CharacterSetupScreen.kt'
txt = open(path, encoding='utf-8').read()

def splice(start_pat, end_pat, replacement, name):
    global txt
    m = re.search(start_pat, txt)
    if not m:
        print('FAIL start not found:', name); sys.exit(1)
    n = re.search(end_pat, txt[m.start():])
    if not n:
        print('FAIL end not found:', name); sys.exit(1)
    abs_end = m.start() + n.start()
    old = txt[m.start():abs_end]
    txt = txt[:m.start()] + replacement + txt[abs_end:]
    print('OK', name, f'({abs_end - m.start()} chars)')
    return old

# ── 1) 取出旧「对话模式」区（含卡片/plotLength/例句），从模拟设置区移除 ──
old_mode = splice(r'            // ──── 对话模式（模拟设置；微信聊天/动作演绎/剧情补足三选一，取代 1\.0\.28 前的「剧情模式」开关） ────',
                  r'            // ──── 增强检索（原「高质量检索回复」',
                  '', 'remove old mode section')

# 从旧区里切出「三档卡片 + 例句」（去掉 plotLength 子块），作为新顶部区的可选卡内容
# 旧区结构：SectionLabel → GlassCard { Column { 标题列 + 三档凹槽 + 锁定说明 + plotLength + 例句 } }
# 去掉 plotLength 子块（含它前面的注释与分隔线），其余保留
m_plot = re.search(r'\n                    // 单次回复长度：.*?(?=\n                    // 例句引导：)', old_mode, re.S)
if not m_plot:
    print('FAIL plotLength sub-block not found'); sys.exit(1)
cards_body = old_mode[:m_plot.start()] + old_mode[m_plot.end():]
plot_block = m_plot.group(0)

# 点击行为：锁定分支在这条路径里不可达（可选卡只在未锁定时出现），去掉死分支
cards_body = cards_body.replace(
    '''                                    .clickable {
                                        when {
                                            modeLocked -> showModeLocked = true
                                            dialogueMode == mode -> Unit
                                            !isEditing -> {
                                                dialogueMode = mode
                                                showModePickWarn = true
                                            }
                                            else -> dialogueMode = mode
                                        }
                                    }''',
    '''                                    .clickable {
                                        when {
                                            dialogueMode == mode -> Unit
                                            !isEditing -> {
                                                dialogueMode = mode
                                                showModePickWarn = true
                                            }
                                            else -> dialogueMode = mode
                                        }
                                    }''')

# 例句块前的「还没选档位时不显示」判断在 stage-1 已经是「选了才显示」，保留原样
# 去掉旧 SectionLabel 行（新块自带）
cards_body = re.sub(r'^.*SectionLabel\(Icons\.Filled\.AutoStories, s\.dialogueMode\)\n', '', cards_body, count=1)
# 去掉旧注释头
cards_body = re.sub(r'^.*取代 1\.0\.28 前的「剧情模式」开关） ────\n', '', cards_body, count=1)

# ── 2) plotLength 抽成独立私有可组合函数（两种分支共用） ──
# 旧 plot_block 是 GlassCard 内 Column 里的子块（含分隔线+注释+Column）。改写成独立卡。
plot_lines = plot_block.strip('\n')
# 提取核心 Column（从「Column(」到结尾的两个收尾括号），外面重新包 GlassCard
m_col = re.search(r'Column\(\n\s*modifier = Modifier\n\s*\.fillMaxWidth\(\)\n\s*\.frostedCard\(.*$', plot_lines, re.S)
if not m_col:
    print('FAIL plot column not found'); sys.exit(1)
plot_col = m_col.group(0)
# 去掉外层 if 的收尾括号（最后一行独立 }）；Column 自己的收尾括号保留
lines = plot_col.rstrip().split('\n')
if lines[-1].strip() == '}':
    lines = lines[:-1]
plot_col = '\n'.join(lines)
# 缩进整体减 4 空格（从 GlassCard 内层挪到独立函数体）
plot_col = '\n'.join((l[4:] if l.startswith('    ') else l) for l in plot_col.split('\n'))
plot_col = '        ' + plot_col.lstrip() if False else plot_col  # 首行缩进对齐（保持原样，仅语法正确即可）

PLOT_FN = '''
/** 单次回复长度（叙事档专属；1.0.93 起从对话模式卡里拆出——模式区两态都可能用它） */
@Composable
private fun PlotLengthCard(
    plotLength: Int,
    onPlotLengthChange: (Int) -> Unit,
    hazeState: HazeState,
    advancedMaterial: Boolean,
    colors: com.freechat.ui.theme.FreeChatColors
) {
    val s = LocalStrings.current
    GlassCard(hazeState, advancedMaterial, colors) {
''' + plot_col + '''
    }
}
'''

# ── 3) 组装新顶部区（先选模式 / 只读展示 两态） ──
TOP_BLOCK = '''            // ──── 对话模式（置顶，1.0.93 流程重构）：创建先选档再进设定；锁定后只读展示 ────
            // 选择态带三档介绍与对话例句（帮用户分清区别）；编辑且未聊过时同款可改；聊过即锁。
            SectionLabel(Icons.Filled.AutoStories, s.dialogueMode)
            if (!modeChosen || (!modeLocked && isEditing)) {
''' + cards_body + '''            } else {
                GlassCard(hazeState, advancedMaterial, colors) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                when (dialogueMode) {
                                    DialogueMode.ACTION -> s.dialogueModeAction
                                    DialogueMode.PLOT -> s.dialogueModePlot
                                    else -> s.dialogueModeWechat
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.TextPrimary
                            )
                            Text(
                                when (dialogueMode) {
                                    DialogueMode.ACTION -> s.dialogueModeActionDesc
                                    DialogueMode.PLOT -> s.dialogueModePlotDesc
                                    else -> s.dialogueModeWechatDesc
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp),
                                color = colors.TextSecondary
                            )
                            if (modeLocked) {
                                Text(s.dialogueModeLockedHint, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextTertiary)
                            }
                        }
                        // 创建流程里选完还能回上一步重选（正文本身不可改，重选走流程）
                        if (!isEditing) {
                            Text(
                                s.modeRepick,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.Primary,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                    .clickable { modeChosen = false }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
            // 单次回复长度：动作演绎/剧情补足专属设置（跟模式走，独立成节）
            if (dialogueMode == DialogueMode.ACTION || dialogueMode == DialogueMode.PLOT) {
                SectionLabel(Icons.Filled.ShortText, s.plotLength)
                PlotLengthCard(plotLength, { plotLength = it }, hazeState, advancedMaterial, colors)
            }

'''

# 顶部区插到「头像」之前；设定内容整体包进 if (modeChosen)
# 结束边界 = 底部按钮前的 Spacer
# ⚠ splice() 已就地改全局 txt，返回值是被删片段——不能再赋回 txt（会把整份清空）
splice(r'            // ──── 头像（预览态只读；点击弹查看/编辑/更换三选项） ────',
       r'            // ──── 头像（预览态只读；点击弹查看/编辑/更换三选项） ────',
       TOP_BLOCK + '            if (modeChosen) {\n', 'insert top block + open stage gate')
# ↑ 同一锚点做 replacement=插入（start==end 时删除 0 字符、原地插入）

# 在底部按钮前关闸
print('DEBUG close-gate precheck:', bool(re.search(r'\n            Spacer\(Modifier\.height\(12\.dp\)\)\n\n            // ──── 底部按钮（仅创建时显示；编辑时用右上角「保存」按钮） ────', txt)), 'txt len', len(txt))
splice(r'\n            Spacer\(Modifier\.height\(12\.dp\)\)\n\n            // ──── 底部按钮（仅创建时显示；编辑时用右上角「保存」按钮） ────',
       r'\n            Spacer\(Modifier\.height\(12\.dp\)\)\n\n            // ──── 底部按钮（仅创建时显示；编辑时用右上角「保存」按钮） ────',
       '\n            }\n\n            Spacer(Modifier.height(12.dp))\n\n            // ──── 底部按钮（仅创建时显示；编辑时用右上角「保存」按钮） ────',
       'close stage gate')

# ── 4) plotLength 独立函数追加到文件末尾（放 ChatExampleBubble 后面） ──
txt += PLOT_FN
print('OK append PlotLengthCard')

open(path, 'w', encoding='utf-8').write(txt)
print('written.')
