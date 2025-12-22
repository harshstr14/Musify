package com.example.musify

import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan

class CustomTypefaceSpan(
    private val typeface: Typeface
) : MetricAffectingSpan() {

    override fun updateDrawState(tp: TextPaint) {
        apply(tp)
    }

    override fun updateMeasureState(tp: TextPaint) {
        apply(tp)
    }

    private fun apply(paint: TextPaint) {
        paint.typeface = typeface
        paint.isAntiAlias = true
    }
}