package app.aaps.plugins.periodcalendar.ui

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.setFragmentResult
import app.aaps.plugins.periodcalendar.R
import app.aaps.plugins.periodcalendar.model.CyclePhase
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.android.support.DaggerDialogFragment
import javax.inject.Inject

class PhaseConfigDialog : DaggerDialogFragment() {
    @Inject lateinit var aapsLogger: app.aaps.core.interfaces.logging.AAPSLogger

    private var selectedColor: Int = Color.parseColor("#E57373")

    var onPhaseConfigured: ((Bundle) -> Unit)? = null

    companion object {
        private const val ARG_PHASE_DAY_FROM = "day_from"
        private const val ARG_PHASE_DAY_TO = "day_to"
        private const val ARG_PHASE_LABEL = "label"
        private const val ARG_PHASE_COLOR = "color"
        private const val ARG_IS_EDITING = "is_editing"

        const val RESULT_KEY = "phase_config_result"
        const val RESULT_PHASE_DAY_FROM = "phase_day_from"
        const val RESULT_PHASE_DAY_TO = "phase_day_to"
        const val RESULT_PHASE_LABEL = "phase_label"
        const val RESULT_PHASE_COLOR = "phase_color"
        const val RESULT_IS_NEW = "is_new"
        const val RESULT_OLD_DAY_FROM = "old_day_from"
        const val RESULT_OLD_DAY_TO = "old_day_to"
        const val RESULT_OLD_LABEL = "old_label"

        private val PRESET_COLORS =
            intArrayOf(
                Color.parseColor("#E57373"),
                Color.parseColor("#64B5F6"),
                Color.parseColor("#81C784"),
                Color.parseColor("#FFD54F"),
                Color.parseColor("#BA68C8"),
                Color.parseColor("#4DB6AC"),
                Color.parseColor("#FF8A65"),
                Color.parseColor("#9575CD"),
                Color.parseColor("#F06292"),
                Color.parseColor("#4FC3F7"),
            )

        fun newInstance(phase: CyclePhase? = null): PhaseConfigDialog {
            val dialog = PhaseConfigDialog()
            val args = Bundle()
            if (phase != null) {
                args.putInt(ARG_PHASE_DAY_FROM, phase.dayFrom)
                args.putInt(ARG_PHASE_DAY_TO, phase.dayTo)
                args.putString(ARG_PHASE_LABEL, phase.label)
                args.putString(ARG_PHASE_COLOR, phase.colorHex)
                args.putBoolean(ARG_IS_EDITING, true)
            } else {
                args.putBoolean(ARG_IS_EDITING, false)
            }
            dialog.arguments = args
            return dialog
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(context).inflate(R.layout.phase_config_dialog, null)

        val labelInput = view.findViewById<EditText>(R.id.phase_label_input)
        val dayFromInput = view.findViewById<EditText>(R.id.phase_day_from_input)
        val dayToInput = view.findViewById<EditText>(R.id.phase_day_to_input)
        val colorPreview = view.findViewById<android.view.View>(R.id.color_preview)
        val btnPickColor = view.findViewById<MaterialButton>(R.id.btn_pick_color)
        val btnSave = view.findViewById<MaterialButton>(R.id.btn_save)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btn_cancel)
        val dialogTitle = view.findViewById<TextView>(R.id.dialog_title)

        val args = arguments
        val isEditing = args?.getBoolean(ARG_IS_EDITING, false) ?: false

        if (isEditing) {
            dialogTitle.text = getString(R.string.edit_phase)
            labelInput.setText(args?.getString(ARG_PHASE_LABEL))
            dayFromInput.setText(args?.getInt(ARG_PHASE_DAY_FROM)?.toString())
            dayToInput.setText(args?.getInt(ARG_PHASE_DAY_TO)?.toString())
            val colorHex = args?.getString(ARG_PHASE_COLOR) ?: "#E57373"
            selectedColor = Color.parseColor(colorHex)
        } else {
            dialogTitle.text = getString(R.string.add_phase)
            selectedColor = Color.parseColor("#E57373")
        }

        colorPreview.setBackgroundColor(selectedColor)

        btnPickColor.setOnClickListener { showColorPickerDialog(colorPreview) }

        btnSave.setOnClickListener {
            val label = labelInput.text.toString().trim()
            val dayFrom = dayFromInput.text.toString().toIntOrNull()
            val dayTo = dayToInput.text.toString().toIntOrNull()

            if (label.isEmpty() || dayFrom == null || dayTo == null) {
                return@setOnClickListener
            }

            val clampedFrom = dayFrom.coerceIn(1, 99)
            val clampedTo = dayTo.coerceIn(clampedFrom, 99)
            val colorHex = String.format("#%06X", 0xFFFFFF and selectedColor)

            val resultBundle =
                Bundle().apply {
                    putInt(RESULT_PHASE_DAY_FROM, clampedFrom)
                    putInt(RESULT_PHASE_DAY_TO, clampedTo)
                    putString(RESULT_PHASE_LABEL, label)
                    putString(RESULT_PHASE_COLOR, colorHex)
                    putBoolean(RESULT_IS_NEW, !isEditing)
                    if (isEditing) {
                        putInt(RESULT_OLD_DAY_FROM, args?.getInt(ARG_PHASE_DAY_FROM) ?: clampedFrom)
                        putInt(RESULT_OLD_DAY_TO, args?.getInt(ARG_PHASE_DAY_TO) ?: clampedTo)
                        putString(RESULT_OLD_LABEL, args?.getString(ARG_PHASE_LABEL) ?: label)
                    }
                }

            aapsLogger.debug(
                app.aaps.core.interfaces.logging.LTag.CORE,
                "PhaseConfigDialog: Sending result with key=$RESULT_KEY, label=$label, dayFrom=$clampedFrom, dayTo=$clampedTo, isNew=${!isEditing}",
            )

            // Try direct callback first, then fall back to Fragment result API
            try {
                onPhaseConfigured?.invoke(resultBundle)
                aapsLogger.debug(app.aaps.core.interfaces.logging.LTag.CORE, "PhaseConfigDialog: Result sent via callback")
            } catch (e: Exception) {
                aapsLogger.error(app.aaps.core.interfaces.logging.LTag.CORE, "PhaseConfigDialog: Error in callback, using setFragmentResult", e)
                try {
                    val targetFm = parentFragmentManager
                    targetFm.setFragmentResult(RESULT_KEY, resultBundle)
                } catch (e2: Exception) {
                    aapsLogger.error(app.aaps.core.interfaces.logging.LTag.CORE, "PhaseConfigDialog: Error in setFragmentResult", e2)
                }
            }
            dismiss()
        }

        btnCancel.setOnClickListener { dismiss() }

        return AlertDialog
            .Builder(requireContext())
            .setView(view)
            .create()
    }

    private fun showColorPickerDialog(colorPreview: android.view.View) {
        val colorNames = arrayOf("Red", "Blue", "Green", "Yellow", "Purple", "Teal", "Orange", "Deep Purple", "Pink", "Light Blue")
        val selectedIdx = PRESET_COLORS.indexOfFirst { it == selectedColor }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.phase_color)
            .setSingleChoiceItems(colorNames, selectedIdx) { dialog, which ->
                selectedColor = PRESET_COLORS[which]
                colorPreview.setBackgroundColor(selectedColor)
                dialog.dismiss()
            }.show()
    }
}
