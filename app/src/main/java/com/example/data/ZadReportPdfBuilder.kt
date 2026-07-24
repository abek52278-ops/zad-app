package com.example.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * يبني تقرير زاد الشهري كملف PDF حقيقي بترويسة وختم أحمر رسمي — بديل مشاركة
 * النص العادي اللي كانت بتستخدمه ExportReportButton (نفس محتوى buildExportText،
 * لكن كملف منسّق باسم التطبيق بدل نص خام في الـ Share Sheet).
 */
object ZadReportPdfBuilder {

    private const val PAGE_WIDTH = 595   // A4 @ 72dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private val ZAD_RED = Color.rgb(0xC6, 0x28, 0x28)

    fun build(context: Context, report: ZadCentralBrain.BrainReport): File {
        val document = PdfDocument()
        val bodyText = ZadCentralBrain.buildExportText(context, report)

        val bodyPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 13f
            color = Color.rgb(30, 30, 30)
        }
        val contentWidth = (PAGE_WIDTH - MARGIN * 2).toInt()
        val layout = StaticLayout.Builder
            .obtain(bodyText, 0, bodyText.length, bodyPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setLineSpacing(4f, 1.1f)
            .build()

        val headerHeight = 65f
        val contentTop = MARGIN + headerHeight
        val pageBottom = PAGE_HEIGHT - MARGIN

        var lineOffset = 0
        var pageNum = 1
        var isFirstPage = true

        while (lineOffset < layout.lineCount) {
            val pageTop = if (isFirstPage) contentTop else MARGIN
            val availableHeight = pageBottom - pageTop
            var linesThisPage = 0
            val startTop = layout.getLineTop(lineOffset)
            while (lineOffset + linesThisPage < layout.lineCount) {
                val lineBottom = layout.getLineBottom(lineOffset + linesThisPage)
                if (lineBottom - startTop > availableHeight && linesThisPage > 0) break
                linesThisPage++
            }

            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            if (isFirstPage) drawHeader(canvas)

            val sliceTop = layout.getLineTop(lineOffset)
            val sliceBottom = layout.getLineBottom(lineOffset + linesThisPage - 1)
            canvas.save()
            canvas.clipRect(MARGIN, pageTop, PAGE_WIDTH - MARGIN, pageTop + (sliceBottom - sliceTop))
            canvas.translate(MARGIN, pageTop - sliceTop)
            layout.draw(canvas)
            canvas.restore()

            lineOffset += linesThisPage
            val isLastPage = lineOffset >= layout.lineCount
            if (isLastPage) drawStamp(canvas)

            document.finishPage(page)
            pageNum++
            isFirstPage = false
        }

        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val fileName = "zad_report_${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.pdf"
        val file = File(reportsDir, fileName)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun drawHeader(canvas: Canvas) {
        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = ZAD_RED
            textSize = 26f
            isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
        }
        val subtitlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(90, 90, 90)
            textSize = 12f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("تقرير زاد الشهري", PAGE_WIDTH - MARGIN, MARGIN + 28f, titlePaint)
        val today = LocalDate.now()
        canvas.drawText(
            "صادر بتاريخ ${today.dayOfMonth}/${today.monthValue}/${today.year} — تحليل عقل زاد لسلوكك المالي",
            PAGE_WIDTH - MARGIN, MARGIN + 50f, subtitlePaint
        )
        val linePaint = Paint().apply { color = ZAD_RED; strokeWidth = 2f }
        canvas.drawLine(MARGIN, MARGIN + 62f, PAGE_WIDTH - MARGIN, MARGIN + 62f, linePaint)
    }

    /** ختم أحمر دائري — "معتمد من عقل زاد" في آخر صفحة، بديل شعار رسمي مرفوع كملف صورة */
    private fun drawStamp(canvas: Canvas) {
        val cx = PAGE_WIDTH - MARGIN - 60f
        val cy = PAGE_HEIGHT - MARGIN - 70f
        val radius = 55f
        val stampPaint = Paint().apply {
            isAntiAlias = true
            color = ZAD_RED
            style = Paint.Style.STROKE
            strokeWidth = 3f
            alpha = 200
        }
        canvas.drawCircle(cx, cy, radius, stampPaint)
        canvas.drawCircle(cx, cy, radius - 8f, stampPaint)

        val stampTextPaint = Paint().apply {
            isAntiAlias = true
            color = ZAD_RED
            textAlign = Paint.Align.CENTER
            alpha = 200
        }
        canvas.save()
        canvas.rotate(-14f, cx, cy)
        stampTextPaint.textSize = 20f
        stampTextPaint.isFakeBoldText = true
        canvas.drawText("زاد", cx, cy - 2f, stampTextPaint)
        stampTextPaint.textSize = 10f
        stampTextPaint.isFakeBoldText = false
        canvas.drawText("معتمد من عقل زاد", cx, cy + 16f, stampTextPaint)
        canvas.restore()
    }
}
