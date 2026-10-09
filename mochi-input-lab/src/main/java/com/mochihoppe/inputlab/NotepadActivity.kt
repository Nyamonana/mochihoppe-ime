package com.mochihoppe.inputlab

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.*

/** A separate offline APK. Text stays in RAM and there is no posting capability. */
class NotepadActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }
        body.addView(TextView(this).apply { text = "送信機能なし・オフライン入力テスト\nChMateや掲示板には接続しません。" })
        fun field(title: String, type: Int, options: Int = EditorInfo.IME_ACTION_NONE) {
            body.addView(EditText(this).apply { hint = title; inputType = type; imeOptions = options or EditorInfo.IME_FLAG_NO_EXTRACT_UI; minLines = 2 })
        }
        field("本文A：日本語・URL挿入・選択範囲を確認", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        field("本文B：入力先切替を確認", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        field("プライベート：画像機能が無効", InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        field("パスワード：画像機能が無効", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        field("PIN：画像機能が無効", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        setContentView(ScrollView(this).apply { addView(body) })
    }
}
