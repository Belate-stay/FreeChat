# -*- coding: utf-8 -*-
# 打包核验：四连修关键标记必须在 APK 的 dex 里（多 dex 全扫）
import glob
import sys
import os

os.chdir(sys.argv[1] if len(sys.argv) > 1 else ".")
markers = [
    b"preloadInFlight",
    b"tts_cache",
    b"synthesizeWithRetry",
    b"preloadVoiceMessages",
    "点点点省着用".encode("utf-8"),
    "不是高冷疏离".encode("utf-8"),
    "独立有主见".encode("utf-8"),
    # 10-19 深夜「回复形态活变通」改写：旧标记「碎片短句是日常的默认形态」已被下句替换，钉新文案
    "默认短句碎片式".encode("utf-8"),
    "跟着内容走".encode("utf-8"),
    # 2026-10-19 追加三单
    b"PreloadTask",
    b"speakSynthesizing",
    "转文字".encode("utf-8"),
    "如何获取API？".encode("utf-8"),
    b"platform.deepseek.com",
]
found = {m: [] for m in markers}
for f in sorted(glob.glob("classes*.dex")):
    data = open(f, "rb").read()
    for m in found:
        if m in data:
            found[m].append(f)
missing = 0
for m, fs in found.items():
    label = m.decode("utf-8", "replace")
    print(label, fs if fs else "MISSING-ALL")
    if not fs:
        missing += 1
print("MISSING=%d" % missing)
sys.exit(1 if missing else 0)
