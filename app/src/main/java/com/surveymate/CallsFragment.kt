package com.surveymate

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.surveymate.databinding.FragmentCallsBinding

class CallsFragment : Fragment() {
    private var _binding: FragmentCallsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: CallAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCallsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = CallAdapter { call ->
            startActivity(Intent(requireContext(), AddSurveyActivity::class.java).apply {
                putExtra(AddSurveyActivity.EXTRA_PHONE, call.number)
                putExtra(AddSurveyActivity.EXTRA_NAME, call.name)
                putExtra(AddSurveyActivity.EXTRA_SOURCE, Survey.SOURCE_RECENT)
            })
        }
        binding.callList.layoutManager = LinearLayoutManager(requireContext())
        binding.callList.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        val calls = CallLogRepository.recent(requireContext())
        adapter.submit(calls)
        binding.emptyState.visibility = if (calls.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
