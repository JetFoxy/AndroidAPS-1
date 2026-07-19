package app.aaps.plugins.periodcalendar.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.aaps.plugins.periodcalendar.R
import app.aaps.plugins.periodcalendar.model.CyclePhase
import java.util.Calendar

class CycleCalendarView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : LinearLayout(context, attrs, defStyleAttr) {
        private val headerRow: LinearLayout
        private val calendarGrid: LinearLayout
        private val predictionText: TextView

        private var currentMonthStart: Calendar = Calendar.getInstance()
        private var cycleDayProvider: ((Long) -> Int)? = null
        private var phaseProvider: ((Long) -> CyclePhase?)? = null
        private var predictedNextStart: Long? = null
        private var avgCycleLength: Double = 28.0

        private val maxFutureCycles = 2

        init {
            currentMonthStart.set(Calendar.DAY_OF_MONTH, 1)
            currentMonthStart.set(Calendar.HOUR_OF_DAY, 0)
            currentMonthStart.set(Calendar.MINUTE, 0)
            currentMonthStart.set(Calendar.SECOND, 0)
            currentMonthStart.set(Calendar.MILLISECOND, 0)

            orientation = VERTICAL

            val inflater = LayoutInflater.from(context)
            val root = inflater.inflate(R.layout.cycle_calendar_view, this, true)

            headerRow = root.findViewById(R.id.calendar_header_row)
            calendarGrid = root.findViewById(R.id.calendar_grid)
            predictionText = root.findViewById(R.id.prediction_text)

            setupHeaderRow()
        }

        private fun setupHeaderRow() {
            val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            headerRow.removeAllViews()
            for (dayName in dayNames) {
                val tv =
                    TextView(context).apply {
                        text = dayName
                        gravity = Gravity.CENTER
                        textSize = 12f
                        setPadding(0, 8, 0, 8)
                        setTextColor(Color.GRAY)
                    }
                val params = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                tv.layoutParams = params
                headerRow.addView(tv)
            }
        }

        fun setCycleDayProvider(provider: (Long) -> Int) {
            cycleDayProvider = provider
        }

        fun setPhaseProvider(provider: (Long) -> CyclePhase?) {
            phaseProvider = provider
        }

        fun setPredictionInfo(
            nextStart: Long?,
            avgLength: Double,
        ) {
            predictedNextStart = nextStart
            avgCycleLength = avgLength
        }

        fun getCurrentMonthStart(): Long = currentMonthStart.timeInMillis

        fun navigateMonth(delta: Int) {
            currentMonthStart.add(Calendar.MONTH, delta)
            renderCalendar()
        }

        fun renderCalendar() {
            calendarGrid.removeAllViews()

            val cal = currentMonthStart.clone() as Calendar
            val month = cal.get(Calendar.MONTH)

            cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek())

            var weekRow: LinearLayout? = null
            var dayCount = 0

            while (true) {
                if (dayCount % 7 == 0) {
                    weekRow =
                        LinearLayout(context).apply {
                            orientation = HORIZONTAL
                            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                        }
                    calendarGrid.addView(weekRow)
                }

                val dayCal = cal.clone() as Calendar
                val dayOfMonth = dayCal.get(Calendar.DAY_OF_MONTH)
                val isCurrentMonth = dayCal.get(Calendar.MONTH) == month

                val dayView = createDayView(dayCal, dayOfMonth, isCurrentMonth)
                weekRow?.addView(dayView)

                dayCount++
                cal.add(Calendar.DAY_OF_MONTH, 1)

                if (dayCount >= 42 && cal.get(Calendar.DAY_OF_MONTH) == 1) break
            }

            updatePredictionText()
        }

        private fun createDayView(
            dayCal: Calendar,
            dayOfMonth: Int,
            isCurrentMonth: Boolean,
        ): TextView {
            val tv =
                TextView(context).apply {
                    text = dayOfMonth.toString()
                    gravity = Gravity.CENTER
                    textSize = 14f
                    setPadding(0, 12, 0, 12)
                    layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                    isEnabled = isCurrentMonth
                }

            if (!isCurrentMonth) {
                tv.setTextColor(Color.LTGRAY)
                return tv
            }

            val dayTimestamp = dayCal.timeInMillis
            val today = Calendar.getInstance()

            val maxFutureMs = avgCycleLength * maxFutureCycles * 24L * 60 * 60 * 1000
            val todayMs = today.timeInMillis
            if (dayTimestamp > todayMs + maxFutureMs) {
                tv.setBackgroundColor(Color.TRANSPARENT)
                tv.setTextColor(Color.BLACK)
                return tv
            }

            val cycleDay = cycleDayProvider?.invoke(dayTimestamp) ?: 0
            val phase = phaseProvider?.invoke(dayTimestamp)

            if (cycleDay > 0 && phase != null) {
                val bgColor =
                    try {
                        Color.parseColor(phase.colorHex)
                    } catch (e: Exception) {
                        Color.TRANSPARENT
                    }
                tv.setBackgroundColor(bgColor.withAlpha(80))
                tv.setTextColor(Color.WHITE)
            } else {
                tv.setBackgroundColor(Color.TRANSPARENT)
                tv.setTextColor(Color.BLACK)
            }

            if (dayCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                dayCal.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                dayCal.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH)
            ) {
                val currentBg = tv.background
                tv.text = "● $dayOfMonth"
                tv.textSize = 12f
            }

            return tv
        }

        private fun Int.withAlpha(alpha: Int): Int = (this and 0x00FFFFFF) or (alpha shl 24)

        private fun updatePredictionText() {
            if (predictedNextStart != null) {
                val cal = Calendar.getInstance()
                cal.timeInMillis = predictedNextStart!!
                val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                val dateStr = "${monthNames[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.DAY_OF_MONTH)}, ${cal.get(Calendar.YEAR)}"
                predictionText.text = "Avg cycle: ${avgCycleLength.toInt()} days | Next predicted: $dateStr"
                predictionText.visibility = View.VISIBLE
            } else {
                predictionText.text = "Log 2+ cycles to see predictions"
                predictionText.visibility = View.VISIBLE
            }
        }
    }
