package com.lhzkml.jasmine

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.lhzkml.jasmine.core.agent.tools.ShellPolicy

class ShellPolicyActivity : AppCompatActivity() {

    private lateinit var rgPolicy: RadioGroup
    private lateinit var layoutBlacklist: LinearLayout
    private lateinit var layoutWhitelist: LinearLayout
    private lateinit var etBlacklist: EditText
    private lateinit var etWhitelist: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shell_policy)

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }

        rgPolicy = findViewById(R.id.rgPolicy)
        layoutBlacklist = findViewById(R.id.layoutBlacklist)
        layoutWhitelist = findViewById(R.id.layoutWhitelist)
        etBlacklist = findViewById(R.id.etBlacklist)
        etWhitelist = findViewById(R.id.etWhitelist)

        // 加载当前配置
        val policy = ProviderManager.getShellPolicy(this)
        when (policy) {
            ShellPolicy.MANUAL -> rgPolicy.check(R.id.rbManual)
            ShellPolicy.BLACKLIST -> rgPolicy.check(R.id.rbBlacklist)
            ShellPolicy.WHITELIST -> rgPolicy.check(R.id.rbWhitelist)
        }
        updateListVisibility(policy)

        etBlacklist.setText(ProviderManager.getShellBlacklist(this).joinToString("\n"))
        etWhitelist.setText(ProviderManager.getShellWhitelist(this).joinToString("\n"))

        rgPolicy.setOnCheckedChangeListener { _, checkedId ->
            val newPolicy = when (checkedId) {
                R.id.rbBlacklist -> ShellPolicy.BLACKLIST
                R.id.rbWhitelist -> ShellPolicy.WHITELIST
                else -> ShellPolicy.MANUAL
            }
            ProviderManager.setShellPolicy(this, newPolicy)
            updateListVisibility(newPolicy)
        }
    }

    override fun onPause() {
        super.onPause()
        // 保存列表内容
        val blacklist = etBlacklist.text.toString().lines().filter { it.isNotBlank() }
        val whitelist = etWhitelist.text.toString().lines().filter { it.isNotBlank() }
        ProviderManager.setShellBlacklist(this, blacklist)
        ProviderManager.setShellWhitelist(this, whitelist)
    }

    private fun updateListVisibility(policy: ShellPolicy) {
        layoutBlacklist.visibility = if (policy == ShellPolicy.BLACKLIST) View.VISIBLE else View.GONE
        layoutWhitelist.visibility = if (policy == ShellPolicy.WHITELIST) View.VISIBLE else View.GONE
    }
}
