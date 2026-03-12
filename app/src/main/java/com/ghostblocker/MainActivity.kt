package com.ghostblocker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val OVERLAY_PERMISSION_REQ = 1234

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        createNotificationChannel()

        val seekBar = findViewById<SeekBar>(R.id.seekBar)
        val tvPercent = findViewById<TextView>(R.id.tvPercent)
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val savedPercent = prefs.getInt("block_percent", 39)
        seekBar.progress = savedPercent
        tvPercent.text = "차단 영역: 상단 ${savedPercent}%"

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvPercent.text = "차단 영역: 상단 ${progress}%"
                prefs.edit().putInt("block_percent", progress).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        btnStart.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "'다른 앱 위에 표시' 권한을 허용해주세요", Toast.LENGTH_LONG).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, OVERLAY_PERMISSION_REQ)
            } else {
                startBlockerService()
            }
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, TouchBlockerService::class.java))
            Toast.makeText(this, "차단 중지됨", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startBlockerService() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val percent = prefs.getInt("block_percent", 39)
        val intent = Intent(this, TouchBlockerService::class.java)
        intent.putExtra("block_percent", percent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "상단 ${percent}% 고스트터치 차단 시작!", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQ && Settings.canDrawOverlays(this)) {
            startBlockerService()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "blocker_channel", "고스트터치 차단 서비스", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
