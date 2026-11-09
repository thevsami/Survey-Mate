package com.surveymate

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.surveymate.databinding.FragmentRecordingsBinding

class RecordingsFragment : Fragment() {
    private var _binding: FragmentRecordingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: RecordingAdapter
    private lateinit var store: CallRecordingStore
    private lateinit var player: RecordingPlayer
    private var selected: CallRecording? = null
    private var seeking = false
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (_binding != null) refresh()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecordingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        store = CallRecordingStore(requireContext())
        player = RecordingPlayer(requireContext())
        adapter = RecordingAdapter(
            onPlay = { loadAndPlay(it) },
            onDelete = { confirmDelete(it) }
        )
        binding.recordingList.layoutManager = LinearLayoutManager(requireContext())
        binding.recordingList.adapter = adapter
        binding.playerPlay.setOnClickListener { togglePlayback() }
        binding.playerSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.playerPosition.text = formatClock(progress.toLong())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                seeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seeking = false
                player.seekTo(seekBar?.progress ?: 0)
            }
        })
        player.onTick = { position, duration ->
            if (_binding != null && !seeking) {
                binding.playerSeek.max = duration.coerceAtLeast(1)
                binding.playerSeek.progress = position
                binding.playerPosition.text = formatClock(position.toLong())
                binding.playerDuration.text = formatClock(duration.toLong())
            }
        }
        player.onState = { playing ->
            if (_binding != null) {
                binding.playerPlay.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
                adapter.setPlaying(if (playing) selected?.id else null)
            }
        }
        player.onComplete = {
            if (_binding != null) {
                binding.playerPlay.setImageResource(R.drawable.ic_play)
                adapter.setPlaying(null)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        store.addListener(listener)
        refresh()
    }

    override fun onPause() {
        store.removeListener(listener)
        player.stop()
        super.onPause()
    }

    override fun onDestroyView() {
        store.removeListener(listener)
        player.release()
        super.onDestroyView()
        _binding = null
    }

    private fun refresh() {
        val recordings = store.all()
        adapter.submit(recordings, if (player.playing) selected?.id else null)
        binding.emptyState.visibility = if (recordings.isEmpty()) View.VISIBLE else View.GONE
        if (selected != null && recordings.none { it.id == selected?.id }) {
            selected = null
            player.stop()
            binding.playerCard.visibility = View.GONE
        }
    }

    private fun loadAndPlay(item: CallRecording) {
        if (selected?.id == item.id && player.playing) {
            player.pause()
            return
        }
        if (selected?.id == item.id && player.currentId == item.id) {
            player.resume()
            return
        }
        val file = store.fileFor(item)
        selected = item
        showPlayer(item)
        if (!player.play(item.id, file)) {
            if (player.lastPlayEmpty) {
                Toast.makeText(requireContext(), R.string.recording_silent, Toast.LENGTH_LONG).show()
            }
            binding.playerPlay.setImageResource(R.drawable.ic_play)
            adapter.setPlaying(null)
            refresh()
        }
    }

    private fun togglePlayback() {
        val item = selected ?: return
        if (player.currentId == item.id) {
            if (player.playing) player.pause() else player.resume()
        } else {
            loadAndPlay(item)
        }
    }

    private fun showPlayer(item: CallRecording) {
        binding.playerCard.visibility = View.VISIBLE
        binding.playerTitle.text = item.name.ifBlank { getString(R.string.call_recordings) }
        binding.playerPhone.text = item.phone.ifBlank { getString(R.string.unknown_number) }
        binding.playerSeek.max = item.durationMs.toInt().coerceAtLeast(1)
        binding.playerSeek.progress = 0
        binding.playerPosition.text = formatClock(0)
        binding.playerDuration.text = formatClock(item.durationMs)
        binding.playerPlay.setImageResource(R.drawable.ic_pause)
        adapter.setPlaying(item.id)
    }

    private fun confirmDelete(item: CallRecording) {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.confirm_delete_recording)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                if (selected?.id == item.id) {
                    player.stop()
                    selected = null
                    binding.playerCard.visibility = View.GONE
                }
                store.delete(item.id)
                refresh()
            }
            .show()
    }

    companion object {
        fun formatClock(ms: Long): String {
            val total = (ms / 1000).coerceAtLeast(0)
            val minutes = total / 60
            val seconds = total % 60
            return "%d:%02d".format(minutes, seconds)
        }
    }
}
