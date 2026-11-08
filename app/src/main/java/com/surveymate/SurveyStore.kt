package com.surveymate

import android.content.Context
import org.json.JSONArray

class SurveyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("surveys", Context.MODE_PRIVATE)

    fun all(): List<Survey> {
        val raw = prefs.getString("items", "[]") ?: "[]"
        val array = JSONArray(raw)
        val result = mutableListOf<Survey>()
        for (i in 0 until array.length()) {
            result.add(Survey.fromJson(array.getJSONObject(i)))
        }
        return result.sortedWith(
            compareBy<Survey> { it.completed }
                .thenBy { it.date }
                .thenBy { it.time }
        )
    }

    fun get(id: Long): Survey? = all().find { it.id == id }

    fun add(survey: Survey) {
        save(all() + survey)
    }

    fun update(survey: Survey) {
        save(all().map { if (it.id == survey.id) survey else it })
    }

    fun delete(id: Long) {
        save(all().filterNot { it.id == id })
    }

    private fun save(items: List<Survey>) {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        prefs.edit().putString("items", array.toString()).apply()
    }
}
