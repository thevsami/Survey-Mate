package com.surveymate

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.surveymate.databinding.ActivityAddSurveyBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddSurveyActivity : BaseActivity() {
    private lateinit var binding: ActivityAddSurveyBinding
    private lateinit var store: SurveyStore
    private var surveyId: Long = 0L
    private var source: String = Survey.SOURCE_MANUAL

    private val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!text.isNullOrBlank()) {
            val current = binding.notes.text?.toString().orEmpty()
            binding.notes.setText(if (current.isBlank()) text else "$current $text")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddSurveyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        store = SurveyStore(this)

        surveyId = intent.getLongExtra(EXTRA_ID, 0L)
        source = intent.getStringExtra(EXTRA_SOURCE) ?: Survey.SOURCE_MANUAL
        val existing = if (surveyId != 0L) store.get(surveyId) else null

        binding.toolbar.title = if (existing != null) getString(R.string.edit_survey) else getString(R.string.new_survey)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.sourceLabel.text = when (existing?.source ?: source) {
            Survey.SOURCE_CALL -> getString(R.string.source_call)
            Survey.SOURCE_WHATSAPP -> getString(R.string.source_whatsapp)
            Survey.SOURCE_RECENT -> getString(R.string.source_recent)
            else -> getString(R.string.source_manual)
        }

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (existing != null) {
            binding.name.setText(existing.name)
            binding.phone.setText(existing.phone)
            binding.date.setText(existing.date)
            binding.time.setText(existing.time)
            binding.location.setText(existing.location)
            binding.notes.setText(existing.notes)
            source = existing.source
        } else {
            var phone = cleanLabel(intent.getStringExtra(EXTRA_PHONE).orEmpty())
            var name = cleanLabel(intent.getStringExtra(EXTRA_NAME).orEmpty())
            if (intent.getBooleanExtra(EXTRA_FROM_LIVE_CALL, false) && phone.isBlank()) {
                val resolved = CallNumberTracker.resolve(this)
                phone = resolved.first
                if (name.isBlank()) name = resolved.second
            }
            binding.name.setText(name)
            binding.phone.setText(phone)
            binding.date.setText(today)
        }

        binding.date.setOnClickListener { pickDate() }
        binding.time.setOnClickListener { pickTime() }
        binding.voice.setOnClickListener { startVoice() }
        binding.save.setOnClickListener { save() }
    }

    private fun cleanLabel(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed == getString(R.string.unknown_number)) return ""
        return CallNumberTracker.sanitizeNumber(trimmed).ifBlank {
            CallNumberTracker.sanitizeName(trimmed)
        }
    }

    private fun pickDate() {
        val calendar = Calendar.getInstance()
        val existing = binding.date.text?.toString().orEmpty()
        if (existing.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            val parts = existing.split("-")
            calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        }
        val dialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                binding.date.setText(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        dialog.datePicker.minDate = System.currentTimeMillis() - 1000
        dialog.show()
    }

    private fun pickTime() {
        val calendar = Calendar.getInstance()
        TimePickerDialog(
            this,
            { _, hour, minute ->
                binding.time.setText(String.format(Locale.US, "%02d:%02d", hour, minute))
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun startVoice() {
        val language = if (LocaleHelper.code(this) == "si") "si-LK" else "en-US"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt))
        }
        try {
            voiceLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.voice_prompt), Toast.LENGTH_SHORT).show()
        }
    }

    private fun save() {
        val name = binding.name.text?.toString()?.trim().orEmpty()
        val date = binding.date.text?.toString()?.trim().orEmpty()
        val time = binding.time.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            Toast.makeText(this, getString(R.string.name_required), Toast.LENGTH_SHORT).show()
            return
        }
        if (date.isBlank() || time.isBlank()) {
            Toast.makeText(this, getString(R.string.date_time_required), Toast.LENGTH_SHORT).show()
            return
        }
        val survey = Survey(
            id = if (surveyId != 0L) surveyId else System.currentTimeMillis(),
            name = name,
            phone = binding.phone.text?.toString()?.trim().orEmpty(),
            date = date,
            time = time,
            location = binding.location.text?.toString()?.trim().orEmpty(),
            notes = binding.notes.text?.toString()?.trim().orEmpty(),
            completed = store.get(surveyId)?.completed ?: false,
            source = source
        )
        if (surveyId != 0L) store.update(survey) else store.add(survey)
        ReminderScheduler.schedule(this, survey)
        Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        if (isFinishing && CallNumberTracker.inCall) {
            OverlayController.show(this, CallNumberTracker.source)
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PHONE = "extra_phone_number"
        const val EXTRA_NAME = "extra_client_name"
        const val EXTRA_SOURCE = "extra_source"
        const val EXTRA_ID = "extra_survey_id"
        const val EXTRA_FROM_LIVE_CALL = "extra_from_live_call"
    }
}
