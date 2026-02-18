package com.lhzkml.jasmine

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class PlannerConfigActivity : AppCompatActivity() {

    private lateinit var etMaxIterations: EditText
    private lateinit var switchCritic: SwitchCompat
    private lateinit var tvSummary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_planner_config)

        findViewById<android.view.View>(R.id.btnBack).setOnClickListener { save(); finish() }
        etMaxIterations = findViewById(R.id.etMaxIterations)
        switchCritic = findViewById(R.id.switchCritic)
        tvSummary = findViewById(R.id.tvSummary)

        etMaxIterations.setText(ProviderManager.getPlannerMaxIterations(this).toString())
        switchCritic.isChecked = ProviderManager.isPlannerCriticEnabled(this)

        switchCritic.setOnCheckedChangeListener { _, isChecked ->
            ProviderManager.setPlannerCriticEnabled(this, isChecked)
            refreshSummary()
        }

        etMaxIterations.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val v = s.toString().trim().toIntOrNull() ?: return
                ProviderManager.setPlannerMaxIterations(this@PlannerConfigActivity, v.coerceIn(1, 20))
                refreshSummary()
            }
        })

        refreshSummary()
    }

    private fun save() {
        val maxIter = (etMaxIterations.text.toString().trim().toIntOrNull() ?: 1).coerceIn(1, 20)
        ProviderManager.setPlannerMaxIterations(this, maxIter)
        ProviderManager.setPlannerCriticEnabled(this, switchCritic.isChecked)
    }

    private fun refreshSummary() {
        val maxIter = ProviderManager.getPlannerMaxIterations(this)
        val critic = if (switchCritic.isChecked) "Critic 评估" else "无 Critic"
        tvSummary.text = "迭代 $maxIter 次 · $critic"
    }
}
