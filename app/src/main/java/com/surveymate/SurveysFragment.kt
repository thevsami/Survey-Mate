package com.surveymate

import android.app.DatePickerDialog
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
import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat

class SurveysFragment : Fragment() {
    private var _binding: FragmentSurveysBinding? = null
    private val binding get() = _binding!!
    private lateinit var store: SurveyStore
    private lateinit var adapter: SurveyAdapter
    private var selectedDate: String? = null

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
        binding.chooseDateButton.setOnClickListener { chooseDate() }
        binding.seeAllButton.setOnClickListener {
            selectedDate = null
            refresh()
        }
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
        val incomplete = all.filterNot { it.completed }
        val visibleSurveys = selectedDate?.let { date ->
            incomplete.filter { it.date == date }
        } ?: incomplete
        binding.upcomingCount.text = getString(R.string.upcoming_count, incomplete.size)
        binding.chooseDateButton.text = selectedDate?.let {
            getString(R.string.selected_filter_date, it)
        } ?: getString(R.string.select_date)
        adapter.submit(visibleSurveys)
        val emptyTitle = when {
            visibleSurveys.isNotEmpty() -> null
            all.isEmpty() -> R.string.empty_surveys_title
            selectedDate != null -> R.string.empty_date_surveys_title
            else -> R.string.empty_incomplete_title
        }
        val emptyBody = when {
            visibleSurveys.isNotEmpty() -> null
            all.isEmpty() -> R.string.empty_surveys_body
            selectedDate != null -> R.string.empty_date_surveys_body
            else -> R.string.empty_incomplete_body
        }
        if (emptyTitle != null && emptyBody != null) {
            binding.emptyTitle.setText(emptyTitle)
            binding.emptyBody.setText(emptyBody)
            binding.emptyState.visibility = View.VISIBLE
        } else {
            binding.emptyState.visibility = View.GONE
        }
    }

    private fun chooseDate() {
        val calendar = Calendar.getInstance()
        selectedDate?.let { date ->
            runCatching {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)
            }.getOrNull()?.let(calendar::setTime)
        }
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                selectedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                refresh()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun openEditor(survey: Survey) {
        startActivity(Intent(requireContext(), AddSurveyActivity::class.java).apply {
            putExtra(AddSurveyActivity.EXTRA_ID, survey.id)
        })
    }

    private fun markDone(survey: Survey) {
        val updated = survey.copy(completed = true)
        store.update(updated)
        ReminderScheduler.refresh(requireContext())
        refresh()
    }

    private fun confirmDelete(survey: Survey) {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.confirm_delete)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                store.delete(survey.id)
                ReminderScheduler.refresh(requireContext())
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
