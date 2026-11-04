package de.dennisguse.opentracks.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dennisguse.opentracks.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

private const val GAUGE_START = 135f
private const val GAUGE_SWEEP = 270f

/**
 * Instrument-cluster speedometer: segmented arc lit up to the current speed, ticks every
 * 10 km/h with labels every 20, and a marker at the trip's max speed.
 */
@Composable
fun SpeedGauge(
    speedKmh: Double,
    maxSpeedKmh: Double,
    color: Color,
    modifier: Modifier = Modifier,
    scaleMax: Int = 180
) {
    val fraction = (speedKmh / scaleMax).coerceIn(0.0, 1.0).toFloat()
    val maxFraction = (maxSpeedKmh / scaleMax).coerceIn(0.0, 1.0).toFloat()
    Box(modifier = modifier.aspectRatio(1.25f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.07f
            val diameter = size.minDimension - stroke * 2
            val topLeft = Offset((size.width - diameter) / 2, stroke + size.height * 0.04f)
            val arcSize = Size(diameter, diameter)
            val center = Offset(topLeft.x + diameter / 2, topLeft.y + diameter / 2)
            val radius = diameter / 2

            // Segmented track and lit segments (like LED cluster bars)
            val segments = 36
            val segSweep = GAUGE_SWEEP / segments
            for (i in 0 until segments) {
                val lit = (i + 1) / segments.toFloat() <= fraction + 0.0001f
                drawArc(
                    color = if (lit) color else CyberLineSubtle,
                    startAngle = GAUGE_START + i * segSweep + 1f,
                    sweepAngle = segSweep - 2f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )
            }

            // Ticks
            for (v in 0..scaleMax step 10) {
                val angle = Math.toRadians((GAUGE_START + GAUGE_SWEEP * v / scaleMax).toDouble())
                val major = v % 20 == 0
                val outer = radius - stroke * 0.9f
                val inner = outer - if (major) stroke * 0.9f else stroke * 0.45f
                drawLine(
                    color = if (major) CyberText else CyberTextTertiary,
                    start = Offset(center.x + (cos(angle) * inner).toFloat(), center.y + (sin(angle) * inner).toFloat()),
                    end = Offset(center.x + (cos(angle) * outer).toFloat(), center.y + (sin(angle) * outer).toFloat()),
                    strokeWidth = if (major) 3f else 1.5f
                )
            }

            // Max-speed marker
            if (maxFraction > 0f) {
                rotate(GAUGE_START + GAUGE_SWEEP * maxFraction + 90f, center) {
                    drawRect(
                        color = CyberMagenta,
                        topLeft = Offset(center.x - stroke * 0.25f, topLeft.y - stroke * 0.55f),
                        size = Size(stroke * 0.5f, stroke * 1.1f)
                    )
                }
            }
        }

        // Scale labels
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight
            val d = minOf(w, h) * 0.86f
            val r = d / 2 - (minOf(w, h) * 0.07f) * 2.7f
            for (v in 0..scaleMax step 40) {
                val angle = Math.toRadians((GAUGE_START + GAUGE_SWEEP * v / scaleMax).toDouble())
                val cx = w / 2 + r * cos(angle).toFloat()
                val cy = (minOf(w, h) * 0.07f) + h * 0.04f + d / 2 + r * sin(angle).toFloat()
                Text(
                    "$v",
                    style = monoStyle(10, FontWeight.Normal),
                    color = CyberTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .offset(x = cx - 14.dp, y = cy - 8.dp)
                        .width(28.dp)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${speedKmh.toInt()}", style = displayStyle(96).copy(lineHeight = 92.sp), color = color)
            Text("KM/H", style = EyebrowStyle.copy(fontSize = 13.sp, letterSpacing = 3.sp), color = CyberText)
            Spacer(Modifier.height(4.dp))
            Text("MÁX ${maxSpeedKmh.toInt()}", style = monoStyle(11), color = CyberMagenta)
        }
    }
}

/** Mechanical odometer: one framed drum per digit, last drum tinted like the tenths wheel. */
@Composable
fun OdometerWheels(km: Double, modifier: Modifier = Modifier, digits: Int = 6, drum: Dp = 30.dp) {
    val text = km.toLong().coerceAtLeast(0).toString().padStart(digits, '0').takeLast(maxOf(digits, km.toLong().toString().length))
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        text.forEachIndexed { i, c ->
            val last = i == text.lastIndex
            Box(
                modifier = Modifier
                    .size(width = drum, height = drum * 1.35f)
                    .background(if (last) CyberMagenta else CyberOnAcid, RectangleShape),
                contentAlignment = Alignment.Center
            ) {
                // drum shading bands
                Box(Modifier.fillMaxWidth().height(drum * 0.22f).align(Alignment.TopCenter).background(Color.Black.copy(alpha = 0.35f)))
                Box(Modifier.fillMaxWidth().height(drum * 0.22f).align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.35f)))
                Text("$c", style = monoStyle((drum.value * 0.85f).toInt(), FontWeight.Bold), color = if (last) CyberOnMagenta else CyberAcid)
            }
        }
    }
}

/** Brazilian Mercosul plate: white plate, blue band with BRASIL, black characters. */
@Composable
fun MercosulPlate(plate: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(98.dp)
            .border(2.dp, CyberOnAcid, RectangleShape)
            .background(Color.White)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(Color(0xFF003399)),
            contentAlignment = Alignment.Center
        ) {
            Text("BRASIL", fontSize = 6.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 1.sp, lineHeight = 7.sp)
        }
        Text(
            plate.uppercase(),
            style = monoStyle(16, FontWeight.Bold),
            color = Color.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
        )
    }
}

/** Dashed lane marking used as a section divider. */
@Composable
fun RoadDivider(modifier: Modifier = Modifier, color: Color = CyberAcid) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(4.dp)
    ) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(28f, 18f))
        )
    }
}
