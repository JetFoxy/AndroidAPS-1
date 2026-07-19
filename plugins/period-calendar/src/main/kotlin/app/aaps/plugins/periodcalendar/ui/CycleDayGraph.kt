package app.aaps.plugins.periodcalendar.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import app.aaps.core.graph.data.GraphViewWithCleanup
import app.aaps.core.graph.data.LineGraphSeries
import app.aaps.plugins.periodcalendar.R
import app.aaps.plugins.periodcalendar.services.CycleDayStatsService
import com.jjoe64.graphview.DefaultLabelFormatter
import com.jjoe64.graphview.GridLabelRenderer
import com.jjoe64.graphview.series.DataPoint
import java.text.NumberFormat
import kotlin.math.ceil

class CycleDayGraph
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0,
    ) : GraphViewWithCleanup(context, attrs, defStyle) {
        enum class GraphMode {
            TDD_TOTAL,
            TDD_BASAL,
            TDD_BOLUS,
            TDD_CARBS,
            ISF,
            IC,
        }

        private val modeColors =
            mapOf(
                GraphMode.TDD_TOTAL to Color.parseColor("#4CAF50"),
                GraphMode.TDD_BASAL to Color.parseColor("#2196F3"),
                GraphMode.TDD_BOLUS to Color.parseColor("#FF9800"),
                GraphMode.TDD_CARBS to Color.parseColor("#9C27B0"),
                GraphMode.ISF to Color.parseColor("#F44336"),
                GraphMode.IC to Color.parseColor("#00BCD4"),
            )

        private var currentMode = GraphMode.TDD_TOTAL
        private var stats: CycleDayStatsService.AggregatedCycleStats? = null

        init {
            gridLabelRenderer.gridColor = Color.parseColor("#404040")
            gridLabelRenderer.horizontalLabelsColor = Color.parseColor("#CCCCCC")
            gridLabelRenderer.verticalLabelsColor = Color.parseColor("#CCCCCC")
            gridLabelRenderer.labelVerticalWidth = 60
            gridLabelRenderer.gridStyle = GridLabelRenderer.GridStyle.HORIZONTAL
            viewport.isXAxisBoundsManual = true
            viewport.isYAxisBoundsManual = false

            val nf = NumberFormat.getInstance()
            nf.maximumFractionDigits = 1
            gridLabelRenderer.labelFormatter =
                object : DefaultLabelFormatter(nf, nf) {
                    override fun formatLabel(
                        value: Double,
                        isValueX: Boolean,
                    ): String =
                        if (isValueX) {
                            value.toInt().toString()
                        } else {
                            super.formatLabel(value, isValueX)
                        }
                }
        }

        fun setStats(stats: CycleDayStatsService.AggregatedCycleStats) {
            this.stats = stats
            renderGraph()
        }

        fun setMode(mode: GraphMode) {
            currentMode = mode
            renderGraph()
        }

        private fun renderGraph() {
            val stats = stats ?: return
            removeAllSeries()

            if (stats.statsByDay.isEmpty()) return

            val points = mutableListOf<DataPoint>()
            var maxY = 0.0
            var minY = Double.MAX_VALUE

            for (dayStat in stats.statsByDay) {
                val value =
                    when (currentMode) {
                        GraphMode.TDD_TOTAL -> dayStat.tddTotal
                        GraphMode.TDD_BASAL -> dayStat.tddBasal
                        GraphMode.TDD_BOLUS -> dayStat.tddBolus
                        GraphMode.TDD_CARBS -> dayStat.tddCarbs
                        GraphMode.ISF -> dayStat.isfMgdl
                        GraphMode.IC -> dayStat.ic
                    }

                if (value != null && value > 0) {
                    points.add(DataPoint(dayStat.cycleDay.toDouble(), value))
                    maxY = maxOf(maxY, value)
                    minY = minOf(minY, value)
                }
            }

            if (points.isEmpty()) return

            val dataPoints = points.toTypedArray()
            val series = LineGraphSeries(dataPoints)
            series.color = modeColors[currentMode] ?: Color.WHITE
            series.thickness = 6
            series.isDrawDataPoints = true
            series.dataPointsRadius = 6f
            addSeries(series)

            viewport.setMinX(1.0)
            viewport.setMaxX(maxOf(stats.maxCycleDay.toDouble(), 28.0))
            viewport.setMinY(0.0)
            viewport.setMaxY(ceil(maxY * 1.1))

            gridLabelRenderer.numHorizontalLabels = minOf(stats.maxCycleDay + 1, 15)
            title = getModeTitle()
        }

        private fun getModeTitle(): String =
            when (currentMode) {
                GraphMode.TDD_TOTAL -> context.getString(R.string.graph_tdd_total)
                GraphMode.TDD_BASAL -> context.getString(R.string.graph_tdd_basal)
                GraphMode.TDD_BOLUS -> context.getString(R.string.graph_tdd_bolus)
                GraphMode.TDD_CARBS -> context.getString(R.string.graph_tdd_carbs)
                GraphMode.ISF -> context.getString(R.string.graph_isf)
                GraphMode.IC -> context.getString(R.string.graph_ic)
            }
    }
