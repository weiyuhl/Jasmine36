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

        // 加载当前值
        val reqTimeout = ProviderManager.getRequestTimeout(this)
        if (reqTimeout > 0) etRequestTimeout.setText(reqTimeout.toString())

        val socketTimeout = ProviderManager.getSocketTimeout(this)
        if (socketTimeout > 0) etSocketTimeout.setText(socketTimeout.toString())

        val connectTimeout = ProviderManager.getConnectTimeout(this)
        if (connectTimeout > 0) etConnectTimeout.setText(connectTimeout.toString())

        switchResume.isChecked = ProviderManager.isStreamResumeEnabled(this)
        layoutResumeParams.visibility = if (switchResume.isChecked) View.VISIBLE else View.GONE
        switchResume.setOnCheckedChangeListener { _, isChecked ->
            ProviderManager.setStreamResumeEnabled(this, isChecked)
            layoutResumeParams.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        etMaxRetries.setText(ProviderManager.getStreamResumeMaxRetries(this).toString())
    }

    private fun save() {
        val reqTimeout = etRequestTimeout.text.toString().trim().toIntOrNull() ?: 0
        val socketTimeout = etSocketTimeout.text.toString().trim().toIntOrNull() ?: 0
        val connectTimeout = etConnectTimeout.text.toString().trim().toIntOrNull() ?: 0
        val maxRetries = (etMaxRetries.text.toString().trim().toIntOrNull() ?: 3).coerceIn(1, 10)

        ProviderManager.setRequestTimeout(this, reqTimeout)
        ProviderManager.setSocketTimeout(this, socketTimeout)
        ProviderManager.setConnectTimeout(this, connectTimeout)
        ProviderManager.setStreamResumeMaxRetries(this, maxRetries)
    }
}
