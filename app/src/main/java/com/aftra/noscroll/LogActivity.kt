package com.aftra.noscroll

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aftra.noscroll.databinding.ActivityLogBinding

class LogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.refreshButton.setOnClickListener { refresh() }
        binding.copyButton.setOnClickListener {
            val text = binding.headerText.text.toString() + "\n" + binding.logText.text
            getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("NoScroll journal", text))
            Toast.makeText(this, R.string.log_copied, Toast.LENGTH_SHORT).show()
        }
        binding.clearButton.setOnClickListener {
            DebugLog.clear(this)
            binding.logText.text = ""
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        binding.headerText.text = DebugStatus.header()
        binding.logText.text = DebugLog.read(this, MAX_SHOWN_CHARS).ifEmpty { getString(R.string.log_empty) }
        binding.scroll.post { binding.scroll.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    private companion object {
        // Limite l'affichage et la copie (le presse-papiers n'aime pas les gros textes).
        const val MAX_SHOWN_CHARS = 150_000
    }
}
