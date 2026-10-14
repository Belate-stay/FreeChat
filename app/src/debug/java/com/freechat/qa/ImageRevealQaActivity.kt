package com.freechat.qa

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import com.freechat.data.GenerationPhase
import com.freechat.data.LocalStore
import com.freechat.model.*
import com.freechat.ui.animation.LocalMotionEnabled
import com.freechat.ui.components.ChatBubble
import com.freechat.ui.theme.*
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.io.File

/** Offline visual/geometry check of the real streaming -> result composable, including AGSL. */
class ImageRevealQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dark = intent.getBooleanExtra("dark", false)
        val companion = intent.getBooleanExtra("companion", false)
        val picture = File(filesDir, "reveal-qa.png")
        val bitmap = Bitmap.createBitmap(640, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 640f, 800f,
                intArrayOf(0xfff3e2c0.toInt(), 0xffd5ad91.toInt(), 0xff63949b.toInt()), null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, 640f, 800f, paint)
        paint.shader = null
        paint.color = 0xffe5cfaa.toInt()
        canvas.drawCircle(465f, 160f, 58f, paint)
        paint.color = 0xff739e9a.toInt()
        canvas.drawOval(-250f, 400f, 720f, 1050f, paint)
        paint.color = 0xff416f72.toInt()
        canvas.drawOval(130f, 550f, 1050f, 1150f, paint)
        LocalStore.writeBytes(picture, ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.PNG, 100, this) }.toByteArray())
        bitmap.recycle()
        setContent {
            FreeChatTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, ColorTheme.PINE) {
                CompositionLocalProvider(LocalMotionEnabled provides !intent.getBooleanExtra("reduced", false)) {
                    val colors = LocalFreeChatColors.current
                    var finished by remember { mutableStateOf(false) }
                    var phase by remember { mutableStateOf(GenerationPhase.IMAGE_GENERATING) }
                    var sizeBefore by remember { mutableStateOf<Pair<Int, Int>?>(null) }
                    var topBefore by remember { mutableStateOf<Float?>(null) }
                    var stable by remember { mutableStateOf(true) }
                    val started = remember { SystemClock.elapsedRealtime() }
                    LaunchedEffect(Unit) {
                        delay(4500)
                        phase = GenerationPhase.IMAGE_RECEIVING
                        delay(500)
                        Log.i("ImageRevealQA", "FINISH t=${SystemClock.elapsedRealtime() - started}")
                        finished = true
                        delay(2000)
                        Log.i("ImageRevealQA", "${if (stable) "PASS" else "FAIL"} stable=$stable dark=$dark companion=$companion reduced=${intent.getBooleanExtra("reduced", false)}")
                    }
                    Column(Modifier.fillMaxSize().background(colors.Background).statusBarsPadding().padding(16.dp)) {
                        Text("图片完成过渡 · 离线验证", color = colors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(24.dp))
                        ChatBubble(Message(id = "reveal-qa", role = Role.ASSISTANT,
                            content = if (finished && companion) com.freechat.i18n.LocalStrings.current.sceneImageVisualOnly else "",
                            imageUrls = if (finished) listOf(picture.absolutePath) else emptyList(),
                            isStreaming = !finished, mode = if (companion) ChatMode.COMPANION else ChatMode.STANDARD,
                            sceneVisualization = finished && companion, modelName = "QA Image Model", thinkingTimeMs = 5000),
                            dark, liveThinkingMs = 5000,
                            liveImagePhase = phase.takeUnless { finished },
                            onRegenerate = if (finished) ({}) else null,
                            onDeleteScene = if (finished && companion) ({}) else null,
                            onEnterMultiSelect = {},
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                val size = coordinates.size.width to coordinates.size.height
                                val top = coordinates.positionInWindow().y
                                if (sizeBefore == null) { sizeBefore = size; topBefore = top }
                                else if (sizeBefore != size || kotlin.math.abs(requireNotNull(topBefore) - top) > 1f) stable = false
                                Log.i("ImageRevealQA", "BOUNDS t=${SystemClock.elapsedRealtime() - started} size=$size y=$top")
                            })
                    }
                }
            }
        }
    }
}
