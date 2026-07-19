package app.aaps.plugins.periodcalendar.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.setFragmentResultListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.periodcalendar.PeriodCalendar
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.periodcalendar.R
import app.aaps.plugins.periodcalendar.databinding.PeriodCalendarFragmentBinding
import app.aaps.plugins.periodcalendar.model.CycleInterval
import app.aaps.plugins.periodcalendar.model.CyclePhase
import app.aaps.plugins.periodcalendar.services.CycleCounterService
import app.aaps.plugins.periodcalendar.services.CycleDayStatsService
import app.aaps.plugins.periodcalendar.services.CycleNotificationService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import dagger.android.support.DaggerFragment
import java.util.Calendar
import javax.inject.Inject

class PeriodCalendarFragment : DaggerFragment() {
    @Inject lateinit var aapsLogger: AAPSLogger

    @Inject lateinit var rh: ResourceHelper

    @Inject lateinit var activePlugin: ActivePlugin

    @Inject lateinit var cycleCounterService: CycleCounterService

    @Inject lateinit var cycleDayStatsService: CycleDayStatsService

    @Inject lateinit var cycleNotificationService: CycleNotificationService

    @Inject lateinit var dateUtil: DateUtil

    private var _binding: PeriodCalendarFragmentBinding? = null
    val binding get() = _binding!!

    private var dateChangeReceiver: BroadcastReceiver? = null

    private val monthNames =
        arrayOf(
            "January",
            "February",
            "March",
            "April",
            "May",
            "June",
            "July",
            "August",
            "September",
            "October",
            "November",
            "December",
        )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = PeriodCalendarFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        // Set up listener for phase config dialog results
        aapsLogger.debug(app.aaps.core.interfaces.logging.LTag.CORE, "PeriodCalendarFragment: Setting up listener for ${PhaseConfigDialog.RESULT_KEY}")
        setFragmentResultListener(PhaseConfigDialog.RESULT_KEY) { key, bundle ->
            aapsLogger.debug(app.aaps.core.interfaces.logging.LTag.CORE, "*** LISTENER TRIGGERED *** key=$key, bundle keys: ${bundle.keySet().toList()}")
            try {
                handlePhaseConfigResult(bundle)
                aapsLogger.debug(app.aaps.core.interfaces.logging.LTag.CORE, "PeriodCalendarFragment: handlePhaseConfigResult completed successfully")
            } catch (e: Exception) {
                aapsLogger.error(app.aaps.core.interfaces.logging.LTag.CORE, "PeriodCalendarFragment: Error in handlePhaseConfigResult", e)
            }
        }
        aapsLogger.debug(app.aaps.core.interfaces.logging.LTag.CORE, "PeriodCalendarFragment: Listener setup complete")

        binding.phasesRecyclerview.layoutManager = LinearLayoutManager(context)
        binding.phasesRecyclerview.adapter = PhaseAdapter()

        binding.historyRecyclerview.layoutManager = LinearLayoutManager(context)
        binding.historyRecyclerview.adapter = CycleIntervalAdapter()

        binding.btnResetCycle.setOnClickListener { showResetConfirmation() }
        binding.btnSetDay.setOnClickListener { showSetDayDialog() }
        binding.btnClearHistory.setOnClickListener { showClearHistoryConfirmation() }
        binding.btnAddPhase.setOnClickListener { showPhaseConfigDialog(null) }

        binding.btnPrevMonth.setOnClickListener {
            binding.calendarView.navigateMonth(-1)
            updateMonthLabel()
        }
        binding.btnNextMonth.setOnClickListener {
            binding.calendarView.navigateMonth(1)
            updateMonthLabel()
        }

        binding.calendarView.setCycleDayProvider { timestamp ->
            cycleCounterService.getPredictedCycleDayForDate(timestamp)
        }
        binding.calendarView.setPhaseProvider { timestamp ->
            cycleCounterService.getPhaseForDate(timestamp)
        }

        setupTabs()
        setupGraphModeSelector()
        setupNotificationSwitches()
        registerDateChangeReceiver()

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        val isPluginEnabled = activePlugin.getSpecificPluginsListByInterface(PeriodCalendar::class.java)
            .firstOrNull()?.let { (it as app.aaps.core.interfaces.plugin.PluginBase).isEnabled() } == true
        cycleNotificationService.checkAndNotify(isPluginEnabled)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        unregisterDateChangeReceiver()
        _binding = null
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    when (tab?.position) {
                        0 -> {
                            binding.calendarTabContent.visibility = View.VISIBLE
                            binding.statsTabContent.visibility = View.GONE
                            binding.historyTabContent.visibility = View.GONE
                        }
                        1 -> {
                            binding.calendarTabContent.visibility = View.GONE
                            binding.statsTabContent.visibility = View.VISIBLE
                            binding.historyTabContent.visibility = View.GONE
                            loadStats()
                        }
                        2 -> {
                            binding.calendarTabContent.visibility = View.GONE
                            binding.statsTabContent.visibility = View.GONE
                            binding.historyTabContent.visibility = View.VISIBLE
                            loadHistory()
                        }
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}

