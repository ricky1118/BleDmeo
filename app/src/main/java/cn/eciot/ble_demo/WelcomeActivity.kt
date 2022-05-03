package cn.eciot.ble_demo

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import java.lang.NullPointerException

class WelcomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)
        val looper = Looper.myLooper()
        Handler(looper!!).postDelayed({
            startActivity(Intent().setClass(this,MainActivity().javaClass))
        }, 3500)
    }
}
