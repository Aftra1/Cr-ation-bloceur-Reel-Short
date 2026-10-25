package com.aftra.noscroll

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.aftra.noscroll.databinding.ActivityMainBinding

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
    }

    override fun onResume() {
        super.onResume()
        binding.statusText.setText(
            if (isServiceEnabled()) R.string.status_enabled else R.string.status_disabled
        )
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(this, NoScrollService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}
