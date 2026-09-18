package com.freechat.proactive

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.Calendar

/**
 * 一条「AI 自己定下的主动联系计划」。
 *
 * 这不是闹钟本身的副本——闹钟存在系统 AlarmManager 里，本文件只是它的账本：
 * 进程被杀过、手机重启过之后，靠这份账本把闹钟重新挂回来，并且知道当时为什么要找对方。
 */
data class ProactiveRequest(
    val convId: String = "",
    val characterName: String = "",
    val dueAt: Long = 0L,        // 触发时刻（epoch ms，已按静默时段调整过）
    val reason: String = "",     // 模型给的理由：到点时会喂回给它，帮它想起当时想说什么
    val createdAt: Long = 0L,    // 定下这条计划的时刻（= 那条回复产生的时间）
    val retries: Int = 0         // 唤醒失败重试次数（防止后台起不来时无限重排）
)

/** 每日配额与节奏（跨天自动归零） */
data class ProactiveQuota(
    val dateKey: String = "",
    val total: Int = 0,
    val perConv: Map<String, Int> = emptyMap(),
    val lastFiredAt: Map<String, Long> = emptyMap()   // convId -> 上次真正发出主动消息的时刻
)

/**
 * 主动智能的本地账本（filesDir 下一个 JSON）。
 *
 * 为什么不用 DataStore：这里的读写全部发生在「闹钟唤醒 → 服务里」这条冷路径上，
 * 需要同步、确定、无协程依赖的读写；DataStore 是异步 Flow，反而要在服务里再包一层等待。
 */
class ProactiveStore(context: Context) {

    companion object {
        private const val TAG = "FreeChat"
        private const val REQ_FILE = "freechat_proactive.json"
        private const val QUOTA_FILE = "freechat_proactive_quota.json"

        /** 单个角色每天最多主动找几次 */
        const val DAILY_PER_CONV = 3
        /** 整机每天上限（多角色时兜底，免得一整天被轮番轰炸） */
        const val DAILY_TOTAL = 6
        /** 同一角色两条主动消息之间的最小间隔：模型想连发也压住，不黏人 */
        const val MIN_GAP_MS = 90 * 60 * 1000L
        /** 唤醒失败后的重试间隔 */
        const val RETRY_DELAY_MS = 7 * 60 * 1000L
        /** 最多重试几次就放弃 */
        const val MAX_RETRIES = 2

        /** 静默时段 00:30 - 07:30：落在这一段里的计划一律往后挪（凌晨的手机通知是实打实的伤害） */
        const val QUIET_START_MIN = 30          // 00:30
        const val QUIET_END_MIN = 7 * 60 + 30   // 07:30

        /** 上一次主动消息的时刻跨天之后还要不要留（用来压最小间隔，留 26 小时足够） */
        private const val LAST_FIRED_KEEP_MS = 26 * 60 * 60 * 1000L

        /**
         * 进程级锁。**必须**是静态的：@Synchronized 锁的是 this，而这个类在进程里有好几份实例
         * （VM 的、服务的、接收器的），各自锁各自等于没锁。读-改-写交错会互相覆盖：
         * 服务收尾删旧计划的同时，模型刚定下的新计划正在落盘 —— 最后写进去的可能是服务那份空数组，
         * 账本没了、但闹钟还挂在系统里 → 到点查不到请求 → 整条计划静默消失。
         */
        private val LOCK = Any()

        /**
         * 今天的日期键。不用静态 SimpleDateFormat —— 那个类不是线程安全的，
         * 而 ProactiveStore 在本进程里会有好几份实例，它们会在不同线程上同时读配额。
         */
        private fun todayKey(): String {
            val c = Calendar.getInstance()
            return "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH) + 1}-${c.get(Calendar.DAY_OF_MONTH)}"
        }
    }

    private val dir = context.filesDir
    private val gson = Gson()
    private val reqFile = File(dir, REQ_FILE)
    private val quotaFile = File(dir, QUOTA_FILE)

    private fun <T> locked(block: () -> T): T = synchronized(LOCK) { block() }

    /**
     * 先写临时文件再改名。writeText 会先把目标截断成 0 字节，
     * 并发读到那个瞬间就会拿到半截/空内容 —— Gson 解析失败 → emptyList() → 所有待触发计划看起来都没了，
     * 而下一次写入会把这个「空」当成事实固化下来。改名是原子的，读方要么看到旧的、要么看到新的。
     */
    private fun atomicWrite(file: File, text: String) {
        try {
            val tmp = File(dir, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "ProactiveStore write failed: ${file.name}", e)
        }
    }

    // ========== 计划 ==========

    fun all(): List<ProactiveRequest> = locked {
        try {
            if (reqFile.exists()) {
                val type = object : TypeToken<List<ProactiveRequest>>() {}.type
                gson.fromJson<List<ProactiveRequest>>(reqFile.readText(), type) ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "ProactiveStore.all failed", e); emptyList()
        }
    }

