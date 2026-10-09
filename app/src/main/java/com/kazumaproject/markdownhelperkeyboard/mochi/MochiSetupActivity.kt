package com.kazumaproject.markdownhelperkeyboard.mochi

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity

class MochiSetupActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        val scroll = ScrollView(this).apply { addView(content) }; setContentView(scroll)
        content.addView(TextView(this).apply { text = getString(com.kazumaproject.markdownhelperkeyboard.R.string.app_name); textSize = 24f })
        content.addView(TextView(this).apply {
            text = "日本語入力・変換は端末内で処理します。画像検索は、専用検索欄で検索を押したときだけサイトへ検索語を送ります。投稿文・パスワード・クリップボード内容は画像検索へ送りません。\n\n画像を選ぶとURLだけを挿入し、送信・Enter・改行は行いません。サイトへのログイン・使用数加算はありません。通信基盤にはIP等のアクセス記録が残る場合があります。\n\n最近・お気に入りはこの端末だけに保存します。サムネキャッシュ上限は50MBです。Sumireを基にした非公式派生版で、完全オフラインのアプリではありません。"
        })
        fun action(title: String, block: () -> Unit) { content.addView(Button(this).apply { text = title; setOnClickListener { block() } }) }
        action("1. キーボードを有効にする") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        action("2. キーボードを切り替える") { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
        action("日本語入力・テーマ・辞書の設定") { startActivity(Intent(this, MainActivity::class.java)) }
        action("最近・お気に入り・画像キャッシュを削除") {
            AlertDialog.Builder(this).setMessage("この端末の画像機能の保存データを削除しますか？ 日本語辞書は削除しません。")
                .setNegativeButton("キャンセル", null).setPositiveButton("削除") { _, _ ->
                    MochiSavedImages.clear(this); Toast.makeText(this, "画像機能のデータを削除しました", Toast.LENGTH_SHORT).show()
                }.show()
        }
        action("派生元・ライセンスについて") {
            val names = assets.list("licenses").orEmpty().sorted().toTypedArray()
            AlertDialog.Builder(this).setTitle("派生元・ライセンス")
                .setItems(names) { _, index ->
                    val name = names[index]
                    val source = assets.open("licenses/$name").bufferedReader().use { it.readText() }
                    val text = TextView(this).apply {
                        setPadding(24, 16, 24, 16)
                        setTextIsSelectable(true)
                        this.text = if (name.endsWith(".html")) android.text.Html.fromHtml(source, android.text.Html.FROM_HTML_MODE_LEGACY) else source
                    }
                    AlertDialog.Builder(this).setTitle(name)
                        .setView(ScrollView(this).apply { addView(text) })
                        .setPositiveButton("閉じる", null).show()
                }.setNegativeButton("閉じる", null).show()
        }
    }
}
