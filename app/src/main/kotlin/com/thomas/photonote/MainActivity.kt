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
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (20 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        layout.addView(android.widget.TextView(this).apply {
            text = "Write a caption, then Generate.\nIt lands in your photo roll as a JPG."
            textSize = 16f
            setPadding(0, 0, 0, pad)
        })

        val edit = EditText(this).apply {
            hint = "Caption text…"
            minLines = 6
            maxLines = 10
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP
            setSingleLine(false)
        }
        layout.addView(edit)

        layout.addView(Button(this).apply {
            text = "Generate"
            setOnClickListener { generate(edit.text.toString()) }
        })

        val scroll = ScrollView(this)
        scroll.addView(layout)
        setContentView(scroll)
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
        val meas = Paint(paint)

        val lines = mutableListOf<String>()
        for (raw in text.replace("\r", "").split("\n")) {
            if (raw.isEmpty()) { lines.add(""); continue }
            var cur = ""
            for (word in raw.split(" ")) {
                val test = if (cur.isEmpty()) word else "$cur $word"
                if (meas.measureText(test) <= maxTextW) {
                    cur = test
                } else {
                    if (cur.isNotEmpty()) lines.add(cur)
                    // hard-break overlong single words
                    var part = word
                    while (meas.measureText(part) > maxTextW) {
                        var cut = part.length - 1
                        while (cut > 0 && meas.measureText(part.substring(0, cut)) > maxTextW) cut--
                        lines.add(part.substring(0, cut))
                        part = part.substring(cut)
                    }
                    cur = part
                }
            }
            if (cur.isNotEmpty()) lines.add(cur)
        }
        if (lines.isEmpty()) lines.add("")

        val h = (2 * margin + lines.size * lh).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.BLACK)
        var y = margin + ts
        for (line in lines) {
            canvas.drawText(line, margin, y, paint)
            y += lh
        }

        // MediaStore insert; delete-then-insert to overwrite same-named file
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
            edit.setText("")
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
