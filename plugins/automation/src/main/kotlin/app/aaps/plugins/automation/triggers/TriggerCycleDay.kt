package app.aaps.plugins.automation.triggers

import android.widget.LinearLayout
import app.aaps.core.interfaces.periodcalendar.PeriodCalendar
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputDouble
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import app.aaps.plugins.automation.elements.StaticLabel
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.Optional

class TriggerCycleDay(injector: HasAndroidInjector) : Trigger(injector) {

    var day = InputDouble(1.0, 1.0, 99.0, 1.0, DecimalFormat("0"))

    constructor(injector: HasAndroidInjector, triggerCycleDay: TriggerCycleDay) : this(injector) {
        day = InputDouble(triggerCycleDay.day)
    }

    fun day(day: Int): TriggerCycleDay {
        this.day.value = day.toDouble()
        return this
    }

    // Edge-detection: fire once per entry into the target cycle day.
    // Null = armed; non-null = already fired for that day number.
    // In-memory — at most one duplicate fire after app restart is acceptable.
    private var firedForDay: Int? = null

    override fun shouldRun(): Boolean {
        val plugin = activePlugin.getSpecificPluginsListByInterface(PeriodCalendar::class.java).firstOrNull()
        if (plugin == null || !(plugin as PluginBase).isEnabled()) return false
        val calendar = plugin as PeriodCalendar

        val currentDay = calendar.getCurrentCycleDay()
        val targetDay = day.value.toInt()

        if (currentDay <= 0 || currentDay != targetDay) {
            // Left the target day — re-arm for the next entry.
            if (firedForDay == targetDay) firedForDay = null
            return false
        }

        // In the target day: fire only on the first observation (edge).
        if (firedForDay == targetDay) return false
        firedForDay = targetDay
        return true
    }

    override fun dataJSON(): JSONObject =
        JSONObject().put("day", day.value.toInt())

    override fun fromJSON(data: String): Trigger {
        val o = JSONObject(data)
        day.value = o.optInt("day", 1).toDouble()
        return this
    }

    override fun friendlyName(): Int = R.string.cycle_day_trigger

    override fun friendlyDescription(): String =
        rh.gs(R.string.cycle_day_trigger_desc, day.value.toInt())

    override fun icon(): Optional<Int> = Optional.of(app.aaps.core.objects.R.drawable.ic_access_alarm_24dp)

    override fun duplicate(): Trigger = TriggerCycleDay(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.cycle_day_trigger, this))
            .add(LabelWithElement(rh, rh.gs(R.string.day_label), "", day))
            .build(root)
    }
}
