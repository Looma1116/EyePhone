package com.example.eyephone

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class GuardianSaveActivity : AppCompatActivity() {
    lateinit var pref: SharedPreferences
    lateinit var editor: SharedPreferences.Editor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guardian_save)


        //초기화
        pref = getPreferences(Context.MODE_PRIVATE)
        editor = pref.edit()

        var edit :EditText = findViewById(R.id.guardian_save_editText)
        var btn : Button = findViewById(R.id.guardian_save_saveBtn)

        var inputData = pref.getString("InputData","")

        edit.setText(inputData.toString())

        btn.setOnClickListener(View.OnClickListener {
            // (key : InputData, value : EditText에 입력한 데이터)
            editor.putString("InputData",edit.text.toString())
            editor.apply();
        })

        var backBtn: ImageButton =findViewById(R.id.guardian_save_backBtn)

        backBtn.setOnClickListener {
            val intent = Intent(this, SettingActivity::class.java)
            startActivity(intent)
        }

    }


}
