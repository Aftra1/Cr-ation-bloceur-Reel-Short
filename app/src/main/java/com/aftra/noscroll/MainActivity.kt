package com.aftra.noscroll

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
        // Le système peut reconnecter le service juste après le démarrage de l'app.
        binding.root.postDelayed({ if (!isFinishing) refresh() }, 1500)
    }

    private fun refresh() {
        // Coché dans les réglages mais pas lancé : typiquement MIUI qui a tué l'app
        // (balayage dans les apps récentes). Android ne le relance pas tout seul.
        binding.statusText.setText(
            when {
                !DebugStatus.isEnabledInSettings(this) -> R.string.status_disabled
                DebugStatus.connectedAt == 0L -> R.string.status_stopped
                else -> R.string.status_enabled
            }
        )

        val paused = Prefs.isPaused(this)
        binding.pauseButton.setText(if (paused) R.string.resume_button else R.string.pause_button)
        binding.pauseStatusText.text = if (paused) {
            getString(R.string.paused_until, DateFormat.getTimeFormat(this).format(Date(Prefs.pausedUntil(this))))
        } else ""

        binding.statsText.text = getString(R.string.stats, Prefs.blocksToday(this), Prefs.blocksTotal(this))
    }
}
