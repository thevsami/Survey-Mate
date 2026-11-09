package com.surveymate

import android.provider.CallLog
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.surveymate.databinding.ItemCallBinding
import java.text.DateFormat
import java.util.Date

class CallAdapter(
    private val onAdd: (RecentCall) -> Unit,
    private val onCall: (RecentCall) -> Unit,
    private val onWhatsApp: (RecentCall) -> Unit
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
            val number = call.number.ifBlank { ctx.getString(R.string.unknown_number) }
            binding.title.text = call.name.ifBlank { number }
            binding.phone.text = if (call.number.isBlank() && call.source == Survey.SOURCE_WHATSAPP) {
                ctx.getString(R.string.whatsapp_phone_unavailable)
            } else {
                number
            }
            binding.avatar.text = (call.name.ifBlank { number }).take(1).uppercase()
            val typeLabel = when (call.type) {
                CallLog.Calls.INCOMING_TYPE -> ctx.getString(R.string.call_in)
                CallLog.Calls.OUTGOING_TYPE -> ctx.getString(R.string.call_out)
                CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> ctx.getString(R.string.call_missed)
                else -> ctx.getString(R.string.recent_calls)
            }
            val callLabel = if (call.source == Survey.SOURCE_WHATSAPP) {
                "${ctx.getString(R.string.call_whatsapp)} · $typeLabel"
            } else typeLabel
            val typeColor = when (call.type) {
                CallLog.Calls.INCOMING_TYPE -> R.color.success
                CallLog.Calls.OUTGOING_TYPE -> R.color.secondary
                CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> R.color.missed
                else -> R.color.text_secondary
            }
            val whenText = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(call.dateMillis))
            val duration = if (call.durationSeconds >= 60) {
                ctx.getString(R.string.duration_min, (call.durationSeconds / 60).toInt())
            } else {
                ctx.getString(R.string.duration_sec, call.durationSeconds.toInt())
            }
            val subtitle = SpannableString(callLabel)
            subtitle.setSpan(
                ForegroundColorSpan(ctx.getColor(typeColor)),
                0,
                callLabel.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            binding.subtitle.text = subtitle
            binding.callDetails.text = "$whenText · $duration"
            binding.add.setOnClickListener { onAdd(call) }
            val hasNumber = call.number.isNotBlank()
            val canRequestNumber = call.source == Survey.SOURCE_WHATSAPP
            binding.callAction.isEnabled = hasNumber || canRequestNumber
            binding.whatsAppAction.isEnabled = call.number.any(Char::isDigit) || canRequestNumber
            binding.callAction.setOnClickListener { onCall(call) }
            binding.whatsAppAction.setOnClickListener { onWhatsApp(call) }
        }
    }
}
