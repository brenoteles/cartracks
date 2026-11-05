package de.dennisguse.opentracks.io

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import de.dennisguse.opentracks.R
import java.io.File
import java.io.FileOutputStream

/** Stacks header, map snapshot and the infographic into one PNG and opens the share sheet. */
object ReportImageExporter {

    private const val INK = 0xFF0E0D1A.toInt()
    private const val ACID = 0xFFE8FF3A.toInt()
    private const val MAGENTA = 0xFFC5259C.toInt()
    private const val MUTED = 0xFFBDC1A3.toInt()

    /** Graphics-layer captures are HARDWARE bitmaps; a software Canvas can only draw software ones. */
    private fun software(b: Bitmap): Bitmap =
        if (b.config == Bitmap.Config.HARDWARE) b.copy(Bitmap.Config.ARGB_8888, false) else b

    fun compose(context: Context, title: String, subtitle: String, mapIn: Bitmap?, bodyIn: Bitmap): Bitmap {
        val map = mapIn?.let { software(it) }
        val body = software(bodyIn)
        val width = body.width
        val pad = (width * 0.045f).toInt()
        val headerH = (width * 0.2f).toInt()
        val mapH = map?.let { (it.height.toFloat() * width / it.width).toInt() } ?: 0
        val footerH = (width * 0.07f).toInt()
        val out = Bitmap.createBitmap(width, headerH + mapH + body.height + footerH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(INK)

        val bebas = ResourcesCompat.getFont(context, R.font.bebas_neue_regular)
        val mono = ResourcesCompat.getFont(context, R.font.ibm_plex_mono_medium)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = ACID
        p.typeface = bebas
        p.textSize = headerH * 0.42f
        c.drawText(title, pad.toFloat(), headerH * 0.52f, p)
        p.typeface = mono
        p.color = MUTED
        p.textSize = headerH * 0.13f
        c.drawText(subtitle, pad.toFloat(), headerH * 0.78f, p)
        p.color = ACID
        c.drawRect(0f, headerH - 6f, width.toFloat(), headerH.toFloat(), p)

        var y = headerH
        if (map != null) {
            c.drawBitmap(map, null, Rect(0, y, width, y + mapH), Paint(Paint.FILTER_BITMAP_FLAG))
            y += mapH
        }
        c.drawBitmap(body, 0f, y.toFloat(), null)
        y += body.height

        p.color = MAGENTA
        c.drawRect(0f, y.toFloat(), width.toFloat(), y + 4f, p)
        p.color = MUTED
        p.textSize = footerH * 0.38f
        c.drawText("Gerado pelo CarTracks", pad.toFloat(), y + footerH * 0.62f, p)
        return out
    }

    fun share(context: Context, image: Bitmap, fileName: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartilhar relatório").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
