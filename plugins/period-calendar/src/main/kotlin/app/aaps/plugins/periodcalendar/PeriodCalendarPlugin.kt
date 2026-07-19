package app.aaps.plugins.periodcalendar

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.periodcalendar.PeriodCalendar
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.plugins.periodcalendar.services.CycleCounterService
import app.aaps.plugins.periodcalendar.ui.PeriodCalendarFragment
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeriodCalendarPlugin
    @Inject
    constructor(
        aapsLogger: AAPSLogger,
        rh: ResourceHelper,
        private val cycleCounterService: CycleCounterService,
    ) : PluginBase(
            PluginDescription()
                .mainType(PluginType.GENERAL)
                .fragmentClass(PeriodCalendarFragment::class.java.name)
                .pluginIcon(R.drawable.ic_period_circle)
                .pluginName(R.string.period_calendar)
                .shortName(R.string.period_calendar_short)
                .description(R.string.description_period_calendar)
                .enableByDefault(false)
                .visibleByDefault(false),
            aapsLogger,
            rh,
        ),
        PeriodCalendar {

    override fun getCycleDayText(): String = cycleCounterService.getFormattedDisplay()

    override fun getCurrentCycleDay(): Int = cycleCounterService.getCurrentCycleDay()

    override fun getCurrentPhaseLabel(): String? = cycleCounterService.getCurrentPhase()?.label

    override fun getPhaseLabels(): List<String> = cycleCounterService.phases.map { it.label }
}
