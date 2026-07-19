package app.aaps.plugins.automation.triggers

import android.widget.LinearLayout
import app.aaps.core.interfaces.periodcalendar.PeriodCalendar
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputDropdownMenu
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import app.aaps.plugins.automation.elements.StaticLabel
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import java.util.Optional

class TriggerCyclePhase(injector: HasAndroidInjector) : Trigger(injector) {

    var phaseLabel: String = "ME"

    constructor(injector: HasAndroidInjector, triggerCyclePhase: TriggerCyclePhase) : this(injector) {
        phaseLabel = triggerCyclePhase.phaseLabel
    }

    fun phaseLabel(label: String): TriggerCyclePhase {
        this.phaseLabel = label
        return this
    }

    // Edge-detection: fire once per entry into the target phase.
    // Null = armed; non-null = already fired for that phase label.
    // In-memory — at most one duplicate fire after app restart is acceptable.
    private var firedForPhase: String? = null

    private fun getPeriodCalendar(): PeriodCalendar? {
        val plugin = activePlugin.getSpecificPluginsListByInterface(PeriodCalendar::class.java).firstOrNull()
        return if (plugin != null && (plugin as PluginBase).isEnabled()) plugin as PeriodCalendar else null
    }

    override fun shouldRun(): Boolean {
        val calendar = getPeriodCalendar() ?: return false
        val currentPhase = calendar.getCurrentPhaseLabel()

        if (currentPhase != phaseLabel) {
            // Left the target phase — re-arm for the next entry.
            if (firedForPhase == phaseLabel) firedForPhase = null
            return false
        }

        // In the target phase: fire only on the first observation (edge).
        if (firedForPhase == phaseLabel) return false
        firedForPhase = phaseLabel
        return true
    }

    override fun dataJSON(): JSONObject =
        JSONObject().put("phaseLabel", phaseLabel)

    override fun fromJSON(data: String): Trigger {
        val o = JSONObject(data)
        phaseLabel = o.optString("phaseLabel", "ME")
        return this
    }

    override fun friendlyName(): Int = R.string.cycle_phase_trigger

    override fun friendlyDescription(): String =
        rh.gs(R.string.cycle_phase_trigger_desc, phaseLabel)

    override fun icon(): Optional<Int> = Optional.of(app.aaps.core.objects.R.drawable.ic_access_alarm_24dp)

    override fun duplicate(): Trigger = TriggerCyclePhase(injector, this)

    override fun generateDialog(root: LinearLayout) {
        val phases = getPeriodCalendar()?.getPhaseLabels()
            ?: listOf("ME", "FO", "FE", "OV", "L1", "L2")
        val phaseDropdown = InputDropdownMenu(rh, phaseLabel)
        phaseDropdown.setList(ArrayList(phases))
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.cycle_phase_trigger, this))
            .add(LabelWithElement(rh, rh.gs(R.string.phase_label), "", phaseDropdown))
            .build(root)
    }
}