                override fun onTabReselected(tab: TabLayout.Tab?) {}
            },
        )
    }

    private fun setupGraphModeSelector() {
        binding.graphModeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val mode =
                    when (checkedIds[0]) {
                        R.id.chip_tdd_total -> CycleDayGraph.GraphMode.TDD_TOTAL
                        R.id.chip_tdd_basal -> CycleDayGraph.GraphMode.TDD_BASAL
                        R.id.chip_tdd_bolus -> CycleDayGraph.GraphMode.TDD_BOLUS
                        R.id.chip_tdd_carbs -> CycleDayGraph.GraphMode.TDD_CARBS
                        R.id.chip_isf -> CycleDayGraph.GraphMode.ISF
                        R.id.chip_ic -> CycleDayGraph.GraphMode.IC
                        else -> CycleDayGraph.GraphMode.TDD_TOTAL
                    }
                binding.cycleDayGraph.setMode(mode)
            }
        }
        binding.chipTddTotal.isChecked = true
    }

    private fun setupNotificationSwitches() {
        cycleNotificationService.createNotificationChannel()

        binding.switchNotifyCycleStart.isChecked = cycleNotificationService.notifyCycleStart
        binding.switchNotifyCycleStart.setOnCheckedChangeListener { _, isChecked ->
            cycleNotificationService.notifyCycleStart = isChecked
        }

        binding.switchNotifyPhaseChange.isChecked = cycleNotificationService.notifyPhaseChange
        binding.switchNotifyPhaseChange.setOnCheckedChangeListener { _, isChecked ->
            cycleNotificationService.notifyPhaseChange = isChecked
        }
    }

    private fun registerDateChangeReceiver() {
        dateChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                        aapsLogger.debug(LTag.CORE, "Date/Time changed - updating cycle day")
                        updateUI()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }

        try {
            context?.registerReceiver(dateChangeReceiver, filter, Context.RECEIVER_EXPORTED)
            aapsLogger.debug(LTag.CORE, "Registered date change receiver")
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "Failed to register date change receiver", e)
        }
    }

    private fun unregisterDateChangeReceiver() {
        if (dateChangeReceiver != null) {
            try {
                context?.unregisterReceiver(dateChangeReceiver)
                aapsLogger.debug(LTag.CORE, "Unregistered date change receiver")
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "Failed to unregister date change receiver", e)
            }
            dateChangeReceiver = null
        }
    }

    private fun loadHistory() {
        val intervals = cycleCounterService.getCycleIntervals()
        (binding.historyRecyclerview.adapter as? CycleIntervalAdapter)?.submit(intervals)
        if (intervals.isEmpty()) {
            binding.historyRecyclerview.visibility = View.GONE
            binding.historyAvgLength.visibility = View.GONE
            binding.historyEmptyText.visibility = View.VISIBLE
        } else {
            binding.historyRecyclerview.visibility = View.VISIBLE
            binding.historyAvgLength.visibility = View.VISIBLE
            binding.historyEmptyText.visibility = View.GONE
            binding.historyAvgLength.text =
                rh.gs(R.string.cycle_avg_length, String.format("%.1f", cycleCounterService.getAverageCycleLength()))
        }
    }

    private fun loadStats() {
        val stats = cycleDayStatsService.getAggregatedStats()
        if (stats.statsByDay.isEmpty()) {
            binding.cycleDayGraph.visibility = View.GONE
            binding.graphNoDataText.visibility = View.VISIBLE
        } else {
            binding.cycleDayGraph.visibility = View.VISIBLE
            binding.graphNoDataText.visibility = View.GONE
            binding.cycleDayGraph.setStats(stats)
        }
    }

    private fun updateMonthLabel() {
        val cal = Calendar.getInstance()
        cal.timeInMillis = binding.calendarView.getCurrentMonthStart()
        binding.calendarMonthLabel.text = "${monthNames[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}"
    }

    private fun updateUI() {
        val day = cycleCounterService.getCurrentCycleDay()
        val phase = cycleCounterService.getCurrentPhase()

        if (day > 0) {
            binding.cycleDayDisplay.text = String.format("%02d", day)
            binding.cyclePhaseDisplay.text = phase?.label ?: ""
        } else {
            binding.cycleDayDisplay.text = rh.gs(R.string.cycle_not_started)
            binding.cyclePhaseDisplay.text = ""
        }

        binding.calendarView.setPredictionInfo(
            cycleCounterService.getPredictedNextStart(),
            cycleCounterService.getAverageCycleLength(),
        )
        binding.calendarView.renderCalendar()
        updateMonthLabel()

        (binding.phasesRecyclerview.adapter as? PhaseAdapter)?.notifyDataSetChanged()

        if (binding.historyTabContent.visibility == View.VISIBLE) loadHistory()
    }

    private fun showResetConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.cycle_reset_button)
            .setMessage(R.string.cycle_reset_confirm)
            .setPositiveButton(R.string.ok) { _, _ ->
                cycleCounterService.resetCycle()
                updateUI()
            }.setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showClearHistoryConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.cycle_clear_history)
            .setMessage(R.string.cycle_clear_history_confirm)
            .setPositiveButton(R.string.ok) { _, _ ->
                cycleCounterService.clearHistory()
                updateUI()
            }.setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSetDayDialog() {
        val view = LayoutInflater.from(context).inflate(R.layout.set_day_dialog, null)
        val input = view.findViewById<EditText>(R.id.set_day_input)
        val errorText = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.set_day_input_layout)

        MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .create()
            .apply {
                setOnShowListener {
                    view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel).setOnClickListener { dismiss() }
                    view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_ok).setOnClickListener {
                        val dayStr = input.text.toString().trim()
                        if (dayStr.isEmpty()) {
                            errorText?.error = rh.gs(R.string.cycle_set_day_empty)
                            return@setOnClickListener
                        }
                        val day = dayStr.toIntOrNull()
                        if (day == null) {
                            errorText?.error = rh.gs(R.string.cycle_set_day_invalid)
                            return@setOnClickListener
                        }
                        if (day < 1 || day > 99) {
                            errorText?.error = rh.gs(R.string.cycle_set_day_out_of_range)
                            return@setOnClickListener
                        }
                        errorText?.error = null
                        cycleCounterService.setCycleDay(day)
                        updateUI()
                        dismiss()
                    }
                }
            }.show()
    }

    private fun showPhaseConfigDialog(phase: CyclePhase?) {
        val dialog = PhaseConfigDialog.newInstance(phase)
        dialog.onPhaseConfigured = { bundle ->
            aapsLogger.debug(app.aaps.core.interfaces.logging.LTag.CORE, "PeriodCalendarFragment: Received phase via callback")
            handlePhaseConfigResult(bundle)
        }
        dialog.show(childFragmentManager, "PhaseConfigDialog")
    }

    private fun handlePhaseConfigResult(bundle: Bundle) {
        aapsLogger.debug(app.aaps.core.interfaces.logging.LTag.CORE, "handlePhaseConfigResult() called")
        val dayFrom = bundle.getInt(PhaseConfigDialog.RESULT_PHASE_DAY_FROM)
        val dayTo = bundle.getInt(PhaseConfigDialog.RESULT_PHASE_DAY_TO)
        val label = bundle.getString(PhaseConfigDialog.RESULT_PHASE_LABEL) ?: run {
            aapsLogger.error(app.aaps.core.interfaces.logging.LTag.CORE, "handlePhaseConfigResult: label is null, returning")
            return
        }
        val colorHex = bundle.getString(PhaseConfigDialog.RESULT_PHASE_COLOR) ?: run {
            aapsLogger.error(app.aaps.core.interfaces.logging.LTag.CORE, "handlePhaseConfigResult: colorHex is null, returning")
            return
        }
        val isNew = bundle.getBoolean(PhaseConfigDialog.RESULT_IS_NEW)

        aapsLogger.debug(
            app.aaps.core.interfaces.logging.LTag.CORE,
            "handlePhaseConfigResult: label=$label, dayFrom=$dayFrom, dayTo=$dayTo, isNew=$isNew, colorHex=$colorHex",
        )

        val newPhase = CyclePhase(dayFrom, dayTo, label, colorHex)
        val currentPhases = cycleCounterService.phases
        aapsLogger.debug(app.aaps.core.interfaces.logging.LTag.CORE, "Read current phases: ${currentPhases.size} items")
        val phases = currentPhases.toMutableList()

        if (isNew) {
            phases.add(newPhase)
            phases.sortBy { it.dayFrom }
            aapsLogger.debug(app.aaps.core.interfaces.logging.LTag.CORE, "Added new phase: $label, total now ${phases.size}")
        } else {
            val oldDayFrom = bundle.getInt(PhaseConfigDialog.RESULT_OLD_DAY_FROM)
            val oldDayTo = bundle.getInt(PhaseConfigDialog.RESULT_OLD_DAY_TO)
            val oldLabel = bundle.getString(PhaseConfigDialog.RESULT_OLD_LABEL) ?: run {
                aapsLogger.error(app.aaps.core.interfaces.logging.LTag.CORE, "handlePhaseConfigResult: oldLabel is null")
                return
            }
            val idx =
                phases.indexOfFirst {
                    it.dayFrom == oldDayFrom && it.dayTo == oldDayTo && it.label == oldLabel
                }
            if (idx >= 0) {
                phases[idx] = newPhase
                aapsLogger.debug(app.aaps.core.interfaces.logging.LTag.CORE, "Updated phase at index $idx: $label")
            } else {
                aapsLogger.warn(app.aaps.core.interfaces.logging.LTag.CORE, "Phase to update not found: $oldLabel")
            }
        }

        aapsLogger.debug(app.aaps.core.interfaces.logging.LTag.CORE, "About to save phases (${phases.size} items) to cycleCounterService")
        cycleCounterService.phases = phases
        aapsLogger.debug(app.aaps.core.interfaces.logging.LTag.CORE, "Phases saved. Reading back: ${cycleCounterService.phases.size} items")
        updateUI()
        aapsLogger.debug(app.aaps.core.interfaces.logging.LTag.CORE, "handlePhaseConfigResult() completed")
    }

    inner class PhaseAdapter : RecyclerView.Adapter<PhaseAdapter.PhaseViewHolder>() {
        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): PhaseViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.phase_item, parent, false)
            return PhaseViewHolder(view)
        }

        override fun onBindViewHolder(
            holder: PhaseViewHolder,
            position: Int,
        ) {
            val phases = cycleCounterService.phases
            aapsLogger.debug(app.aaps.core.interfaces.logging.LTag.CORE, "PhaseAdapter.onBindViewHolder: position=$position, total=${phases.size}")
            val phase = phases[position]

            holder.binding.phaseDayRange.text = "${phase.dayFrom}-${phase.dayTo}"
            holder.binding.phaseLabel.text = phase.label
            holder.binding.phaseColorIndicator.setBackgroundColor(android.graphics.Color.parseColor(phase.colorHex))

            holder.binding.btnEditPhase.setOnClickListener {
                showPhaseConfigDialog(phase)
            }

            holder.binding.btnDeletePhase.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.delete_phase)
                    .setMessage(R.string.delete_phase_confirm)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        val newPhases = phases.toMutableList().apply { removeAt(position) }
                        cycleCounterService.phases = newPhases
                        notifyDataSetChanged()
                    }.setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }

        override fun getItemCount(): Int {
            val count = cycleCounterService.phases.size
            aapsLogger.debug(app.aaps.core.interfaces.logging.LTag.CORE, "PhaseAdapter.getItemCount: $count phases")
            return count
        }

        inner class PhaseViewHolder(
            itemView: View,
        ) : RecyclerView.ViewHolder(itemView) {
            val binding =
                app.aaps.plugins.periodcalendar.databinding.PhaseItemBinding
                    .bind(itemView)
        }
    }

    inner class CycleIntervalAdapter : RecyclerView.Adapter<CycleIntervalAdapter.IntervalViewHolder>() {
        private var intervals: List<CycleInterval> = emptyList()

        fun submit(newIntervals: List<CycleInterval>) {
            intervals = newIntervals
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): IntervalViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.cycle_interval_item, parent, false)
            return IntervalViewHolder(view)
        }

        override fun onBindViewHolder(
            holder: IntervalViewHolder,
            position: Int,
        ) {
            val interval = intervals[position]
            holder.binding.intervalStartDate.text = dateUtil.dateString(interval.startTimestamp)
            holder.binding.intervalLength.text =
                if (interval.ongoing) {
                    rh.gs(R.string.cycle_interval_ongoing, interval.lengthDays)
                } else {
                    rh.gs(R.string.cycle_interval_days, interval.lengthDays)
                }
        }

        override fun getItemCount(): Int = intervals.size

        inner class IntervalViewHolder(
            itemView: View,
        ) : RecyclerView.ViewHolder(itemView) {
            val binding =
                app.aaps.plugins.periodcalendar.databinding.CycleIntervalItemBinding
                    .bind(itemView)
        }
    }
}
