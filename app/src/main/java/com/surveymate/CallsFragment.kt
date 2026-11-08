package com.surveymate

import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.surveymate.databinding.FragmentCallsBinding

class CallsFragment : Fragment() {
    private var _binding: FragmentCallsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: CallAdapter
    private lateinit var recordingAdapter: RecordingAdapter
    private lateinit var recordingStore: CallRecordingStore
    private var player: MediaPlayer? = null
    private var playingId: Long? = null
    private val recordingListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (_binding != null) refreshRecordings()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCallsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recordingStore = CallRecordingStore(requireContext())
        adapter = CallAdapter { call ->
            startActivity(Intent(requireContext(), AddSurveyActivity::class.java).apply {
                putExtra(AddSurveyActivity.EXTRA_PHONE, call.number)
                putExtra(AddSurveyActivity.EXTRA_NAME, call.name)
                putExtra(AddSurveyActivity.EXTRA_SOURCE, Survey.SOURCE_RECENT)
            })
        }
        recordingAdapter = RecordingAdapter(
            onPlay = { togglePlayback(it) },
            onDelete = { confirmDelete(it) }
        )
        binding.callList.layoutManager = LinearLayoutManager(requireContext())
        binding.callList.adapter = adapter
        binding.recordingList.layoutManager = LinearLayoutManager(requireContext())
        binding.recordingList.adapter = recordingAdapter
    }

    override fun onResume() {
        super.onResume()
        recordingStore.addListener(recordingListener)
        val calls = CallLogRepository.recent(requireContext())
        adapter.submit(calls)
        refreshRecordings()
        binding.emptyState.visibility = if (calls.isEmpty() && recordingStore.all().isEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    override fun onPause() {
        recordingStore.removeListener(recordingListener)
        super.onPause()
        stopPlayback()
    }

    override fun onDestroyView() {
        recordingStore.removeListener(recordingListener)
        stopPlayback()
        super.onDestroyView()
        _binding = null
    }

    private fun refreshRecordings() {
        val recordings = recordingStore.all()
        val visible = if (recordings.isEmpty()) View.GONE else View.VISIBLE
        binding.recordingsTitle.visibility = visible
        binding.recordingList.visibility = visible
        recordingAdapter.submit(recordings, playingId)
    }

    private fun togglePlayback(item: CallRecording) {
        if (playingId == item.id) {
            stopPlayback()
            return
        }
        stopPlayback()
        val file = recordingStore.fileFor(item)
        if (!file.exists()) {
            refreshRecordings()
            return
        }
        try {
            val next = MediaPlayer()
            next.setDataSource(file.absolutePath)
            next.prepare()
            next.setOnCompletionListener { stopPlayback() }
            next.start()
            player = next
            playingId = item.id
            recordingAdapter.setPlaying(item.id)
        } catch (_: Exception) {
            stopPlayback()
        }
    }

    private fun stopPlayback() {
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        playingId = null
        if (_binding != null) recordingAdapter.setPlaying(null)
    }

    private fun confirmDelete(item: CallRecording) {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.confirm_delete_recording)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                if (playingId == item.id) stopPlayback()
                recordingStore.delete(item.id)
                refreshRecordings()
            }
            .show()
    }
}