    fun get(convId: String): ProactiveRequest? = locked { all().firstOrNull { it.convId == convId } }

    /** 写入/覆盖某个对话的计划（同对话同时只保留一条） */
    fun put(req: ProactiveRequest) = locked {
        write(all().filter { it.convId != req.convId } + req)
    }

    fun remove(convId: String) = locked {
        val list = all()
        val kept = list.filter { it.convId != convId }
        if (kept.size != list.size) write(kept)
    }

    /**
     * 只删「还是那条被触发的计划」：生成过程中模型可能又给自己定了下一次，
     * 那条是新的（dueAt 不同），清掉它等于把刚挂上的闹钟悄悄废掉。
     *
     * 判断和删除必须在同一个锁里做完 —— 分两步（先 get 再 remove）做的话，
     * 新计划可能正好落在两步之间，被按 convId 一并删掉，而它的闹钟还挂在系统里。
     */
    fun removeIfUnchanged(convId: String, expectedDueAt: Long): Boolean = locked {
        val list = all()
        val kept = list.filterNot { it.convId == convId && it.dueAt == expectedDueAt }
        if (kept.size != list.size) { write(kept); true } else false
    }

    fun clearAll() = locked { write(emptyList()) }

    private fun write(list: List<ProactiveRequest>) = atomicWrite(reqFile, gson.toJson(list))

    // ========== 配额与节奏 ==========

    fun quota(): ProactiveQuota = locked {
        val today = todayKey()
        val q = try {
            if (quotaFile.exists()) gson.fromJson(quotaFile.readText(), ProactiveQuota::class.java) else null
        } catch (e: Exception) { null } ?: ProactiveQuota()
        if (q.dateKey == today) q
        else {
            // 跨天归零：次数清零，但**保留**上一条主动消息的时刻 ——
            // 否则 23:55 刚发过，跨过零点之后最小间隔就失效了，00:10 能紧接着再来一条
            val now = System.currentTimeMillis()
            ProactiveQuota(
                dateKey = today,
                lastFiredAt = q.lastFiredAt.filterValues { now - it < LAST_FIRED_KEEP_MS }
            )
        }
    }

    private fun writeQuota(q: ProactiveQuota) = atomicWrite(quotaFile, gson.toJson(q))

    /** 今天还能不能发（按角色 + 整机双重上限） */
    fun canFire(convId: String): Boolean = locked {
        val q = quota()
        q.total < DAILY_TOTAL && (q.perConv[convId] ?: 0) < DAILY_PER_CONV
    }

    /** 记一条已发出的主动消息（只有真的开口了才记，模型沉默的那些轮次不该烧掉当天份额） */
    fun markFired(convId: String) = locked {
        val q = quota()
        writeQuota(q.copy(
            total = q.total + 1,
            perConv = q.perConv + (convId to ((q.perConv[convId] ?: 0) + 1)),
            lastFiredAt = q.lastFiredAt + (convId to System.currentTimeMillis())
        ))
    }

    /** 该对话距上一条主动消息是否已过最小间隔 */
    fun respectsGap(convId: String, at: Long): Boolean = locked {
        val last = quota().lastFiredAt[convId] ?: return@locked true
        at - last >= MIN_GAP_MS
    }

    /** 把时刻推进到满足最小间隔（不够就顺延到 last + MIN_GAP） */
    fun applyGapFloor(convId: String, at: Long): Long = locked {
        val last = quota().lastFiredAt[convId] ?: return@locked at
        val floor = last + MIN_GAP_MS
        if (at < floor) floor else at
    }

    /**
     * 静默时段：落在 00:30-07:30 的计划往后挪到当天/次日的 07:30，并加一点随机抖动
     * （多个角色同时被唤醒时不会挤在同一分钟，也不像机器一样分秒不差）。
     * 纯计算，不碰文件。
     */
    fun adjustQuietHours(at: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = at }
        val mins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        if (mins < QUIET_START_MIN || mins >= QUIET_END_MIN) return at
        cal.set(Calendar.HOUR_OF_DAY, QUIET_END_MIN / 60)
        cal.set(Calendar.MINUTE, QUIET_END_MIN % 60)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= at) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis + kotlin.random.Random.nextLong(0, 25 * 60 * 1000L)
    }

    /**
     * 今天的额度用完了：把这次挪到明天早上，**而不是把计划删掉**。
     * 删掉就等于「今天聊到晚，明天 TA 再也不会想起你」——额度是限制频率的，不是取消承诺的。
     */
    fun nextMorning(after: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = after
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, QUIET_END_MIN / 60)
            set(Calendar.MINUTE, QUIET_END_MIN % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis + kotlin.random.Random.nextLong(0, 40 * 60 * 1000L)
    }
}
