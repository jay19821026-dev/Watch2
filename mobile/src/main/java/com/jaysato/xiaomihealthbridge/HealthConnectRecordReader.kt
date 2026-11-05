package com.jaysato.xiaomihealthbridge

import android.os.RemoteException
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.CancellationException
import kotlin.reflect.KClass

internal suspend fun <T : Record> HealthConnectClient.readAllRecords(
    recordType: KClass<T>,
    timeRangeFilter: TimeRangeFilter,
    dataOriginFilter: Set<DataOrigin> = emptySet(),
    maxRecords: Int = MAX_HEALTH_CONNECT_RECORDS
): List<T> {
    require(maxRecords > 0)
    val records = collectHealthConnectRecordPages(
        maxPages = MAX_HEALTH_CONNECT_PAGES,
        maxRecords = maxRecords
    ) { pageToken ->
        val response = readRecords(
            ReadRecordsRequest(
                recordType = recordType,
                timeRangeFilter = timeRangeFilter,
                dataOriginFilter = dataOriginFilter,
                pageToken = pageToken
            )
        )
        HealthConnectRecordPage(response.records, response.pageToken)
    }
    if (!HealthConnectRecordIdentityPolicy.isValid(records.map { it.metadata.id })) {
        throw InvalidHealthConnectRecordIdentityException()
    }
    return records
}

internal suspend fun <T : Record> HealthConnectClient.foldHealthConnectRecordPages(
    recordType: KClass<T>,
    timeRangeFilter: TimeRangeFilter,
    dataOriginFilter: Set<DataOrigin> = emptySet(),
    maxRecords: Int = MAX_HEALTH_CONNECT_RECORDS,
    initialPageSize: Int = 1_000,
    foldPage: (List<T>) -> Unit
) {
    require(maxRecords > 0 && initialPageSize > 0)
    foldHealthConnectRecordPages(
        maxPages = MAX_HEALTH_CONNECT_PAGES,
        maxRecords = maxRecords,
        fetchPage = { pageToken ->
            val response = readRecords(
                ReadRecordsRequest(
                    recordType = recordType,
                    timeRangeFilter = timeRangeFilter,
                    dataOriginFilter = dataOriginFilter,
                    pageSize = minOf(initialPageSize, maxRecords),
                    pageToken = pageToken
                )
            )
            HealthConnectRecordPage(response.records, response.pageToken)
        },
        recordId = { it.metadata.id },
        foldPage = foldPage
    )
}

internal data class HealthConnectRecordPage<T>(val records: List<T>, val nextPageToken: String?)

internal suspend fun <T> collectHealthConnectRecordPages(
    maxPages: Int,
    maxRecords: Int,
    fetchPage: suspend (String?) -> HealthConnectRecordPage<T>
): List<T> {
    require(maxPages > 0 && maxRecords > 0)
    val records = ArrayList<T>()
    val tracker = HealthConnectPageTokenTracker()
    var pageToken: String? = null
    var pagesRead = 0
    do {
        currentCoroutineContext().ensureActive()
        if (pagesRead >= maxPages) throw HealthConnectReadLimitException("Health Connect page limit exceeded")
        val page = fetchPage(pageToken)
        pagesRead++
        if (page.records.size > maxRecords - records.size) {
            throw HealthConnectReadLimitException("Health Connect record limit exceeded")
        }
        records.addAll(page.records)
        pageToken = page.nextPageToken
        if (tracker.isRepeated(pageToken)) throw HealthConnectReadLimitException("Health Connect repeated page token")
        if (pagesRead >= maxPages && !pageToken.isNullOrEmpty()) {
            throw HealthConnectReadLimitException("Health Connect page limit exceeded")
        }
    } while (!pageToken.isNullOrEmpty())
    currentCoroutineContext().ensureActive()
    return records
}

