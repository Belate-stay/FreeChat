package com.freechat.data

import android.util.Log
import java.io.File

/**
 * 本地存储的**唯一写入口**。
 *
 * 在此之前，对话数组、每个对话的消息、记忆、每对话设置这四类文件是**谁想写就自己写**的：
 * 保存函数散在 `ChatViewModel` 和 `MemoryManager` 里，全是主线程上的 `File.writeText`，
 * 没有锁、没有原子性。之所以一直没出大事，只靠两条纪律撑着 ——
 * 「同一个对话同一时刻只有一个 VM 在写」（`ChatViewModel.live()` 复用实例）和
 * 「主动任务在用户发言时被取消」。而 `ProactiveService` 冷启动时会**自己 new 一个 ViewModel**，
 * 纪律一旦破，两个线程就会同时读-改-写同一个文件，后写的把先写的整个盖掉。
 *
 * 云端同步会把这件事从「偶发」变成「必需」：同步的每一轮都是
 * **读本地 → 和云端合并 → 写回本地**，这三步之间只要前台在写同一个文件，
 * 就会丢一边。所以先把地基立起来，同步只是它的第一个正式用户。
 *
 * 这一层的职责刻意只有三件事，**不含任何业务逻辑**（业务仍然留在原来的地方）：
 *
 * 1. **一把进程级静态锁**。必须静态：`@Synchronized` 锁的是 `this`，
 *    而这些类在进程里有多份实例（VM 的、服务的、接收器的），各锁各的等于没锁。
 * 2. **原子写**：先写 `.tmp` 再 rename。`writeText` 会把目标截断成 0 字节，
 *    并发读到的那个瞬间就是半截/空内容 —— Json 解析失败 → 返回 `emptyList()` →
 *    调用方把这个「空」当成事实，下一次写入再把它固化下来（`ProactiveStore` 的注释里
 *    记着同款事故：所有待触发的主动计划一起消失）。rename 是原子的，读方要么看到旧的、要么看到新的。
 * 3. **写监听**：写完回调一次「哪个逻辑键变了」。这是同步引擎知道自己该推什么的唯一来源
 *    —— 对应网页端 `db.ts` 里那个 `setWriteListener` 接缝，
 *    有了它，同步引擎**不需要**去 hook 十几个业务方法，也不会漏掉任何一条写入路径。
 *
 * 读也走锁：读-改-写的「读」如果读到一个正在被改写的文件，同样会拿到半截数据。
 */
object LocalStore {

    private const val TAG = "LocalStore"

    /** 进程级锁。见类注释第 1 条，必须是静态的。 */
    private val LOCK = Any()

    /** 写监听：参数是逻辑键（见 [keyOf]）。只允许做「记一笔」这种不阻塞的事，见 [writeText]。 */
    @Volatile
    private var onWrite: ((String) -> Unit)? = null

    /**
     * 抑制计数。同步引擎把云端内容落回本地时，要包一层 [suspendApply] ——
     * 那会儿的写入是「同步自己写的」，不能当成「用户改的」再推回云端（会变成来回弹的循环）。
     *
     * 之所以用计数而不是布尔：apply 允许嵌套（比如先落对话、再落消息）。
     */
    @Volatile
    private var suspendDepth = 0

    // ============================================================
    //  文件位置
    // ============================================================

    private const val CONVERSATIONS_FILE = "freechat_conversations.json"
    private const val PER_CONV_FILE = "freechat_perconv.json"
    private const val MSGS_PREFIX = "freechat_msgs_"
    private const val MEMORY_PREFIX = "freechat_memory_"

    /**
     * 角色头像的**指纹**（同步用，见 `sync/Wire` 的「角色头像」一节）。
     *
     * 记的是「这条对话的头像，我们最后一次见到的是哪一张」。有它才分得清两件事：
     * 「这边本来就没有头像」和「这边本来有、被用户删了」—— 后者要告诉云端把头像清掉，
     * 前者一个字都不该说（不然一台还没下载到头像的设备会把云端那张抹了）。
     */
    private const val AVATAR_HASH_PREFIX = "freechat_avatar_"

    // ---- 逻辑键名。定义只有这一处：同步那边也照这些常量比对，不各写一份字面量 ----

    const val CONVERSATIONS_KEY = "freechat:conversations"
    const val PER_CONV_KEY = "freechat:perconv"
    fun msgsKey(convId: String) = "freechat:msgs:$convId"
    fun memoryKey(convId: String) = "freechat:memory:$convId"

    private lateinit var dir: File

    /** 由 `FreeChatApp.onCreate` 调一次。之前各处自己 `File(filesDir, ...)`，两处来源早晚会分叉。 */
    fun init(filesDir: File) {
        dir = filesDir
    }

    /** 本 App 的私有目录。**只给真正需要落一个"不是同步对象"的文件的地方用**（如角色头像），业务数据一律走上面那几个 File */
    fun filesDir(): File = dir

    fun conversationsFile(): File = File(dir, CONVERSATIONS_FILE)
    fun perConvFile(): File = File(dir, PER_CONV_FILE)
    fun messagesFile(convId: String): File = File(dir, "$MSGS_PREFIX$convId.json")
    fun memoryFile(convId: String): File = File(dir, "$MEMORY_PREFIX$convId.json")

