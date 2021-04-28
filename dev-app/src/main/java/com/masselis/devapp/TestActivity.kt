package com.masselis.devapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.vincentmasselis.rxuikotlin.postForUI
import kotlinx.android.synthetic.main.activity_test.*

class TestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)
    }

    fun setMessage(message: String) {
        postForUI { test_activity_test_message.text = message }
    }
}
