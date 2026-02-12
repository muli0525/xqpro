package com.chesspro.app.core.data

/**
 * 棋谱记录
 */
data class ChessRecord(
    val id: Int,
    val title: String,
    val author: String,
    val date: String,
    val category: String,
    val moves: List<String> = emptyList(),
    val fen: String = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"
)

/**
 * 棋谱分类
 */
enum class RecordCategory(val label: String) {
    ANCIENT("古代棋谱"),
    MASTER("名家著作"),
    FULL_GAME("象棋全局"),
    PERSONAL("个人赛"),
    LAYOUT("布局")
}

/**
 * 棋谱库 - 内置示例棋谱
 */
object ChessRecordRepository {

    fun getCategories(): List<RecordCategory> = RecordCategory.entries.toList()

    fun getRecordsByCategory(category: RecordCategory): List<ChessRecord> {
        return when (category) {
            RecordCategory.ANCIENT -> ancientRecords
            RecordCategory.MASTER -> masterRecords
            RecordCategory.FULL_GAME -> fullGameRecords
            RecordCategory.PERSONAL -> personalRecords
            RecordCategory.LAYOUT -> layoutRecords
        }
    }

    fun getAllRecords(): List<ChessRecord> {
        return ancientRecords + masterRecords + fullGameRecords + personalRecords + layoutRecords
    }

    private val ancientRecords = listOf(
        ChessRecord(1, "崇本堂梅花谱", "不详", "2024-08-10", "古代棋谱"),
        ChessRecord(2, "反梅花谱", "薛丙巴吉人", "2024-08-10", "古代棋谱"),
        ChessRecord(3, "会珍阁绿蓉桥", "不详", "2024-09-16", "古代棋谱"),
        ChessRecord(4, "金鹏十八变", "不详", "2024-08-10", "古代棋谱"),
        ChessRecord(5, "橘中秘", "朱晋桢", "2024-08-10", "古代棋谱"),
        ChessRecord(6, "梅花变法谱", "不详", "2024-08-10", "古代棋谱"),
        ChessRecord(7, "梅花谱", "王再越", "2024-08-10", "古代棋谱"),
        ChessRecord(8, "梅花泉", "不详", "2024-08-10", "古代棋谱"),
        ChessRecord(9, "善庆堂重订梅花变", "不详", "2024-08-10", "古代棋谱"),
    )

    private val masterRecords = listOf(
        ChessRecord(10, "特级大师杨官璘巧手妙着", "杨官璘", "2024-09-08", "名家著作"),
        ChessRecord(11, "荣华自战解说谱", "不详", "2024-09-08", "名家著作"),
        ChessRecord(12, "荣华妙局精萃", "不详", "2024-09-17", "名家著作"),
        ChessRecord(13, "许银川百局精选", "许银川", "2024-09-08", "名家著作"),
        ChessRecord(14, "王天一佳局赏析2", "不详", "2024-09-12", "名家著作"),
    )

    private val fullGameRecords = listOf(
        ChessRecord(15, "李来群实战百局", "不详", "2024-08-10", "象棋全局"),
        ChessRecord(16, "王天一2015对局", "不详", "2024-08-10", "象棋全局"),
        ChessRecord(17, "郑惟桐2015对局", "不详", "2024-09-08", "象棋全局"),
    )

    private val personalRecords = listOf(
        ChessRecord(18, "我的对局记录", "本地", "2024-10-01", "个人赛"),
    )

    private val layoutRecords = listOf(
        ChessRecord(19, "中炮对屏风马", "不详", "2024-08-10", "布局"),
        ChessRecord(20, "仙人指路对局", "不详", "2024-08-10", "布局"),
        ChessRecord(21, "飞相局", "不详", "2024-09-01", "布局"),
    )
}
