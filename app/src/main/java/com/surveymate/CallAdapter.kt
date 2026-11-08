package com.surveymate

import android.provider.CallLog
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.surveymate.databinding.ItemCallBinding
import java.text.DateFormat
import java.util.Date

class CallAdapter(
    private val onAdd: (RecentCall) -> Unit
) : RecyclerView.Adapter<CallAdapter.Holder>() {
    private val items = mutableListOf<RecentCall>()

    fun submit(data: List<RecentCall>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemCallBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(private val binding: ItemCallBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(call: RecentCall) {
            val ctx = binding.root.context
            binding.title.text = call.displayName
            binding.avatar.text = call.displayName.take(1).uppercase()
            val typeLabel = when (call.type) {
                CallLog.Calls.INCOMING_TYPE -> ctx.getString(R.string.call_in)
                CallLog.Calls.OUTGOING_TYPE -> ctx.getString(R.string.call_out)
                CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> ctx.getString(R.string.call_missed)
                else -> ctx.getString(R.string.recent_calls)
            }
            val whenText = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(call.dateMillis))
            val duration = if (call.durationSeconds >= 60) {
                ctx.getString(R.string.duration_min, (call.durationSeconds / 60).toInt())
            } else {
                ctx.getString(R.string.duration_sec, call.durationSeconds.toInt())
            }
            binding.subtitle.text = "$typeLabel · ${call.number} · $whenText · $duration"
            binding.add.setOnClickListener { onAdd(call) }
        }
    }
}
