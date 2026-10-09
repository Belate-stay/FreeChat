package com.freechat.qa

import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.FrameMetrics
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.freechat.data.VoiceSpectrumAnalyzer
import com.freechat.data.VoiceSpectrumFrame
import com.freechat.model.ColorTheme
import com.freechat.model.CharacterProfile
import com.freechat.model.DialogueMode
import com.freechat.model.InputStyle
import com.freechat.model.ThemeMode
import com.freechat.ui.animation.LocalMotionEnabled
import com.freechat.ui.components.ChatInput
import com.freechat.ui.components.ImageGenerationPlaceholder
import com.freechat.ui.components.FullScreenImagePreview
import com.freechat.ui.components.PreviewImage
import com.freechat.ui.components.VoiceSpectrumBars
import com.freechat.ui.components.voiceDroplet
import com.freechat.ui.theme.FreeChatTheme
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.screens.CharacterSetupScreen
import com.freechat.viewmodel.ChatViewModel
import com.freechat.ui.animation.MotionButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin

/** Debug-only, offline rendering/gesture fixture. This activity is never packaged in release. */
class RenderQaActivity : ComponentActivity() {
    private lateinit var metricsThread: HandlerThread
    private var frames = 0
    private var slowFrames = 0
    private var totalNanos = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        metricsThread = HandlerThread("render-qa-metrics").apply { start() }
        window.addOnFrameMetricsAvailableListener({ _, metrics, _ ->
            val duration = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 0L) {
                frames++
                totalNanos += duration
                if (duration > 16_666_667) slowFrames++
            }
            if (frames > 0 && frames % 180 == 0) Log.i("RenderQA",
                "frames=$frames over16ms=$slowFrames averageMs=${totalNanos / frames / 1_000_000.0}")
        }, Handler(metricsThread.looper))
        val dark = intent.getBooleanExtra("dark", false)
        val reduced = intent.getBooleanExtra("reduced", false)
        val compact = intent.getBooleanExtra("compact", false)
        val imageOnly = intent.getBooleanExtra("imageOnly", false)
        val probe = intent.getBooleanExtra("probe", false)
        val gallery = intent.getBooleanExtra("gallery", false)
        val settingsMode = intent.getStringExtra("settingsMode")
        val settingsViewModel = if (settingsMode != null) ViewModelProvider(this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application))[ChatViewModel::class.java] else null
        val profile = if (settingsMode == "preview") CharacterProfile(name = "离线检查角色",
            dialogueMode = DialogueMode.ACTION, personalityText = "安静、自然、温和", age = "20") else null
        val galleryImages = if (gallery) prepareGalleryImages() else emptyList()
        val imagePhase = runCatching { com.freechat.data.GenerationPhase.valueOf(intent.getStringExtra("phase") ?: "IDLE") }
            .getOrDefault(com.freechat.data.GenerationPhase.IDLE)
        val theme = runCatching { ColorTheme.valueOf(intent.getStringExtra("theme") ?: "BLUE") }.getOrDefault(ColorTheme.BLUE)
        setContent {
            FreeChatTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, colorTheme = theme) {
                CompositionLocalProvider(LocalAdvancedMaterial provides true, LocalMotionEnabled provides !reduced) {
                    if (settingsViewModel != null) {
                        CharacterSetupScreen(settingsViewModel, dark, onBack = { finish() }, onCreate = {}, initial = profile)
                    } else if (gallery) {
                        GalleryQaScene(galleryImages) { image ->
                            Log.i("RenderQA", "galleryDownload message=${image.messageId} source=${image.source}")
                            File(cacheDir, "render-qa-gallery/selected-source.txt").writeText(image.source)
                        }
                    } else {
                    val colors = LocalFreeChatColors.current
                    val keyboardHeight = with(LocalDensity.current) { WindowInsets.ime.getBottom(this).toDp() }
                    var gestureStatus by remember { mutableStateOf("长按语音键；滑出松手取消") }
                    val spectrum = remember { MutableStateFlow(VoiceSpectrumFrame(0f, FloatArray(16))) }
                    // Deterministic PCM at known frequencies exercises the actual analyzer, not random heights.
                    LaunchedEffect(imageOnly) {
                        if (imageOnly) return@LaunchedEffect
                        val analyzer = VoiceSpectrumAnalyzer()
                        val pcm = ShortArray(640)
                        var t = 0
                        while (true) {
                            val amplitude = .12 + .10 * sin(t * .032)
                            for (i in pcm.indices) {
                                val p = (t * 640 + i) / 16000.0
                                pcm[i] = (32767 * amplitude *
                                    (.62 * sin(2 * PI * 240 * p) + .24 * sin(2 * PI * 1500 * p) +
                                        .12 * sin(2 * PI * 4200 * p))).toInt().toShort()
                            }
                            spectrum.value = analyzer.analyze(pcm)
                            t++
                            delay(40)
                        }
                    }
                    Box(Modifier.fillMaxSize().background(colors.Background).systemBarsPadding()) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp)) {
                            Text("FreeChat · 渲染检查", color = colors.TextPrimary,
                                style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(8.dp))
                            Text(gestureStatus, color = colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(12.dp))
                            ImageGenerationPlaceholder(colors, phase = imagePhase)
                            if (probe) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (pose in listOf(0f, .25f, .5f, .75f)) {
                                    val progress = remember { mutableFloatStateOf(pose) }
                                    val microphone = remember { mutableStateOf(Offset.Zero) }
                                    Box(Modifier.size(76.dp, 60.dp)
                                        .onGloballyPositioned {
                                            microphone.value = it.positionInWindow() + Offset(it.size.width * .9f, it.size.height * .5f)
                                        }.voiceDroplet(progress, microphone)
                                        .background(colors.Primary), contentAlignment = Alignment.Center) {
                                        Text("$pose", color = colors.OnPrimary)
                                    }
                                }
                            }
                            if (!imageOnly) VoiceSpectrumBars(spectrum, dark, Modifier.fillMaxWidth().height(110.dp))
                        }
                        ChatInput(
                            onSend = { Log.i("RenderQA", "typedSend") }, onStop = {}, isLoading = false, isDark = dark,
                            onRecognizeVoice = { Log.i("RenderQA", "recognizeVoice"); delay(120); "本地测试语音" },
                            onVoiceInput = { Log.i("RenderQA", "sendVoice"); gestureStatus = it },
                            onRecordingChanged = { Log.i("RenderQA", "recording=$it") },
                            voiceSessionKey = "offline-qa",
                            keyboardHeightDp = keyboardHeight,
                            inputStyle = if (compact) InputStyle.COMPACT else InputStyle.COMPLETE,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                    }
                }
            }
        }
    }

    private fun prepareGalleryImages(): List<PreviewImage> {
        val directory = File(cacheDir, "render-qa-gallery").apply { mkdirs() }
        val shapes = listOf(
            Triple(480, 800, Color.rgb(40, 97, 160)),
            Triple(640, 640, Color.rgb(122, 70, 154)),
            Triple(960, 540, Color.rgb(27, 127, 103)),
            Triple(540, 960, Color.rgb(169, 81, 45)),
        )
        val labels = listOf("UPLOAD", "GENERATED", "SCENE", "UPLOAD 2")
        return shapes.mapIndexed { index, (width, height, color) ->
            val file = File(directory, "gallery-${index + 1}.png")
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(color)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE; strokeWidth = 4f }
            canvas.drawLine(0f, height / 2f, width.toFloat(), height / 2f, paint)
            canvas.drawLine(width / 2f, 0f, width / 2f, height.toFloat(), paint)
            paint.textSize = width / 14f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${index + 1} ${labels[index]}", width / 2f, height / 2f - 24f, paint)
            paint.textSize = width / 22f
            canvas.drawText("${width} x $height", width / 2f, height / 2f + 56f, paint)
            paint.style = Paint.Style.STROKE
            canvas.drawRect(12f, 12f, width - 12f, height - 12f, paint)
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            PreviewImage("gallery-${index + 1}", file.absolutePath)
        }
    }

    override fun onDestroy() {
        if (::metricsThread.isInitialized) metricsThread.quitSafely()
        super.onDestroy()
    }
}

@Composable
private fun GalleryQaScene(images: List<PreviewImage>, onDownload: (PreviewImage) -> Unit) {
    val colors = LocalFreeChatColors.current
    var open by remember { mutableStateOf(true) }
    var selection by remember { mutableStateOf("尚未选择下载图片") }
    Column(Modifier.fillMaxSize().background(colors.Background).systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("FreeChat · 离线图库检查", color = colors.TextPrimary, style = MaterialTheme.typography.titleLarge)
        Text("第 2 张为起点：左右切页，双击缩放，放大后平移，原尺寸下竖向关闭。", color = colors.TextSecondary)
        Text(selection, color = colors.TextPrimary)
        MotionButton(onClick = { open = true }) { Text("重新打开第 2 张") }
    }
    if (open) FullScreenImagePreview(images, initialIndex = 1, originRect = null,
        onDownload = { image ->
            selection = "下载选中：${image.messageId}\n${image.source}"
            onDownload(image)
        }, onDismiss = { open = false; Log.i("RenderQA", "galleryDismiss") })
}
