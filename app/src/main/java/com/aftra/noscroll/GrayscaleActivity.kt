package com.aftra.noscroll

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aftra.noscroll.databinding.ActivityGrayscaleBinding

class GrayscaleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGrayscaleBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGrayscaleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.adbCommand.text = Grayscale.adbCommand(this)
        binding.copyCommandButton.setOnClickListener {
            getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("adb", Grayscale.adbCommand(this)))
            Toast.makeText(this, R.string.gray_copied, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.permissionStatus.setText(
            if (Grayscale.hasPermission(this)) R.string.gray_perm_granted else R.string.gray_perm_missing
        )
    }
}
