package com.surveymate

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.surveymate.databinding.FragmentSurveysBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SurveysFragment : Fragment() {
    private var _binding: FragmentSurveysBinding? = null
    private val binding get() = _binding!!
    private lateinit var store: SurveyStore
    private lateinit var adapter: SurveyAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSurveysBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        store = SurveyStore(requireContext())
        adapter = SurveyAdapter(
            onEdit = { openEditor(it) },
            onDone = { markDone(it) },
            onDelete = { confirmDelete(it) }
        )
        binding.surveyList.layoutManager = LinearLayoutManager(requireContext())
        binding.surveyList.adapter = adapter
        binding.addButton.setOnClickListener {
            startActivity(Intent(requireContext(), AddSurveyActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val all = store.all()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val todayCount = all.count { it.date == today && !it.completed }
        val upcoming = all.count { !it.completed && it.date >= today }
        binding.todayCount.text = getString(R.string.today_survey_count, todayCount)
        binding.upcomingCount.text = getString(R.string.upcoming_count, upcoming)
        adapter.submit(all)
        binding.emptyState.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openEditor(survey: Survey) {
        startActivity(Intent(requireContext(), AddSurveyActivity::class.java).apply {
            putExtra(AddSurveyActivity.EXTRA_ID, survey.id)
        })
    }

    private fun markDone(survey: Survey) {
        val updated = survey.copy(completed = true)
        store.update(updated)
        ReminderScheduler.cancel(requireContext(), survey.id)
        refresh()
    }

    private fun confirmDelete(survey: Survey) {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.confirm_delete)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                store.delete(survey.id)
                ReminderScheduler.cancel(requireContext(), survey.id)
                Toast.makeText(requireContext(), R.string.deleted, Toast.LENGTH_SHORT).show()
                refresh()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
