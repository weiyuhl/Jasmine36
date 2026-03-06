package com.lhzkml.jasmine

import android.app.Activity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.lhzkml.jasmine.core.agent.runtime.ToolRegistryBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 将 ToolRegistryBuilder 所需的各类用户交互对话框注册到 builder 上。
 * 从 MainActivity 中提取，避免 Activity 臃肿。
 */
object DialogHandlers {

    fun register(activity: Activity, builder: ToolRegistryBuilder) {
        builder.shellConfirmationHandler = { command, purpose, _ ->
            val deferred = CompletableDeferred<Boolean>()
            withContext(Dispatchers.Main) {
                AlertDialog.Builder(activity)
                    .setTitle("执行命令确认")
                    .setMessage("目的：$purpose\n\n命令：$command\n\n是否允许执行？")
                    .setPositiveButton("允许") { _, _ -> deferred.complete(true) }
                    .setNegativeButton("拒绝") { _, _ -> deferred.complete(false) }
                    .setCancelable(false)
                    .show()
            }
            deferred.await()
        }

        builder.askUserHandler = { question ->
            val deferred = CompletableDeferred<String>()
            withContext(Dispatchers.Main) {
                val input = EditText(activity).apply {
                    hint = "请输入回复"
                    setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 16))
                }
                AlertDialog.Builder(activity)
                    .setTitle("AI 询问")
                    .setMessage(question)
                    .setView(input)
                    .setPositiveButton("发送") { _, _ ->
                        val answer = input.text.toString().trim()
                        deferred.complete(answer.ifEmpty { "(无回复)" })
                    }
                    .setNegativeButton("取消") { _, _ ->
                        deferred.complete("(用户取消)")
                    }
                    .setCancelable(false)
                    .show()
            }
            deferred.await()
        }

        builder.singleSelectHandler = { question, options ->
            val deferred = CompletableDeferred<String>()
            withContext(Dispatchers.Main) {
                var selectedIndex = -1
                AlertDialog.Builder(activity)
                    .setTitle(question)
                    .setSingleChoiceItems(options.toTypedArray(), -1) { _, which ->
                        selectedIndex = which
                    }
                    .setPositiveButton("确定") { _, _ ->
                        if (selectedIndex >= 0) {
                            deferred.complete(options[selectedIndex])
                        } else {
                            deferred.complete("(未选择)")
                        }
                    }
                    .setNegativeButton("取消") { _, _ ->
                        deferred.complete("(用户取消)")
                    }
                    .setCancelable(false)
                    .show()
            }
            deferred.await()
        }

        builder.multiSelectHandler = { question, options ->
            val deferred = CompletableDeferred<List<String>>()
            withContext(Dispatchers.Main) {
                val selected = BooleanArray(options.size) { false }
                var dialog: AlertDialog? = null

                val b = AlertDialog.Builder(activity)
                    .setTitle(question)
                    .setSingleChoiceItems(options.toTypedArray(), -1) { _, which ->
                        selected[which] = !selected[which]
                        dialog?.listView?.setItemChecked(which, selected[which])
                    }
                    .setPositiveButton("确定") { _, _ ->
                        val result = options.filterIndexed { index, _ -> selected[index] }
                        deferred.complete(result.ifEmpty { listOf("(未选择)") })
                    }
                    .setNegativeButton("取消") { _, _ ->
                        deferred.complete(listOf("(用户取消)"))
                    }
                    .setCancelable(false)

                dialog = b.create()
                dialog.show()
                dialog.listView?.choiceMode = android.widget.ListView.CHOICE_MODE_MULTIPLE
            }
            deferred.await()
        }

        builder.rankPrioritiesHandler = { question, items ->
            val deferred = CompletableDeferred<List<String>>()
            withContext(Dispatchers.Main) {
                val ranked = items.toMutableList()
                val adapter = android.widget.ArrayAdapter(
                    activity,
                    android.R.layout.simple_list_item_1,
                    ranked
                )
                val listView = android.widget.ListView(activity).apply {
                    this.adapter = adapter
                    setPadding(dp(activity, 16), dp(activity, 8), dp(activity, 16), dp(activity, 8))
                }

                listView.setOnItemClickListener { _, _, position, _ ->
                    if (position > 0) {
                        val item = ranked.removeAt(position)
                        ranked.add(position - 1, item)
                        adapter.notifyDataSetChanged()
                    }
                }

                AlertDialog.Builder(activity)
                    .setTitle("AI 询问 - 排序优先级")
                    .setMessage("$question\n\n点击项目向上移动")
                    .setView(listView)
                    .setPositiveButton("确定") { _, _ ->
                        deferred.complete(ranked)
                    }
                    .setNegativeButton("取消") { _, _ ->
                        deferred.complete(items)
                    }
                    .setCancelable(false)
                    .show()
            }
            deferred.await()
        }

        builder.askMultipleQuestionsHandler = { questions ->
            val deferred = CompletableDeferred<List<String>>()
            withContext(Dispatchers.Main) {
                val answers = mutableListOf<String>()
                val inputs = questions.map { question ->
                    val layout = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(activity, 16), dp(activity, 8), dp(activity, 16), dp(activity, 8))
                    }
                    val label = TextView(activity).apply {
                        text = question
                        setPadding(0, 0, 0, dp(activity, 8))
                    }
                    val input = EditText(activity).apply {
                        hint = "请输入回复"
                    }
                    layout.addView(label)
                    layout.addView(input)
                    layout to input
                }

                val scrollView = ScrollView(activity).apply {
                    val container = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        inputs.forEach { (layout, _) -> addView(layout) }
                    }
                    addView(container)
                }

                AlertDialog.Builder(activity)
                    .setTitle("AI 询问 (${questions.size} 个问题)")
                    .setView(scrollView)
                    .setPositiveButton("发送") { _, _ ->
                        inputs.forEach { (_, input) ->
                            answers.add(input.text.toString().trim().ifEmpty { "(无回复)" })
                        }
                        deferred.complete(answers)
                    }
                    .setNegativeButton("取消") { _, _ ->
                        deferred.complete(List(questions.size) { "(用户取消)" })
                    }
                    .setCancelable(false)
                    .show()
            }
            deferred.await()
        }
    }

    private fun dp(activity: Activity, value: Int): Int {
        return (value * activity.resources.displayMetrics.density).toInt()
    }
}
