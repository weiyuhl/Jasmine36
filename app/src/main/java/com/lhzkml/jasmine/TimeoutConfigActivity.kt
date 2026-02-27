package com.lhzkml.jasmine

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class TimeoutConfigActivity : AppCompatActivity() {

    private lateinit var etRequestTimeout: EditText
    private lateinit var etSocketTimeout: EditText
    private lateinit var etConnectTimeout: EditText
    private lateinit var switchResume: SwitchCompat
    private lateinit var layoutResumeParams: LinearLayout
    private lateinit var etMaxRetries: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_timeout_config)

        findViewById<View>(R.id.btnBack).setOnClickListener { save(); finish() }

        etRequestTimeout = findViewById(R.id.etRequestTimeout)
        etSocketTimeout = findViewById(R.id.etSocketTimeout)
        etConnectTimeout = findViewById(R.id.etConnectTimeout)
        switchResume = findViewById(R.id.switchResume)
        layoutResumeParams = findViewById(R.id.layoutResumeParams)
        etMaxRetries = findViewById(R.id.etMaxRetries)

        val config = AppConfig.configRepo()
        
        // 加载当前值
        val reqTimeout = config.getRequestTimeout()
        if (reqTimeout > 0) etRequestTimeout.setText(reqTimeout.toString())

        val socketTimeout = config.getSocketTimeout()
        if (socketTimeout > 0) etSocketTimeout.setText(socketTimeout.toString())

        val connectTimeout = config.getConnectTimeout()
        if (connectTimeout > 0) etConnectTimeout.setText(connectTimeout.toString())

        switchResume.isChecked = config.isStreamResumeEnabled()
        layoutResumeParams.visibility = if (switchResume.isChecked) View.VISIBLE else View.GONE
        switchResume.setOnCheckedChangeListener { _, isChecked ->
            config.setStreamResumeEnabled(isChecked)
            layoutResumeParams.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        etMaxRetries.setText(config.getStreamResumeMaxRetries().toString())
    }

    private fun save() {
        val reqTimeout = etRequestTimeout.text.toString().trim().toIntOrNull() ?: 0
        val socketTimeout = etSocketTimeout.text.toString().trim().toIntOrNull() ?: 0
        val connectTimeout = etConnectTimeout.text.toString().trim().toIntOrNull() ?: 0
        val maxRetries = (etMaxRetries.text.toString().trim().toIntOrNull() ?: 3).coerceIn(1, 10)

        val config = AppConfig.configRepo()
        config.setRequestTimeout(reqTimeout)
        config.setSocketTimeout(socketTimeout)
        config.setConnectTimeout(connectTimeout)
        config.setStreamResumeMaxRetries(maxRetries)
    }
}
