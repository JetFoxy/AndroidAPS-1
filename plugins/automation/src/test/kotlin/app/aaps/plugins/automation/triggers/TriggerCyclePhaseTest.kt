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

class TriggerCyclePhaseTest : TriggerTestBase() {

    private lateinit var mockPeriodCalendar: PluginBase

    @BeforeEach
    fun prepareMocks() {
        mockPeriodCalendar = mock(extraInterfaces = arrayOf(PeriodCalendar::class))
        whenever(rh.gs(R.string.cycle_phase_trigger)).thenReturn("Cycle Phase")
        whenever(rh.gs(R.string.cycle_phase_trigger_desc)).thenReturn("Phase = %1\$s")
    }

    private fun setupPlugin(enabled: Boolean) {
        whenever(mockPeriodCalendar.isEnabled()).thenReturn(enabled)
        val list = arrayListOf(mockPeriodCalendar)
        whenever(activePlugin.getSpecificPluginsListByInterface(PeriodCalendar::class.java)).thenReturn(list)
    }

    private fun setupCurrentPhase(label: String?) {
        whenever((mockPeriodCalendar as PeriodCalendar).getCurrentPhaseLabel()).thenReturn(label)
    }

    @Test
    fun `shouldRun returns false when plugin is disabled`() {
        setupPlugin(enabled = false)
        setupCurrentPhase("ME")

        val trigger = TriggerCyclePhase(injector).phaseLabel("ME")
        assertThat(trigger.shouldRun()).isFalse()
    }

    @Test
    fun `shouldRun returns false when plugin is not registered`() {
        whenever(activePlugin.getSpecificPluginsListByInterface(PeriodCalendar::class.java)).thenReturn(arrayListOf())

        val trigger = TriggerCyclePhase(injector).phaseLabel("ME")
        assertThat(trigger.shouldRun()).isFalse()
    }

    @Test
    fun `shouldRun returns false when cycle is not started`() {
        setupPlugin(enabled = true)
        setupCurrentPhase(null)

        val trigger = TriggerCyclePhase(injector).phaseLabel("ME")
        assertThat(trigger.shouldRun()).isFalse()
    }

    @Test
    fun `shouldRun returns false when current phase does not match`() {
        setupPlugin(enabled = true)
        setupCurrentPhase("FO")

        val trigger = TriggerCyclePhase(injector).phaseLabel("ME")
        assertThat(trigger.shouldRun()).isFalse()
    }

    @Test
    fun `shouldRun returns true when current phase matches`() {
        setupPlugin(enabled = true)
        setupCurrentPhase("ME")

        val trigger = TriggerCyclePhase(injector).phaseLabel("ME")
        assertThat(trigger.shouldRun()).isTrue()
    }

    @Test
    fun `shouldRun returns true for FO phase`() {
        setupPlugin(enabled = true)
        setupCurrentPhase("FO")

        val trigger = TriggerCyclePhase(injector).phaseLabel("FO")
        assertThat(trigger.shouldRun()).isTrue()
    }

    @Test
    fun `shouldRun returns false when no phase configured`() {
        setupPlugin(enabled = true)
        setupCurrentPhase(null)

        val trigger = TriggerCyclePhase(injector).phaseLabel("ME")
        assertThat(trigger.shouldRun()).isFalse()
    }

    private val phaseJson = "{\"data\":{\"phaseLabel\":\"FO\"},\"type\":\"TriggerCyclePhase\"}"

    @Test
    fun toJSONTest() {
        val t = TriggerCyclePhase(injector).phaseLabel("FO")
        JSONAssert.assertEquals(phaseJson, t.toJSON(), true)
    }

    @Test
    fun fromJSONTest() {
        val t = TriggerCyclePhase(injector).phaseLabel("OV")
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerCyclePhase
        assertThat(t2.phaseLabel).isEqualTo("OV")
    }

    @Test
    fun copyConstructorTest() {
        val t = TriggerCyclePhase(injector).phaseLabel("L1")
        val t1 = t.duplicate() as TriggerCyclePhase
        assertThat(t1.phaseLabel).isEqualTo("L1")
    }

    @Test
    fun friendlyNameTest() {
        assertThat(TriggerCyclePhase(injector).friendlyName()).isEqualTo(R.string.cycle_phase_trigger)
    }

    @Test
    fun iconTest() {
        assertThat(TriggerCyclePhase(injector).icon().get()).isEqualTo(app.aaps.core.objects.R.drawable.ic_access_alarm_24dp)
    }

    @Test
    fun `shouldRun fires once then suppresses while in same phase`() {
        setupPlugin(enabled = true)
        setupCurrentPhase("ME")
        val trigger = TriggerCyclePhase(injector).phaseLabel("ME")

        // Edge-triggered: fires once on phase entry so a profile-switch action
        // does not overwrite manual percentage adjustments every 5 min.
        assertThat(trigger.shouldRun()).isTrue()
        assertThat(trigger.shouldRun()).isFalse()
        assertThat(trigger.shouldRun()).isFalse()
    }

    @Test
    fun `shouldRun re-arms after leaving phase and re-entering`() {
        setupPlugin(enabled = true)
        val trigger = TriggerCyclePhase(injector).phaseLabel("ME")

        // In ME phase — entry fires
        setupCurrentPhase("ME")
        assertThat(trigger.shouldRun()).isTrue()
        assertThat(trigger.shouldRun()).isFalse()

        // In FO phase — not target, re-arms ME trigger
        setupCurrentPhase("FO")
        assertThat(trigger.shouldRun()).isFalse()

        // Back in ME phase (next cycle) — fires once more
        setupCurrentPhase("ME")
        assertThat(trigger.shouldRun()).isTrue()
    }
}
