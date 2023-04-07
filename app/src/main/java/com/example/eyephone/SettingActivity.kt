package com.example.eyephone

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class SettingActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting)

        var backBtn: ImageView =findViewById(R.id.setting_backBtn)
        var guardianSaveBtn: ImageView = findViewById(R.id.setting_guardian_registerBtn)

        guardianSaveBtn.setOnClickListener {
            val guardianIntent = Intent(this, GuardianSaveActivity::class.java)
            startActivity(guardianIntent)
        }

        backBtn.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

    }


}
