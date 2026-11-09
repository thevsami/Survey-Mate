package com.surveymate

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.surveymate.databinding.FragmentCallsBinding

class CallsFragment : Fragment() {
    private var _binding: FragmentCallsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: CallAdapter
    private var showingWhatsAppCalls = false
    private var selectedWhatsAppFilter = R.id.allCallsFilter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCallsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = createAdapter()
        binding.callList.layoutManager = LinearLayoutManager(requireContext())
        binding.callList.adapter = adapter
        binding.callTypeTabs.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                showingWhatsAppCalls = checkedId == R.id.whatsAppCallsTab
                refreshCalls()
            }
        }
        binding.whatsAppFilterButtons.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedWhatsAppFilter = checkedId
                refreshCalls()
            }
        }
        binding.whatsAppAccessAction.setOnClickListener {
            WhatsAppCallAccess.request(requireContext())
        }
    }

    private fun createAdapter() = CallAdapter(
        onAdd = { call ->
            startActivity(Intent(requireContext(), AddSurveyActivity::class.java).apply {
                putExtra(AddSurveyActivity.EXTRA_PHONE, call.number)
                putExtra(AddSurveyActivity.EXTRA_NAME, call.name)
                putExtra(
                    AddSurveyActivity.EXTRA_SOURCE,
                    if (call.source == Survey.SOURCE_WHATSAPP) Survey.SOURCE_WHATSAPP else Survey.SOURCE_RECENT
                )
            })
        },
        onCall = { call ->
            withPhoneNumber(call) { number ->
                openExternalApp(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number.trim(), null)))
            }
        },
        onWhatsApp = { call -> withPhoneNumber(call, requireInternational = true, ::openWhatsAppChat) }
    )

    private fun withPhoneNumber(
        call: RecentCall,
        requireInternational: Boolean = false,
        action: (String) -> Unit
    ) {
        if (call.number.isNotBlank()) {
            action(call.number)
            return
        }

        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            hint = getString(R.string.phone_number_hint_international)
            setTextColor(requireContext().getColor(R.color.text_primary))
            setHintTextColor(requireContext().getColor(R.color.text_secondary))
            val horizontalPadding = (16 * resources.displayMetrics.density).toInt()
            val verticalPadding = (8 * resources.displayMetrics.density).toInt()
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.enter_whatsapp_phone_title)
            .setMessage(R.string.enter_whatsapp_phone_message)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.continue_label) { _, _ ->
                val enteredNumber = input.text.toString().trim()
                val digits = enteredNumber.filter(Char::isDigit)
                val valid = digits.length in 7..15 &&
                    (!requireInternational || enteredNumber.startsWith("+") || enteredNumber.startsWith("00"))
                if (!valid) {
                    Toast.makeText(requireContext(), R.string.invalid_phone_number, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (call.source == Survey.SOURCE_WHATSAPP) {
                    WhatsAppCallStore.updateNumber(requireContext(), call.sourceId, enteredNumber)
                }
                action(enteredNumber)
                refreshCalls()
            }
            .show()
    }

    private fun openWhatsAppChat(phone: String) {
        val number = phone.filter(Char::isDigit).let {
            if (phone.trim().startsWith("00")) it.drop(2) else it
        }
        if (number.length !in 7..15) {
            Toast.makeText(requireContext(), R.string.whatsapp_number_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("whatsapp://send").buildUpon()
                .appendQueryParameter("phone", number)
                .build()
        )
        for (packageName in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            try {
                startActivity(intent.setPackage(packageName))
                return
            } catch (_: android.content.ActivityNotFoundException) {
                continue
            }
        }
        Toast.makeText(requireContext(), R.string.no_app_for_action, Toast.LENGTH_SHORT).show()
    }

    private fun openExternalApp(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.no_app_for_action, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshCalls()
    }

    private fun refreshCalls() {
        val context = requireContext()
        val allWhatsAppCalls = if (showingWhatsAppCalls) {
            WhatsAppCallStore.recent(context)
        } else {
            emptyList()
        }
        val filteredWhatsAppCalls = when (selectedWhatsAppFilter) {
            R.id.incomingCallsFilter -> allWhatsAppCalls.filter(RecentCall::isIncoming)
            R.id.outgoingCallsFilter -> allWhatsAppCalls.filter(RecentCall::isOutgoing)
            R.id.missedCallsFilter -> allWhatsAppCalls.filter(RecentCall::isMissed)
            else -> allWhatsAppCalls
        }
        val calls = if (showingWhatsAppCalls) {
            filteredWhatsAppCalls.take(40)
        } else {
            CallLogRepository.phoneRecent(context)
        }
        adapter.submit(calls)
        binding.screenTitle.setText(
            if (showingWhatsAppCalls) R.string.whatsapp_history_title else R.string.recent_calls
        )
        binding.screenBody.setText(
            if (showingWhatsAppCalls) R.string.whatsapp_history_notice else R.string.calls_tab_body
        )
        binding.whatsAppFilters.visibility = if (showingWhatsAppCalls) View.VISIBLE else View.GONE
        binding.whatsAppNotice.visibility = if (showingWhatsAppCalls) View.VISIBLE else View.GONE
        binding.whatsAppAccessAction.visibility =
            if (showingWhatsAppCalls && !WhatsAppCallAccess.isEnabled(context)) View.VISIBLE else View.GONE
        binding.emptyState.visibility = if (calls.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyTitle.setText(
            if (showingWhatsAppCalls) R.string.no_whatsapp_calls_title else R.string.no_phone_calls_title
        )
        binding.emptyBody.text = when {
            !showingWhatsAppCalls -> getString(R.string.no_phone_calls)
            allWhatsAppCalls.isEmpty() -> getString(R.string.no_whatsapp_calls)
            else -> getString(
                R.string.no_whatsapp_calls_for_filter,
                getString(
                    when (selectedWhatsAppFilter) {
                        R.id.incomingCallsFilter -> R.string.filter_incoming
                        R.id.outgoingCallsFilter -> R.string.filter_outgoing
                        R.id.missedCallsFilter -> R.string.filter_missed
                        else -> R.string.filter_all
                    }
                )
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
