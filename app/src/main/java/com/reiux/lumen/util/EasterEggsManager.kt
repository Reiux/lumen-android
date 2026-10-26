package com.reiux.lumen.util

import android.content.Context
import android.widget.Toast
import com.reiux.lumen.R
import com.reiux.lumen.data.repository.SettingsRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class Season {
    NONE,
    HALLOWEEN,
    CHRISTMAS,
    NEW_YEAR
}

object EasterEggsManager {

    fun resolveActiveSeason(forcedSeasonStr: String): Season {
        return when (forcedSeasonStr.uppercase()) {
            "HALLOWEEN" -> Season.HALLOWEEN
            "CHRISTMAS" -> Season.CHRISTMAS
            "NEW_YEAR" -> Season.NEW_YEAR
            "NONE" -> getCalendarSeason()
            else -> getCalendarSeason()
        }
    }

    private fun getCalendarSeason(): Season {
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) + 1 // 1-based (Jan = 1, Oct = 10, Dec = 12)
        val day = cal.get(Calendar.DAY_OF_MONTH)

        return when {
            month == 10 && day in 24..31 -> Season.HALLOWEEN
            month == 12 && day in 20..26 -> Season.CHRISTMAS
            (month == 12 && day == 31) || (month == 1 && day in 1..2) -> Season.NEW_YEAR
            else -> Season.NONE
        }
    }

    fun triggerLightsOffBoldChoice(context: Context, force: Boolean = false) {
        if (force) {
            LumenLogger.i("EasterEgg") { "Triggered 'Bold choice.' Toast (forced)" }
            Toast.makeText(context, context.getString(R.string.toast_bold_choice), Toast.LENGTH_SHORT).show()
            return
        }

        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        if (hour in 0..3) { // 00:00 to 03:59 (between 00:00 and 04:00)
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val repo = SettingsRepository(context)
            if (repo.getBoldChoiceDate() != todayStr) {
                repo.setBoldChoiceDate(todayStr)
                LumenLogger.i("EasterEgg") { "Triggered 'Bold choice.' Toast for late night switch to light theme" }
                Toast.makeText(context, context.getString(R.string.toast_bold_choice), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun getCompletionTitle(context: Context, force: Boolean = false, forcedIndex: Int? = null): String {
        val puns = listOf(
            R.string.completion_pun_1,
            R.string.completion_pun_2,
            R.string.completion_pun_3,
            R.string.completion_pun_4,
            R.string.completion_pun_5
        )

        if (force || kotlin.random.Random.nextInt(20) == 0) {
            val index = forcedIndex ?: kotlin.random.Random.nextInt(puns.size)
            val resId = puns[index.coerceIn(0, puns.size - 1)]
            val punText = context.getString(resId)
            LumenLogger.i("EasterEgg") { "Triggered completion pun: '$punText'" }
            return punText
        }
        return "All Backed Up!"
    }

    fun checkMilestoneToast(context: Context, milestone: Int) {
        val resId = when (milestone) {
            100 -> R.string.toast_milestone_100
            1000 -> R.string.toast_milestone_1000
            10000 -> R.string.toast_milestone_10000
            else -> R.string.toast_milestone_100
        }
        val text = context.getString(resId)
        LumenLogger.i("EasterEgg") { "Triggered milestone $milestone Toast: '$text'" }
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    fun checkLateNightToast(context: Context, force: Boolean = false) {
        if (force) {
            val text = context.getString(R.string.toast_late_night)
            LumenLogger.i("EasterEgg") { "Triggered late night Toast (forced): '$text'" }
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            return
        }

        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        if (hour in 2..4) { // 02:00 to 04:59
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val repo = SettingsRepository(context)
            if (repo.getLateNightDate() != todayStr) {
                repo.setLateNightDate(todayStr)
                val text = context.getString(R.string.toast_late_night)
                LumenLogger.i("EasterEgg") { "Triggered late night Toast: '$text'" }
                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun getEmptyStateSubtitle(context: Context, force: Boolean = false, forcedIndex: Int? = null): String {
        val defaultSubtitle = "Choose photos & videos or tap Sync Folders to start backup"
        val variants = listOf(
            R.string.empty_state_variant_1,
            R.string.empty_state_variant_2,
            R.string.empty_state_variant_3,
            R.string.empty_state_variant_4,
            R.string.empty_state_variant_5
        )

        if (force || kotlin.random.Random.nextInt(20) == 0) {
            val index = forcedIndex ?: kotlin.random.Random.nextInt(variants.size)
            val resId = variants[index.coerceIn(0, variants.size - 1)]
            val variantText = context.getString(resId)
            LumenLogger.i("EasterEgg") { "Triggered empty state variant: '$variantText'" }
            return variantText
        }

        return defaultSubtitle
    }

    fun checkSeasonalGreeting(context: Context, season: Season, force: Boolean = false) {
        if (season == Season.NONE && !force) return

        val targetSeason = if (force && season == Season.NONE) Season.HALLOWEEN else season
        val resId = when (targetSeason) {
            Season.HALLOWEEN -> R.string.seasonal_greeting_halloween
            Season.CHRISTMAS -> R.string.seasonal_greeting_christmas
            Season.NEW_YEAR -> R.string.seasonal_greeting_new_year
            Season.NONE -> R.string.seasonal_greeting_halloween
        }

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val repo = SettingsRepository(context)

        if (targetSeason != Season.NONE) {
            repo.unlockSeasonalTheme(targetSeason.name)
        }

        if (force || !repo.isSeasonalGreetingShown(targetSeason.name, currentYear)) {
            if (!force) {
                repo.setSeasonalGreetingShown(targetSeason.name, currentYear)
            }
            val text = context.getString(resId)
            LumenLogger.i("EasterEgg") { "Triggered seasonal greeting for ${targetSeason.name}: '$text'" }
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }
}