internal suspend fun <T> foldHealthConnectRecordPages(
    maxPages: Int,
    maxRecords: Int,
    fetchPage: suspend (String?) -> HealthConnectRecordPage<T>,
    recordId: (T) -> String,
    foldPage: (List<T>) -> Unit
) {
    require(maxPages > 0 && maxRecords > 0)
    val tracker = HealthConnectPageTokenTracker()
    val seenIds = HashSet<String>()
    var pageToken: String? = null
    var pagesRead = 0
    var recordsRead = 0
    do {
        currentCoroutineContext().ensureActive()
        if (pagesRead >= maxPages) throw HealthConnectReadLimitException("Health Connect page limit exceeded")
        val page = fetchPage(pageToken)
        pagesRead++
        if (page.records.size > maxRecords - recordsRead) {
            throw HealthConnectReadLimitException("Health Connect record limit exceeded")
        }
        val ids = page.records.map(recordId)
        if (!HealthConnectRecordIdentityPolicy.isValid(ids) || ids.any { !seenIds.add(it) }) {
            throw InvalidHealthConnectRecordIdentityException()
        }
        recordsRead += page.records.size
        foldPage(page.records)
        pageToken = page.nextPageToken
        if (tracker.isRepeated(pageToken)) throw HealthConnectReadLimitException("Health Connect repeated page token")
        if (pagesRead >= maxPages && !pageToken.isNullOrEmpty()) {
            throw HealthConnectReadLimitException("Health Connect page limit exceeded")
        }
    } while (!pageToken.isNullOrEmpty())
    currentCoroutineContext().ensureActive()
}

internal class HealthConnectReadLimitException(message: String) : IllegalStateException(message)

internal object HealthConnectRecordIdentityPolicy {
    fun hasDuplicates(recordIds: List<String>): Boolean = recordIds.size != recordIds.distinct().size
    fun isValid(recordIds: List<String>): Boolean = recordIds.none(String::isBlank) && !hasDuplicates(recordIds)
}

internal class InvalidHealthConnectRecordIdentityException : IllegalStateException(
    "Health Connect returned blank or duplicate record IDs; refusing ambiguous aggregation"
)

internal object HealthConnectReadFailurePolicy {
    fun describe(label: String, failure: Throwable): String {
        if (failure is CancellationException) throw failure
        return when (failure) {
            is InvalidHealthConnectRecordIdentityException -> "$label：Health Connect 紀錄 ID 空白或重複，拒絕合併"
            is HealthConnectReadLimitException -> "$label：Health Connect 分頁／紀錄數超過安全上限，拒絕部分結果"
            is SecurityException -> "$label：Health Connect 權限遭拒或已撤銷，請重新確認授權"
            is java.io.IOException -> "$label：Health Connect 儲存讀取 I/O 失敗，稍後重試"
            is RemoteException -> "$label：Health Connect IPC 通訊失敗，稍後重試"
            is IllegalStateException -> "$label：Health Connect 服務目前不可用，確認服務狀態後重試"
            is IllegalArgumentException -> "$label：Health Connect 查詢參數無效，需檢查 App 查詢設定"
            else -> "$label：讀取失敗"
        }
    }
}

internal enum class HealthConnectMetricReadState {
    NO_RECORDS,
    PERMISSION_DENIED,
    READ_FAILED,
    UNAVAILABLE
}

internal object HealthConnectMetricReadStatePolicy {
    fun classify(
        label: String,
        missingPermissions: Set<String>,
        emptyReads: Set<String>,
        failedLabels: Set<String>
    ): HealthConnectMetricReadState = when {
        label in missingPermissions -> HealthConnectMetricReadState.PERMISSION_DENIED
        label in failedLabels -> HealthConnectMetricReadState.READ_FAILED
        label in emptyReads -> HealthConnectMetricReadState.NO_RECORDS
        else -> HealthConnectMetricReadState.UNAVAILABLE
    }

    fun label(state: HealthConnectMetricReadState): String = when (state) {
        HealthConnectMetricReadState.NO_RECORDS -> "本次查詢期間無紀錄"
        HealthConnectMetricReadState.PERMISSION_DENIED -> "尚未授權"
        HealthConnectMetricReadState.READ_FAILED -> "讀取失敗"
        HealthConnectMetricReadState.UNAVAILABLE -> "資料目前不可用"
    }
}

internal class HealthConnectPageTokenTracker {
    private val seenTokens = HashSet<String>()

    fun isRepeated(token: String?): Boolean = !token.isNullOrEmpty() && !seenTokens.add(token)
}

private const val MAX_HEALTH_CONNECT_RECORDS = 25_000
private const val MAX_HEALTH_CONNECT_PAGES = 256
