package com.kazumaproject.markdownhelperkeyboard.mochi

import android.content.Context
import android.graphics.Color
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputConnection
import android.widget.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

/** Network/UI island. Never receives editor text or access to clipboard/actions. */
class MochiKeyboardHost(
    context: Context,
    private val keyboard: View,
    private val allowed: () -> Boolean,
    private val session: () -> Long,
    private val externalConnection: () -> InputConnection?,
    private val routeSearchInput: (EditText?) -> Unit,
    private val searchSelection: (EditText, Int, Int) -> Unit,
    private val client: MochiImageSource = MochiClient(context),
) : LinearLayout(context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var saved = MochiSavedImages(context)
    private val inserter = MochiUrlInserter(session, externalConnection)
    private var opened = false
    private var editing = false
    private var busy = false
    private var mode = "search"
    private var openedSession = -1L
    private var nextPage: Int? = null
    private var submittedQuery = ""
    private var submittedGif = false
    private var operation: Job? = null
    private var operationGeneration = 0L
    private val toolbar = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(Color.rgb(49, 36, 62)) }
    private fun button(text: String, action: () -> Unit) = Button(context).apply {
        this.text = text; textSize = 12f; minWidth = 0; minimumWidth = 0; isAllCaps = false
        setPadding(dp(4), 0, dp(4), 0); setOnClickListener { action() }
    }
    private val imageButton = button("画像") { if (opened) close() else open() }.apply { contentDescription = "画像パネルを開く・閉じる" }
    private val query = object : EditText(context) {
        override fun onSelectionChanged(start: Int, end: Int) { super.onSelectionChanged(start, end); searchSelection(this, start, end) }
    }.apply {
        hint = "画像を検索"; textSize = 16f; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
        setSingleLine(true); inputType = InputType.TYPE_CLASS_TEXT
        filters = arrayOf(InputFilter.LengthFilter(128)); showSoftInputOnFocus = false
        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        setOnFocusChangeListener { _, focus -> if (focus && opened) beginEditing() }
        setOnClickListener { if (opened) beginEditing() }
        setOnEditorActionListener { _, _, _ -> submit(); true }
    }
    private val searchButton = button("検索") { submit() }
    private val clearQuery = button("×") { query.setText(""); if (opened) beginEditing() }.apply { contentDescription = "検索語を消去" }
    private var messageToast: Toast? = null
    private fun message(text: String) {
        messageToast?.cancel()
        messageToast = Toast.makeText(context, text, Toast.LENGTH_LONG).also { it.show() }
    }
    private val grid = RecyclerView(context).apply {
        layoutManager = GridLayoutManager(context, tileColumns(resources.configuration.screenWidthDp))
        itemAnimator = null
        setHasFixedSize(true)
    }
    private val tabs = LinearLayout(context)
    private val progress = ProgressBar(context).apply { visibility = GONE; contentDescription = "画像を取得中" }
    private val gridArea = FrameLayout(context).apply {
        addView(grid, FrameLayout.LayoutParams(-1, -1))
        addView(progress, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER))
    }
    private val pageButton = button("次の24件") { nextPage?.let { loadPage(it) } }
    private val panel = LinearLayout(context).apply { orientation = VERTICAL; setBackgroundColor(Color.rgb(31, 24, 39)) }
    private val adapter = Tiles()
    private val preferredPanelHeight = dp((resources.configuration.screenHeightDp * 0.42f).toInt().coerceIn(160, 360))

    init {
        orientation = VERTICAL
        toolbar.addView(imageButton, LayoutParams(dp(52), dp(48)))
        toolbar.addView(query, LayoutParams(0, dp(48), 1f))
        toolbar.addView(clearQuery, LayoutParams(dp(40), dp(48)))
        toolbar.addView(searchButton, LayoutParams(dp(52), dp(48)))
        addView(toolbar, LayoutParams(-1, dp(48)))
        addView(keyboard, LayoutParams(-1, -2))
        listOf("文字" to { close() }, "検索" to { showMode("search") }, "最近" to { showMode("recent") }, "お気に入り" to { showMode("favorites") }, "GIF" to { showMode("gif") }).forEach { (label, action) ->
            tabs.addView(button(label, action).apply { textSize = 11f; setTextColor(Color.WHITE); setBackgroundColor(Color.TRANSPARENT) }, LayoutParams(0, dp(44), 1f))
        }
        // Keep navigation above the scrollable grid, including constrained IME windows.
        panel.addView(tabs, LayoutParams(-1, dp(44)))
        grid.adapter = adapter
        panel.addView(gridArea, LayoutParams(-1, 0, 1f))
        panel.addView(pageButton, LayoutParams(-1, dp(40)))
        addView(panel, LayoutParams(-1, preferredPanelHeight))
        renderClosed()
    }

    fun refreshAvailability() {
        if (!allowed()) { close(); imageButton.visibility = GONE } else imageButton.visibility = VISIBLE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (panel.visibility != GONE) {
            val available = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) preferredPanelHeight
                else (MeasureSpec.getSize(heightMeasureSpec) - dp(48) - paddingTop - paddingBottom).coerceAtLeast(0)
            // The previous fixed panel plus toolbar could exceed the parent and clip tabs.
            panel.layoutParams.height = minOf(preferredPanelHeight, available)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun open() {
        if (!allowed() || externalConnection() == null) return
        // Finish the caller's composition BEFORE routing any search keystrokes.
        routeSearchInput(null)
        openedSession = session(); opened = true; saved = MochiSavedImages(context)
        query.visibility = VISIBLE; clearQuery.visibility = VISIBLE; searchButton.visibility = VISIBLE
        showMode("search")
    }

    private fun beginEditing() {
        if (!opened || !allowed() || openedSession != session() || busy) return
        if (!editing) { routeSearchInput(query); editing = true }
        panel.visibility = GONE; keyboard.visibility = VISIBLE
    }

    private fun finishEditing() {
        if (editing) { routeSearchInput(null); editing = false }
        query.clearFocus()
    }

    private fun showMode(value: String) {
        if (!opened) return
        finishEditing(); cancelOperation(); mode = value
        panel.visibility = VISIBLE; keyboard.visibility = GONE; pageButton.visibility = GONE; nextPage = null
        val items = when (mode) { "recent" -> saved.recent().map(::fromSaved); "favorites" -> saved.favorites().map(::fromSaved); else -> emptyList() }
        adapter.replace(items)
        if (mode == "favorites" && items.isEmpty()) message("画像の☆で保存すると、ここに表示されます（端末内のみ）")
        tabs.childrenButtons().forEach { it.setTextColor(if (it.text == when (mode) { "favorites" -> "お気に入り"; "recent" -> "最近"; "gif" -> "GIF"; else -> "検索" }) Color.CYAN else Color.WHITE) }
    }

    private fun submit() {
        if (!opened || !allowed()) return
        finishEditing()
        submittedQuery = query.text.toString(); submittedGif = mode == "gif"
        if (mode !in setOf("search", "gif")) mode = "search"
        panel.visibility = VISIBLE; keyboard.visibility = GONE
        adapter.replace(emptyList()); loadPage(1)
    }

    private fun loadPage(page: Int) {
        launchOperation {
            pageButton.visibility = GONE
            val result = client.search(submittedQuery, submittedGif, page)
            ensureSession()
            adapter.replace(result.items)
            nextPage = result.nextPage; pageButton.visibility = if (nextPage != null) VISIBLE else GONE
            if (result.items.isEmpty()) message("該当する画像がありません")
        }
    }

    private fun select(image: MochiImage) {
        if (busy) return
        val expected = externalConnection() ?: return
        val token = openedSession
        launchOperation {
            val images = client.images(image.postId)
            ensureSession()
            if (image.imageCount > 1) {
                adapter.replace(images.map { it.copy(title = image.title, imageCount = 1) })
                nextPage = null; pageButton.visibility = GONE
                message("挿入する画像を選んでください（${images.size}枚）")
            } else {
                val current = images.firstOrNull { it.id == image.id } ?: throw MochiNetworkException(404)
                if (!allowed() || !inserter.insert(token, expected, current.imageUrl, client.debugOrigin)) throw MochiNetworkException(409)
                // Local history errors must never cause a repeated insertion.
                runCatching { saved.remember(toSaved(current.copy(title = image.title))) }
                close()
            }
        }
    }

    private fun ensureSession() { check(opened && openedSession == session() && allowed()) }
    private fun launchOperation(action: suspend () -> Unit) {
        if (!opened || busy || !allowed()) return
        busy = true
        progress.visibility = VISIBLE
        val generation = ++operationGeneration
        operation = scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (opened) message(when {
                    error is MochiNetworkException && error.status == 404 -> "削除または非公開の画像です。挿入していません。"
                    error is MochiNetworkException && error.status == 429 -> "アクセスが集中しています。${error.waitSeconds}秒後に再試行してください。"
                    error is MochiNetworkException && error.status == 409 -> "入力先が変わったか、入力欄が挿入を拒否しました。元の入力欄でやり直してください。"
                    error is MochiNetworkException -> "画像の確認に失敗しました（HTTP ${error.status}）。時間をおいて再試行してください。"
                    else -> "取得・挿入できませんでした。画像または検索を押して再試行してください。"
                })
            } finally { if (generation == operationGeneration) { busy = false; progress.visibility = GONE } }
        }
    }

    private fun cancelOperation() { operationGeneration++; operation?.cancel(); operation = null; client.cancel(); busy = false; progress.visibility = GONE; messageToast?.cancel() }
    fun close() {
        if (editing) finishEditing()
        opened = false; openedSession = -1; cancelOperation(); scope.coroutineContext.cancelChildren()
        query.setText(""); query.clearFocus(); adapter.replace(emptyList()); client.clearMemory()
        renderClosed()
    }
    private fun renderClosed() {
        query.visibility = GONE; clearQuery.visibility = GONE; searchButton.visibility = GONE
        panel.visibility = GONE; keyboard.visibility = VISIBLE
    }
    fun dispose() { close(); scope.cancel() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toSaved(image: MochiImage) = SavedImage(image.postId, image.id, image.title, image.thumbnailUrl, image.fileType)
    private fun fromSaved(image: SavedImage) = MochiImage(image.assetId, image.fileType, image.thumbnailUrl, "", image.postId, image.title)
    private fun LinearLayout.childrenButtons() = (0 until childCount).mapNotNull { getChildAt(it) as? Button }
    companion object {
        internal fun tileColumns(widthDp: Int) = when { widthDp < 340 -> 2; widthDp < 600 -> 3; else -> 4 }
    }

    private inner class Tiles : RecyclerView.Adapter<Tile>() {
        private var items: List<MochiImage> = emptyList()
        fun replace(value: List<MochiImage>) { items = value; notifyDataSetChanged() }
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): Tile {
            val root = object : LinearLayout(context) {
                override fun onMeasure(w: Int, h: Int) { super.onMeasure(w, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(w) + dp(48), MeasureSpec.EXACTLY)) }
            }.apply { orientation = VERTICAL; layoutParams = RecyclerView.LayoutParams(-1, -2); setPadding(dp(2), dp(2), dp(2), dp(2)); setBackgroundColor(Color.rgb(48, 40, 56)) }
            val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            root.addView(image, LayoutParams(-1, 0, 1f))
            val caption = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            val label = TextView(context).apply { textSize = 12f; setTextColor(Color.WHITE); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(dp(4), 0, 0, 0) }
            caption.addView(label, LayoutParams(0, -1, 1f))
            val star = button("☆") {}.apply {
                contentDescription = "お気に入りに保存・解除"; textSize = 24f; setTextColor(Color.WHITE)
                minHeight = 0; minimumHeight = 0
                background = RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), null, GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) })
            }
            caption.addView(star, LayoutParams(dp(40), dp(48)))
            root.addView(caption, LayoutParams(-1, dp(48)))
            return Tile(root, image, label, star)
        }
        override fun onBindViewHolder(holder: Tile, position: Int) {
            val item = items[position]
            holder.job?.cancel(); holder.image.setImageDrawable(null)
            holder.label.text = if (item.imageCount > 1) "${item.imageCount}枚・${item.title}" else if (item.fileType == "gif") "GIF・${item.title}" else item.title
            holder.itemView.contentDescription = item.title.ifBlank { "画像" }
            holder.star.text = if (saved.isFavorite(item.postId, item.id)) "★" else "☆"
            holder.star.setOnClickListener {
                runCatching { saved.toggle(toSaved(item)) }.onSuccess {
                    holder.star.text = if (saved.isFavorite(item.postId, item.id)) "★" else "☆"
                }
            }
            holder.itemView.setOnClickListener { select(item) }
            holder.image.setOnClickListener { select(item) }
            holder.label.setOnClickListener { select(item) }
            holder.job = scope.launch {
                try { holder.image.setImageBitmap(client.thumbnail(item.thumbnailUrl)) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { holder.label.text = "サムネ取得不可" }
            }
        }
        override fun onViewRecycled(holder: Tile) { holder.job?.cancel(); holder.image.setImageDrawable(null) }
    }
    private class Tile(root: View, val image: ImageView, val label: TextView, val star: Button) : RecyclerView.ViewHolder(root) { var job: Job? = null }
}
