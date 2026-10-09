package com.mochihoppe.inputlab

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.SystemClock
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in device smoke test. Run ONLY after approval, with Mochi IME selected.
 * Start with Japanese tenkey selected. Uses only the offline lab, never sends
 * text or performs image searches.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardSmokeTest {
    @Test fun imagePanelAndQwertySurviveKeyboardHideAndShow() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ui = instrumentation.uiAutomation
        ui.serviceInfo = ui.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, NotepadActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val focusDeadline = SystemClock.uptimeMillis() + 10_000
        while (!activity.hasWindowFocus() && SystemClock.uptimeMillis() < focusDeadline) SystemClock.sleep(100)
        assertTrue("Offline lab must be focused before requesting the IME", activity.hasWindowFocus())
        fun editors(view: android.view.View): List<EditText> =
            (if (view is EditText) listOf(view) else emptyList()) +
                if (view is ViewGroup) (0 until view.childCount).flatMap { editors(view.getChildAt(it)) } else emptyList()
        lateinit var editor: EditText
        instrumentation.runOnMainSync { editor = editors(activity.window.decorView).first(); editor.requestFocus() }
        val manager = activity.getSystemService(InputMethodManager::class.java)
        fun show() = instrumentation.runOnMainSync { manager.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT) }
        fun nodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (root == null) emptyList()
            else listOf(root) + (0 until root.childCount).flatMap { nodes(root.getChild(it)) }
        fun imeNodes(): List<AccessibilityNodeInfo> {
            // MuMu presents the app on a virtual display rather than display 0.
            val windows = if (android.os.Build.VERSION.SDK_INT >= 30) {
                val displays = ui.windowsOnAllDisplays
                (0 until displays.size()).flatMap { displays.valueAt(it) }
            } else ui.windows
            return windows.flatMap { nodes(it.root) }.filter { it.packageName?.toString() == "com.mochihoppe.explorer.ime" }
        }
        fun waitFor(label: String, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
            val deadline = SystemClock.uptimeMillis() + 15_000
            do {
                imeNodes().firstOrNull(predicate)?.let { return it }
                SystemClock.sleep(100)
            } while (SystemClock.uptimeMillis() < deadline)
            fail("IME element missing: $label; visible labels=${imeNodes().mapNotNull { it.text }.take(60)}")
            error("unreachable")
        }
        fun click(text: String) {
            var node = waitFor(text) { it.text?.toString() == text && it.isEnabled }
            while (!node.isClickable && node.parent != null) node = node.parent
            assertTrue("Cannot activate $text", node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        }
        fun qwerty() = waitFor("QWERTY q key") { it.text?.toString()?.lowercase() == "q" || it.contentDescription?.toString()?.lowercase() == "q" }
        fun tapKey(id: String) {
            val node = waitFor(id) { it.viewIdResourceName?.endsWith("/" + id) == true && it.isVisibleToUser }
            // Upstream keys dispatch touch gestures rather than ACTION_CLICK.
            val bounds = android.graphics.Rect().also(node::getBoundsInScreen)
            val display = if (android.os.Build.VERSION.SDK_INT >= 30) node.window?.displayId ?: 0 else 0
            ui.executeShellCommand("input -d $display tap ${bounds.centerX()} ${bounds.centerY()}").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
            }
        }
        try {
            show()
            click("画像")
            waitFor("お気に入り") { it.text?.toString() == "お気に入り" }
            assertFalse(imeNodes().any { it.text?.contains("タップでURL挿入") == true })
            click("文字")
            assertFalse(imeNodes().any { it.text?.toString() == "配列" })
            tapKey("key_small_letter")
            qwerty()
            instrumentation.runOnMainSync { manager.hideSoftInputFromWindow(editor.windowToken, 0) }
            SystemClock.sleep(800)
            show()
            qwerty()
            tapKey("key_switch_default")
            waitFor("Japanese tenkey restored by globe") { it.viewIdResourceName?.endsWith("/key_small_letter") == true }
            instrumentation.runOnMainSync { assertEquals("", editor.text.toString()) }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
