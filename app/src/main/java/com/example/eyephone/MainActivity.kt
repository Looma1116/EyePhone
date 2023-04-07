package com.example.eyephone

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.SurfaceView
import android.widget.*
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket
import java.nio.charset.Charset
import java.util.*


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        var settingBtn: ImageView = findViewById(R.id.home_settingBtn)
        var walkingModeBtn: ImageView = findViewById(R.id.home_walking_modeBtn)
        var readingModeBtn: ImageView = findViewById(R.id.home_reading_modeBtn)

        settingBtn.setOnClickListener {
            val settingIntent = Intent(this, SettingActivity::class.java)
            startActivity(settingIntent)
            finish()
        }
        walkingModeBtn.setOnClickListener {
            val walkingIntent = Intent(this, WalkingModeActivity::class.java)
            startActivity(walkingIntent)
            finish()
        }
        readingModeBtn.setOnClickListener {
            val readingIntent = Intent(this, ReadingModeActivity::class.java)
            startActivity(readingIntent)
            finish()
        }
    }
}
