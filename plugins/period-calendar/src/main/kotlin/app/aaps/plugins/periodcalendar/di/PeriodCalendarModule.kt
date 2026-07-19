package app.aaps.plugins.periodcalendar.di

import app.aaps.plugins.periodcalendar.PeriodCalendarPlugin
import app.aaps.plugins.periodcalendar.ui.PeriodCalendarFragment
import app.aaps.plugins.periodcalendar.ui.PhaseConfigDialog
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class PeriodCalendarModule {
    @ContributesAndroidInjector
    abstract fun contributesPeriodCalendarFragment(): PeriodCalendarFragment

    @ContributesAndroidInjector
    abstract fun contributesPhaseConfigDialog(): PhaseConfigDialog

    @Binds
    abstract fun bindPeriodCalendarPlugin(plugin: PeriodCalendarPlugin): app.aaps.core.interfaces.plugin.PluginBase
}
