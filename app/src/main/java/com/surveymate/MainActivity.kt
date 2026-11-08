package com.surveymate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.surveymate.databinding.ActivityMainBinding

class MainActivity : BaseActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NotificationHelper.ensureChannels(this)

        if (savedInstanceState == null) {
            showFragment(SurveysFragment())
            handleQuickIntent(intent)
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_surveys -> showFragment(SurveysFragment())
                R.id.nav_calls -> showFragment(CallsFragment())
                R.id.nav_settings -> showFragment(SettingsFragment())
            }
            true
        }

        requestRuntimePermissions()
        CallMonitor.start(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            binding.bottomNav.post {
                if (!isFinishing) binding.bottomNav.selectedItemId = R.id.nav_settings
            }
        }
    }

    override fun onResume() {
        super.onResume()
        CallMonitor.start(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleQuickIntent(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        CallMonitor.start(this)
    }

    fun requestRuntimePermissions() {
        val needed = mutableListOf<String>()
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            addIfMissing(needed, Manifest.permission.READ_CALL_LOG)
            addIfMissing(needed, Manifest.permission.READ_PHONE_STATE)
        }
        addIfMissing(needed, Manifest.permission.READ_CONTACTS)
        addIfMissing(needed, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) addIfMissing(needed, Manifest.permission.POST_NOTIFICATIONS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 10)
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${packageName}")
            )
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun addIfMissing(target: MutableList<String>, permission: String) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            target.add(permission)
        }
    }

    private fun handleQuickIntent(intent: Intent?) {
        val phone = intent?.getStringExtra(AddSurveyActivity.EXTRA_PHONE)
        val quick = intent?.getBooleanExtra("extra_quick_add", false) == true
        if (quick || !phone.isNullOrBlank() && intent.action == "com.surveymate.NEW_CALL_SURVEY") {
            startActivity(Intent(this, AddSurveyActivity::class.java).apply {
                putExtra(AddSurveyActivity.EXTRA_PHONE, phone)
                putExtra(AddSurveyActivity.EXTRA_NAME, intent.getStringExtra(AddSurveyActivity.EXTRA_NAME))
                putExtra(AddSurveyActivity.EXTRA_SOURCE, intent.getStringExtra(AddSurveyActivity.EXTRA_SOURCE) ?: Survey.SOURCE_CALL)
            })
            intent.replaceExtras(Bundle())
        }
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
