package cn.eciot.ble_demo

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class DeviceActivity : AppCompatActivity() {

    var scrollView: ScrollView? = null
    var receiveDataTextView: TextView? = null
    var scrollCheckBox: CheckBox? = null
    var hexRevCheckBox: CheckBox? = null
    var hexSendCheckBox: CheckBox? = null
    var sendDataEditText: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)

        scrollView = findViewById(R.id.sv_receive)
        receiveDataTextView = findViewById(R.id.tv_receive_data)
        scrollCheckBox = findViewById(R.id.cb_scroll)
        hexRevCheckBox = findViewById(R.id.cb_hex_rev)
        hexSendCheckBox = findViewById(R.id.cb_hex_send)
        sendDataEditText = findViewById(R.id.et_send)
        findViewById<ImageView>(R.id.iv_back).setOnClickListener {
            ECBLE.offBLEConnectionStateChange()
            ECBLE.closeBLEConnection()
            finish()
        }
        findViewById<Button>(R.id.bt_send).setOnClickListener {
            if (sendDataEditText?.text.toString().isEmpty()) {
                return@setOnClickListener
            }
            if (hexSendCheckBox?.isChecked == true) {
                //send hex
                if (!Pattern.compile("^[0-9a-fA-F]+$").matcher(sendDataEditText?.text.toString())
                        .matches()
                ) {
                    showAlert("提示", "格式错误，只能是0-9、a-f、A-F") {}
                    return@setOnClickListener
                }
                if (sendDataEditText?.text.toString().length % 2 == 1) {
                    showAlert("提示", "长度错误，长度只能是双数") {}
                    return@setOnClickListener
                }
                ECBLE.easySendData(sendDataEditText?.text.toString(), true)
            } else {
                //send string
                ECBLE.easySendData(sendDataEditText?.text.toString().replace("\n","\r\n"), false)
            }
        }
        findViewById<Button>(R.id.bt_clear).setOnClickListener {
            receiveDataTextView?.text = ""
        }

        ECBLE.onBLEConnectionStateChange {
            showToast("设备断开")
        }
        ECBLE.onBLECharacteristicValueChange { hex, string ->
            runOnUiThread {
                val timeStr =
                    SimpleDateFormat("[HH:mm:ss,SSS]:").format(Date(System.currentTimeMillis()))
                val nowStr = receiveDataTextView?.text.toString()
                if (hexRevCheckBox?.isChecked == true) {
                    receiveDataTextView?.text = nowStr + timeStr + hex + "\r\n"
                } else {
                    receiveDataTextView?.text = nowStr + timeStr + string + "\r\n"
                }
                if (scrollCheckBox?.isChecked == true) {
                    scrollView?.post { scrollView?.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
    }

    fun showToast(text: String) {
        runOnUiThread {
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
        }
    }

    fun showAlert(title: String, content: String, callback: () -> Unit) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(content)
                .setPositiveButton("确定",
                    DialogInterface.OnClickListener { _, _ -> callback() })
                .setCancelable(false)
                .create().show()
        }
    }

}
