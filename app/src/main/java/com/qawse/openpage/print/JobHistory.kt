package com.qawse.openpage.print

import android.content.Context
import com.qawse.openpage.data.PrintJobRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Recent-jobs list, persisted as a compact JSON document in preferences.
 */
class JobHistory(context: Context) {

    private val prefs = context.getSharedPreferences("openpage_jobs", Context.MODE_PRIVATE)

    private val _jobs = MutableStateFlow<List<PrintJobRecord>>(emptyList())
    val jobs: StateFlow<List<PrintJobRecord>> = _jobs.asStateFlow()

    init { load() }

    fun add(record: PrintJobRecord) {
        val next = (listOf(record) + _jobs.value).take(30)
        _jobs.value = next
        persist(next)
    }

    fun clear() {
        _jobs.value = emptyList()
        persist(emptyList())
    }

    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            val out = mutableListOf<PrintJobRecord>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(PrintJobRecord(
                    fileName = o.optString("f"),
                    printerName = o.optString("p"),
                    pages = o.optInt("n"),
                    copies = o.optInt("c"),
                    timestamp = o.optLong("t"),
                    ok = o.optBoolean("ok", true),
                    problem = if (o.has("x")) o.optString("x") else null,
                    sourceUri = if (o.has("u")) o.optString("u") else null,
                ))
            }
            _jobs.value = out
        }
    }

    private fun persist(jobs: List<PrintJobRecord>) {
        val arr = JSONArray()
        jobs.forEach {
            arr.put(JSONObject().apply {
                put("f", it.fileName)
                put("p", it.printerName)
                put("n", it.pages)
                put("c", it.copies)
                put("t", it.timestamp)
                put("ok", it.ok)
                if (it.problem != null) put("x", it.problem)
                if (it.sourceUri != null) put("u", it.sourceUri)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object { private const val KEY = "history" }
}
