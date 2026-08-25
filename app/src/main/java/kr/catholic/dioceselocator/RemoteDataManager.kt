package kr.catholic.dioceselocator

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 교구장 정보는 원격 JSON으로 갱신하고, 교구 경계 규칙은 앱에 내장합니다.
 * boundaryVersion이 CURRENT_BOUNDARY_VERSION보다 크면 앱 업데이트가 필요합니다.
 */
object RemoteDataManager {
    const val CURRENT_BOUNDARY_VERSION = 1

    // 기본값은 이 프로젝트의 공개 GitHub Raw 데이터입니다. 필요하면 Gradle 속성으로 덮어쓸 수 있습니다.
    val REMOTE_DATA_URL: String = BuildConfig.REMOTE_DATA_URL

    private const val PREFS = "remote_diocese_data"
    private const val KEY_JSON = "cached_json"

    data class RefreshResult(
        val appliedOrdinaries: Int = 0,
        val remoteBoundaryVersion: Int = CURRENT_BOUNDARY_VERSION,
        val dataUpdatedAt: String? = null,
        val skipped: Boolean = false,
        val error: String? = null
    ) {
        val boundaryUpdateRequired: Boolean
            get() = remoteBoundaryVersion > CURRENT_BOUNDARY_VERSION
    }

    fun applyCached(context: Context): RefreshResult? {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_JSON, null) ?: return null
        return runCatching { applyJson(json) }.getOrNull()
    }

    fun refreshAsync(context: Context, onComplete: (RefreshResult) -> Unit) {
        if (REMOTE_DATA_URL.isBlank()) {
            onComplete(RefreshResult(skipped = true))
            return
        }
        Thread {
            val result = runCatching {
                val conn = (URL(REMOTE_DATA_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    if (conn.responseCode !in 200..299) {
                        error("HTTP ${conn.responseCode}")
                    }
                    val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val parsed = applyJson(body)
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putString(KEY_JSON, body).apply()
                    parsed
                } finally {
                    conn.disconnect()
                }
            }.getOrElse { RefreshResult(error = it.localizedMessage ?: "원격 데이터 확인 실패") }
            context.runOnMainThread { onComplete(result) }
        }.start()
    }

    private fun applyJson(raw: String): RefreshResult {
        val root = JSONObject(raw)
        val boundaryVersion = root.optInt("boundaryVersion", CURRENT_BOUNDARY_VERSION)
        val dataUpdatedAt = root.optString("dataUpdatedAt").takeIf { it.isNotBlank() }
        val ordinaries = root.optJSONArray("ordinaries")
        var applied = 0

        if (ordinaries != null) {
            for (i in 0 until ordinaries.length()) {
                val item = ordinaries.optJSONObject(i) ?: continue
                val name = item.optString("diocese")
                val ordinary = item.optString("ordinary")
                if (name.isBlank() || ordinary.isBlank()) continue
                val ok = DioceseRepository.updateOrdinary(
                    dioceseName = name,
                    ordinary = ordinary,
                    title = item.optString("title", "주교"),
                    statusLabel = item.optString("statusLabel", "교구장"),
                    rememberOrdinary = item.optBoolean("rememberOrdinary", true)
                )
                if (ok) applied++
            }
        }
        return RefreshResult(applied, boundaryVersion, dataUpdatedAt)
    }
}

private fun Context.runOnMainThread(block: () -> Unit) {
    android.os.Handler(mainLooper).post(block)
}
