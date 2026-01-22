package com.example.aiclassroomcoach

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.aiclassroomcoach.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.util.Log


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: ClassroomCoachViewModel by viewModels()

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.connect()
        } else {
            binding.statusText.text = "Status: Microphone permission denied"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        viewModel.connect()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.pushToTalkButton.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {Log.d("MainActivity", "Button pressed - calling startPushToTalk")
                    viewModel.startPushToTalk()}
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {Log.d("MainActivity", "Button released - calling stopPushToTalk")
                    viewModel.stopPushToTalk()}
            }
            true
        }

        binding.alwaysOnSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleAlwaysOn(isChecked)
        }

        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                binding.statusText.text = "Status: ${state.status}"
                if (state.lastResponseText.isNotBlank()) {
                    binding.demoPromptBody.text = state.lastResponseText
                }
            }
        }

        ensurePermissions()
    }

    private fun ensurePermissions() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            viewModel.connect()
        } else {
            requestPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.disconnect()
    }
}
