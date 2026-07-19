package app.aaps.plugins.automation.triggers

import app.aaps.core.interfaces.periodcalendar.PeriodCalendar
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.plugins.automation.R
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class TriggerCycleDayTest : TriggerTestBase() {

    private lateinit var mockPeriodCalendar: PluginBase

    @BeforeEach
    fun prepareMocks() {
        mockPeriodCalendar = mock(extraInterfaces = arrayOf(PeriodCalendar::class))
        whenever(rh.gs(R.string.cycle_day_trigger)).thenReturn("Cycle Day")
        whenever(rh.gs(R.string.cycle_day_trigger_desc)).thenReturn("Cycle day = %1\$d")
    }

    private fun setupPlugin(enabled: Boolean) {
        whenever(mockPeriodCalendar.isEnabled()).thenReturn(enabled)
        val list = arrayListOf(mockPeriodCalendar)
        whenever(activePlugin.getSpecificPluginsListByInterface(PeriodCalendar::class.java)).thenReturn(list)
    }

    private fun setupCurrentDay(day: Int) {
        whenever((mockPeriodCalendar as PeriodCalendar).getCurrentCycleDay()).thenReturn(day)
    }

    @Test
    fun `shouldRun returns false when plugin is disabled`() {
        setupPlugin(enabled = false)
        setupCurrentDay(1)

        val trigger = TriggerCycleDay(injector).day(1)
        assertThat(trigger.shouldRun()).isFalse()
    }

    @Test
    fun `shouldRun returns false when plugin is not registered`() {
        whenever(activePlugin.getSpecificPluginsListByInterface(PeriodCalendar::class.java)).thenReturn(arrayListOf())

        val trigger = TriggerCycleDay(injector).day(1)
        assertThat(trigger.shouldRun()).isFalse()
    }

    @Test
    fun `shouldRun returns false when cycle is not started`() {
        setupPlugin(enabled = true)
        setupCurrentDay(0)

        val trigger = TriggerCycleDay(injector).day(1)
        assertThat(trigger.shouldRun()).isFalse()
    }

    @Test
    fun `shouldRun returns false when current day does not match`() {
        setupPlugin(enabled = true)
        setupCurrentDay(3)

        val trigger = TriggerCycleDay(injector).day(1)
        assertThat(trigger.shouldRun()).isFalse()
    }

    @Test
    fun `shouldRun returns true when current day matches`() {
        setupPlugin(enabled = true)
        setupCurrentDay(5)

        val trigger = TriggerCycleDay(injector).day(5)
        assertThat(trigger.shouldRun()).isTrue()
    }

    @Test
    fun `shouldRun returns true for day 1 when cycle just started`() {
        setupPlugin(enabled = true)
        setupCurrentDay(1)

        val trigger = TriggerCycleDay(injector).day(1)
        assertThat(trigger.shouldRun()).isTrue()
    }

    private val dayJson = "{\"data\":{\"day\":5},\"type\":\"TriggerCycleDay\"}"

    @Test
    fun toJSONTest() {
        val t = TriggerCycleDay(injector).day(5)
        JSONAssert.assertEquals(dayJson, t.toJSON(), true)
    }

    @Test
    fun fromJSONTest() {
        val t = TriggerCycleDay(injector).day(5)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerCycleDay
        assertThat(t2.day.value.toInt()).isEqualTo(5)
    }

    @Test
    fun copyConstructorTest() {
        val t = TriggerCycleDay(injector).day(10)
        val t1 = t.duplicate() as TriggerCycleDay
        assertThat(t1.day.value.toInt()).isEqualTo(10)
    }

    @Test
    fun friendlyNameTest() {
        assertThat(TriggerCycleDay(injector).friendlyName()).isEqualTo(R.string.cycle_day_trigger)
    }

    @Test
    fun iconTest() {
        assertThat(TriggerCycleDay(injector).icon().get()).isEqualTo(app.aaps.core.objects.R.drawable.ic_access_alarm_24dp)
    }

    @Test
    fun `shouldRun fires once then suppresses while in same day`() {
        setupPlugin(enabled = true)
        setupCurrentDay(5)
        val trigger = TriggerCycleDay(injector).day(5)

        // Edge-triggered: fires once on entry, stays quiet so a profile-switch
        // action does not overwrite manual percentage adjustments every 5 min.
        assertThat(trigger.shouldRun()).isTrue()
        assertThat(trigger.shouldRun()).isFalse()
        assertThat(trigger.shouldRun()).isFalse()
    }

    @Test
    fun `shouldRun re-arms after day changes and re-enters target`() {
        setupPlugin(enabled = true)
        val trigger = TriggerCycleDay(injector).day(5)

        // Day 5 — entry fires
        setupCurrentDay(5)
        assertThat(trigger.shouldRun()).isTrue()
        assertThat(trigger.shouldRun()).isFalse()

        // Day 6 — not target, re-arms
        setupCurrentDay(6)
        assertThat(trigger.shouldRun()).isFalse()

        // Day 5 again (next cycle) — fires once more
        setupCurrentDay(5)
        assertThat(trigger.shouldRun()).isTrue()
    }
}
