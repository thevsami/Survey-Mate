package com.surveymate

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.surveymate.databinding.ItemRecordingBinding
import java.text.DateFormat
import java.util.Date

class RecordingAdapter(
    private val onPlay: (CallRecording) -> Unit,
    private val onDelete: (CallRecording) -> Unit
) : RecyclerView.Adapter<RecordingAdapter.Holder>() {
    private val items = mutableListOf<CallRecording>()
    private var playingId: Long? = null

    fun submit(data: List<CallRecording>, playing: Long? = playingId) {
        items.clear()
        items.addAll(data)
        playingId = playing
        notifyDataSetChanged()
    }

    fun setPlaying(id: Long?) {
        playingId = id
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemRecordingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(private val binding: ItemRecordingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CallRecording) {
            val ctx = binding.root.context
            val number = item.phone.ifBlank { ctx.getString(R.string.unknown_number) }
            binding.title.text = item.name.ifBlank { ctx.getString(R.string.call_recordings) }
            binding.phone.text = number
            val whenText = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(item.startedAt))
            val seconds = (item.durationMs / 1000).coerceAtLeast(1)
            val duration = if (seconds >= 60) {
                ctx.getString(R.string.duration_min, (seconds / 60).toInt())
            } else {
                ctx.getString(R.string.duration_sec, seconds.toInt())
            }
            binding.subtitle.text = "$whenText · $duration"
            binding.play.setImageResource(
                if (playingId == item.id) R.drawable.ic_pause else R.drawable.ic_play
            )
            binding.root.setOnClickListener { onPlay(item) }
            binding.play.setOnClickListener { onPlay(item) }
            binding.delete.setOnClickListener { onDelete(item) }
        }
    }
}