    /** 头像指纹文件。**故意不带 .json 后缀**：它不是同步对象，`keyOf` 认不出来就会原样返回文件名，监听方忽略掉它 */
    fun avatarHashFile(convId: String): File = File(dir, "$AVATAR_HASH_PREFIX$convId")

    /** 所有消息文件里的对话 id。换账号/首次登录要「把本地全部推一遍」时用来枚举。 */
    fun messageConvIds(): List<String> =
        dir.listFiles { f -> f.name.startsWith(MSGS_PREFIX) && f.name.endsWith(".json") }
            ?.map { it.name.removePrefix(MSGS_PREFIX).removeSuffix(".json") }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    /** 所有有记忆文件的对话 id */
    fun memoryConvIds(): List<String> =
        dir.listFiles { f -> f.name.startsWith(MEMORY_PREFIX) && f.name.endsWith(".json") }
            ?.map { it.name.removePrefix(MEMORY_PREFIX).removeSuffix(".json") }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    // ============================================================
    //  逻辑键 —— 同步引擎按它认「变的是哪一个同步对象」
    // ============================================================

    /**
     * 文件名 → 逻辑键。规则跟网页端 `db.KEYS` 保持一致，方便两端对照维护。
     *
     * 认不出来的文件返回文件名本身（监听方会忽略）—— 不返回 null 是为了让日志里
     * 还能看出是哪个文件触发的。
     */
    fun keyOf(file: File): String {
        val name = file.name
        return when {
            name == CONVERSATIONS_FILE -> CONVERSATIONS_KEY
            name == PER_CONV_FILE -> PER_CONV_KEY
            name.startsWith(MSGS_PREFIX) && name.endsWith(".json") ->
                msgsKey(name.removePrefix(MSGS_PREFIX).removeSuffix(".json"))
            name.startsWith(MEMORY_PREFIX) && name.endsWith(".json") ->
                memoryKey(name.removePrefix(MEMORY_PREFIX).removeSuffix(".json"))
            else -> name
        }
    }

    // ============================================================
    //  锁
    // ============================================================

    /** 包住一次「读-改-写」。同步引擎的合并必须整段包在里面，否则中途会被前台写进去。 */
    fun <T> locked(block: () -> T): T = synchronized(LOCK) { block() }

    /**
     * 包住「把云端内容落回本地」的一段写入。
     *
     * 持锁 + 静音监听，两件事都要：
     * - 持锁：保证没有本地写入插进合并和落盘之间（否则合并的结果是基于旧的本地内容算的，
     *   会把用户刚做的改动盖掉）。
     * - 静音：这段写入不是用户改的，标脏会立刻触发一轮把它推回去，来回弹。
     *
     * 顺序上有一点很关键：静音是在**拿到锁之后**才打开的。反过来的话，
     * 一个在锁外等着的本地写入会被误判成「同步自己写的」而不标脏，
     * 那笔改动就再也不会被推上云端了。
     */
    fun <T> suspendApply(block: () -> T): T = synchronized(LOCK) {
        suspendDepth++
        try {
            block()
        } finally {
            suspendDepth--
        }
    }

    // ============================================================
    //  读 / 写
    // ============================================================

    fun setWriteListener(fn: ((String) -> Unit)?) {
        onWrite = fn
    }

    /** 读文本。文件不存在返回 null（**不是**空串 —— 「没有这个文件」和「文件是空的」要分开）。 */
    fun readText(file: File): String? = synchronized(LOCK) {
        try {
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            Log.e(TAG, "read failed: ${file.name}", e)
            null
        }
    }

    /**
     * 原子写。
     *
     * 监听回调在**锁外**触发：它只应该做「往待推集合里记一笔」这种不阻塞的事，
     * 但万一哪天有人在里面回调进 LocalStore 或去抢别的锁，持锁触发就是一个死锁。
     * 语义上没有损失 —— 脏标记是幂等的，谁先谁后都一样。
     */
    fun writeText(file: File, text: String) {
        val key: String?
        synchronized(LOCK) {
            atomicWrite(file, text)
            key = if (suspendDepth > 0) null else keyOf(file)
        }
        key?.let { onWrite?.invoke(it) }
    }

    /** 删文件（删对话 / 删记忆）。删除也要通知 —— 同步靠它知道该给云端立墓碑。 */
    fun deleteFile(file: File) {
        val key: String?
        synchronized(LOCK) {
            try {
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                Log.e(TAG, "delete failed: ${file.name}", e)
            }
            key = if (suspendDepth > 0) null else keyOf(file)
        }
        key?.let { onWrite?.invoke(it) }
    }

    /**
     * 先写临时文件再改名。`renameTo` 失败就退回直接写 ——
     * 某些文件系统（个别厂商的 sdcardfs/FUSE）不支持同目录 rename，
     * 那时原子性拿不到，但至少内容写进去了，比整个失败强。
     */
    private fun atomicWrite(file: File, text: String) {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "write failed: ${file.name}", e)
        }
    }
}
