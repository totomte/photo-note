package com.thomas.photonote

import android.app.Activity
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var titleEdit: EditText
    private lateinit var whereEdit: EditText
    private lateinit var storyEdit: EditText
    private lateinit var captionEdit: EditText
    private lateinit var storyRow: LinearLayout
    private var storyMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (20 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        layout.addView(TextView(this).apply {
            text = "Story card (title / where / story) or plain caption.\nEither way it lands in your photo roll as a JPG."
            textSize = 16f
            setPadding(0, 0, 0, pad)
        })

        storyRow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        titleEdit = EditText(this).apply {
            hint = "Title"
            inputType = InputType.TYPE_CLASS_TEXT
            textSize = 18f
        }
        storyRow.addView(titleEdit)

        whereEdit = EditText(this).apply {
            hint = "Where / when"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        storyRow.addView(whereEdit)

        storyEdit = EditText(this).apply {
            hint = "The story…"
            minLines = 5
            maxLines = 12
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP
        }
        storyRow.addView(storyEdit)

        layout.addView(storyRow)

        val modeBtn = Button(this).apply {
            text = "Switch to caption"
            setOnClickListener {
                storyMode = !storyMode
                storyRow.visibility = if (storyMode) LinearLayout.VISIBLE else LinearLayout.GONE
                captionEdit.visibility = if (storyMode) LinearLayout.GONE else LinearLayout.VISIBLE
                text = if (storyMode) "Switch to caption" else "Switch to story card"
            }
        }
        layout.addView(modeBtn)

        captionEdit = EditText(this).apply {
            hint = "Caption text…"
            minLines = 6
            maxLines = 10
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP
            visibility = LinearLayout.GONE
        }
        layout.addView(captionEdit)

        layout.addView(Button(this).apply {
            text = "Generate"
            setOnClickListener {
                if (storyMode) generateStory() else generate(captionEdit.text.toString())
            }
        })

        val scroll = ScrollView(this)
        scroll.addView(layout)
        setContentView(scroll)
    }

    private fun wrapLines(p: Paint, text: String, maxTextW: Float): List<String> {
        val lines = mutableListOf<String>()
        for (raw in text.replace("\r", "").split("\n")) {
            if (raw.isEmpty()) { lines.add(""); continue }
            var cur = ""
            for (word in raw.split(" ")) {
                val test = if (cur.isEmpty()) word else "$cur $word"
                if (p.measureText(test) <= maxTextW) {
                    cur = test
                } else {
                    if (cur.isNotEmpty()) lines.add(cur)
                    var part = word
                    while (p.measureText(part) > maxTextW) {
                        var cut = part.length - 1
                        while (cut > 0 && p.measureText(part.substring(0, cut)) > maxTextW) cut--
                        lines.add(part.substring(0, cut))
                        part = part.substring(cut)
                    }
                    cur = part
                }
            }
            if (cur.isNotEmpty()) lines.add(cur)
        }
        return lines
    }

    private fun generateStory() {
        val title = titleEdit.text.toString().trim()
        val where = whereEdit.text.toString().trim()
        val story = storyEdit.text.toString().trim()
        if (title.isEmpty() && story.isEmpty()) {
            Toast.makeText(this, "Nothing to write.", Toast.LENGTH_SHORT).show()
            return
        }
        val w = 1080
        val margin = 120f
        val maxTextW = w - 2 * margin

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 72f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val wherePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 200, 90)
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
        val storyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 50f
        }

        val titleLines = if (title.isEmpty()) emptyList() else wrapLines(titlePaint, title, maxTextW)
        val whereLines = if (where.isEmpty()) emptyList() else wrapLines(wherePaint, where, maxTextW)
        val storyLines = if (story.isEmpty()) emptyList() else wrapLines(storyPaint, story, maxTextW)

        val ts = titlePaint.textSize * 1.25f
        val ws = wherePaint.textSize * 1.4f
        val ss = storyPaint.textSize * 1.45f
        var h = 2 * margin
        if (titleLines.isNotEmpty()) h += (titleLines.size * ts + 40).toInt()
        if (whereLines.isNotEmpty()) h += (whereLines.size * ws + 40).toInt()
        if (storyLines.isNotEmpty()) h += (storyLines.size * ss).toInt()
        h = (h * 1.05f).toInt()

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.BLACK)
        var y = margin
        for (line in titleLines) { y += ts; canvas.drawText(line, margin, y, titlePaint) }
        if (titleLines.isNotEmpty()) y += 40
        for (line in whereLines) { y += ws; canvas.drawText(line, margin, y, wherePaint) }
        if (whereLines.isNotEmpty()) y += 40
        for (line in storyLines) { y += ss; canvas.drawText(line, margin, y, storyPaint) }

        saveJpg(bmp)
        storyEdit.setText("")
    }

    private fun generate(text: String) {
        if (text.isBlank()) {
            Toast.makeText(this, "Nothing to write.", Toast.LENGTH_SHORT).show()
            return
        }
        val w = 1080
        val margin = 120f
        val ts = 52f
        val lh = ts * 1.45f
        val maxTextW = w - 2 * margin

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = ts
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val lines = wrapLines(paint, text, maxTextW).ifEmpty { listOf("") }

        val h = (2 * margin + lines.size * lh).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.BLACK)
        var y = margin + ts
        for (line in lines) {
            canvas.drawText(line, margin, y, paint)
            y += lh
        }

        saveJpg(bmp)
        captionEdit.setText("")
    }

    private fun saveJpg(bmp: Bitmap) {
        val resolver = contentResolver
        fun insert(): android.net.Uri? {
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "PhotoNote_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PhotoNote")
            }
            return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
        }
        var uri = insert()
        if (uri == null) uri = insert()
        if (uri == null) {
            Toast.makeText(this, "Save failed.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            resolver.openOutputStream(uri!!)?.use { os ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 95, os)
            }
            Toast.makeText(this, "Saved to Pictures/PhotoNote", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
