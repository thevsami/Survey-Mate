package com.surveymate

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.surveymate.databinding.ItemSurveyBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class SurveyAdapter(
    private val onEdit: (Survey) -> Unit,
    private val onDone: (Survey) -> Unit,
    private val onDelete: (Survey) -> Unit
) : RecyclerView.Adapter<SurveyAdapter.Holder>() {
    private val items = mutableListOf<Survey>()

    fun submit(data: List<Survey>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemSurveyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    inner class Holder(private val binding: ItemSurveyBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(survey: Survey) {
            val ctx = binding.root.context
            binding.name.text = survey.name.ifBlank { ctx.getString(R.string.client_name) }
            binding.phone.text = survey.phone.ifBlank { ctx.getString(R.string.phone_number) }
            val location = survey.location.ifBlank { "—" }
            binding.whenWhere.text = "${survey.date}  ${survey.time}  ·  $location"
            binding.notes.text = survey.notes
            binding.notes.visibility = if (survey.notes.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
            binding.status.text = statusLabel(survey)
            binding.done.visibility = if (survey.completed) android.view.View.GONE else android.view.View.VISIBLE
            binding.edit.setOnClickListener { onEdit(survey) }
            binding.done.setOnClickListener { onDone(survey) }
            binding.delete.setOnClickListener { onDelete(survey) }
        }

        private fun statusLabel(survey: Survey): String {
            val ctx = binding.root.context
            if (survey.completed) return ctx.getString(R.string.done_label)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            return try {
                val surveyDay = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(survey.date) ?: return survey.date
                val nowDay = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(today) ?: return survey.date
                val days = TimeUnit.MILLISECONDS.toDays(surveyDay.time - nowDay.time)
                when {
                    days < 0 -> ctx.getString(R.string.overdue)
                    days == 0L -> ctx.getString(R.string.today)
                    days == 1L -> ctx.getString(R.string.tomorrow)
                    else -> ctx.getString(R.string.in_days, days.toInt())
                }
            } catch (_: Exception) {
                survey.date
            }
        }
    }
}
