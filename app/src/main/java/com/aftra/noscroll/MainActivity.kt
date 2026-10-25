package com.aftra.noscroll

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import androidx.appcompat.app.AppCompatActivity
import com.aftra.noscroll.databinding.ActivityMainBinding
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.openSettingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.switchReels.isChecked = Prefs.blockReels(this)
        binding.switchShorts.isChecked = Prefs.blockShorts(this)
        binding.switchReels.setOnCheckedChangeListener { _, checked -> Prefs.setBlockReels(this, checked) }
        binding.switchShorts.setOnCheckedChangeListener { _, checked -> Prefs.setBlockShorts(this, checked) }

        binding.switchDebug.isChecked = Prefs.debugMode(this)
        binding.switchDebug.setOnCheckedChangeListener { _, checked -> Prefs.setDebugMode(this, checked) }
        binding.openLogButton.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }

        binding.pauseButton.setOnClickListener {
            if (Prefs.isPaused(this)) Prefs.resume(this) else Prefs.pause(this)
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        binding.statusText.setText(
            if (isServiceEnabled()) R.string.status_enabled else R.string.status_disabled
        )

        val paused = Prefs.isPaused(this)
        binding.pauseButton.setText(if (paused) R.string.resume_button else R.string.pause_button)
        binding.pauseStatusText.text = if (paused) {
            getString(R.string.paused_until, DateFormat.getTimeFormat(this).format(Date(Prefs.pausedUntil(this))))
        } else ""

        binding.statsText.text = getString(R.string.stats, Prefs.blocksToday(this), Prefs.blocksTotal(this))
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(this, NoScrollService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}
