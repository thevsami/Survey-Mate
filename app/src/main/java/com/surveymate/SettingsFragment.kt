package com.surveymate

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.surveymate.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupLanguage()
        binding.previewOverlay.setOnClickListener {
            CallNumberTracker.remember("+94 77 000 0000", getString(R.string.app_name), Survey.SOURCE_MANUAL)
            OverlayController.show(requireContext(), Survey.SOURCE_MANUAL)
        }
    }

    override fun onResume() {
        super.onResume()
        renderPermissions()
    }

    private fun setupLanguage() {
        val options = listOf(getString(R.string.language_english), getString(R.string.language_sinhala))
        val spinner = binding.languageSpinner
        spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, options)
        spinner.setSelection(if (LocaleHelper.code(requireContext()) == "si") 1 else 0)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val code = if (position == 1) "si" else "en"
                if (LocaleHelper.code(requireContext()) != code) {
                    LocaleHelper.save(requireContext(), code)
                    requireActivity().recreate()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun renderPermissions() {
        val list = binding.permissionList
        list.removeAllViews()
        addRow(
            getString(R.string.perm_phone),
            getString(R.string.perm_phone_desc),
            hasPhone()
        ) { (activity as MainActivity).requestRuntimePermissions() }
        addRow(
            getString(R.string.perm_contacts),
            getString(R.string.perm_contacts_desc),
            granted(Manifest.permission.READ_CONTACTS)
        ) { (activity as MainActivity).requestRuntimePermissions() }
        addRow(
            getString(R.string.perm_notify),
            getString(R.string.perm_notify_desc),
            notificationsEnabled()
        ) { (activity as MainActivity).requestRuntimePermissions() }
        addRow(
            getString(R.string.perm_mic),
            getString(R.string.perm_mic_desc),
            granted(Manifest.permission.RECORD_AUDIO)
        ) { (activity as MainActivity).requestRuntimePermissions() }
        addRow(
            getString(R.string.perm_overlay),
            getString(R.string.perm_overlay_desc),
            Settings.canDrawOverlays(requireContext())
        ) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${requireContext().packageName}")))
        }
        addRow(
            getString(R.string.perm_listener),
            getString(R.string.perm_listener_desc),
            notificationAccessEnabled()
        ) { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        addRow(
            getString(R.string.perm_alarms),
            getString(R.string.perm_alarms_desc),
            canExactAlarms()
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
        }
        addRow(
            getString(R.string.perm_battery),
            getString(R.string.perm_battery_desc),
            batteryUnrestricted()
        ) { openBatterySettings() }
    }

    private fun addRow(title: String, subtitle: String, ok: Boolean, onClick: () -> Unit) {
        val row = layoutInflater.inflate(R.layout.item_permission, binding.permissionList, false)
        row.findViewById<TextView>(R.id.title).text = title
        row.findViewById<TextView>(R.id.subtitle).text = subtitle
        val button = row.findViewById<Button>(R.id.action)
        button.text = if (ok) getString(R.string.granted) else getString(R.string.grant)
        button.isEnabled = !ok
        button.setOnClickListener { onClick() }
        binding.permissionList.addView(row)
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED

    private fun hasPhone() = granted(Manifest.permission.READ_CALL_LOG) && granted(Manifest.permission.READ_PHONE_STATE)

    private fun notificationsEnabled(): Boolean {
        val post = if (Build.VERSION.SDK_INT >= 33) granted(Manifest.permission.POST_NOTIFICATIONS) else true
        return post && NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
    }

    private fun notificationAccessEnabled(): Boolean {
        val cn = ComponentName(requireContext(), WhatsAppNotificationListener::class.java)
        val flat = Settings.Secure.getString(requireContext().contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(":").any { ComponentName.unflattenFromString(it) == cn }
    }

    private fun canExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    private fun batteryUnrestricted(): Boolean {
        val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(requireContext().packageName)
    }

    private fun openBatterySettings() {
        val pkg = requireContext().packageName
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$pkg")
            }
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
