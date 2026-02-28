package com.lhzkml.jasmine

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.lhzkml.jasmine.core.config.ProviderConfig

class ProviderConfigActivity : AppCompatActivity() {

    private lateinit var provider: ProviderConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_provider_config)

        val providerId = intent.getStringExtra("provider_id") ?: run { finish(); return }
        val registry = AppConfig.providerRegistry()
        provider = registry.getProvider(providerId) ?: run { finish(); return }

        findViewById<TextView>(R.id.tvTitle).text = provider.name
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)

        val configFragment = ProviderConfigFragment.newInstance(providerId)
        val modelListFragment = ModelListFragment.newInstance(providerId)

        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> configFragment
                    1 -> modelListFragment
                    else -> configFragment
                }
            }
        }

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "配置"
                1 -> "模型列表"
                else -> ""
            }
        }.attach()

        // 支持直接跳转到指定 tab（如从聊天界面跳转到模型列表）
        val initialTab = intent.getIntExtra("tab", 0)
        if (initialTab in 0..1) {
            viewPager.currentItem = initialTab
        }
    }
}
