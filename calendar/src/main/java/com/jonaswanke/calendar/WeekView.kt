package com.jonaswanke.calendar

import android.content.Context
import android.support.annotation.AttrRes
import android.support.v4.content.ContextCompat
import android.text.format.DateUtils
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.LinearLayout
import java.util.*
import kotlin.properties.Delegates

/**
 * TODO: document your custom view class.
 */
class WeekView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, @AttrRes defStyleAttr: Int = 0)
    : LinearLayout(context, attrs, defStyleAttr) {

    var onEventClickListener: ((String) -> Unit)? = null
    var onEventLongClickListener: ((String) -> Unit)? = null

    var week: Week by Delegates.observable(Week()) { _, old, new ->
        if (old == new)
            return@observable

        for (i in 0..6)
            (getChildAt(i) as DayView).day = Day(week, mapBackDay(i))
        events = emptyList()
    }
    val start: Long
        get() = week.start
    val end: Long
        get() = week.start + DateUtils.WEEK_IN_MILLIS
    var events: List<Event> by Delegates.observable(emptyList()) { _, old, new ->
        if (old == new)
            return@observable
        if (new.any { event -> event.start < start || event.start >= end })
            throw IllegalArgumentException("event starts must all be inside the set week")

        val eventsForDays = (0 until 7).map { mutableListOf<Event>() }
        for (event in events)
            eventsForDays[mapDay(event.start.asCalendar().toDay().day)].add(event)
        for (day in 0 until 7)
            (getChildAt(day) as DayView).events = eventsForDays[day]
    }

    init {
        orientation = HORIZONTAL
        dividerDrawable = ContextCompat.getDrawable(context, android.R.drawable.divider_horizontal_bright)
        showDividers = SHOW_DIVIDER_BEGINNING or SHOW_DIVIDER_MIDDLE


        for (i in 0..6) {
            addView(DayView(context).apply {
                day = Day(week, mapBackDay(i))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    /**
     * Maps a [Calendar] weekday ([Calendar.SUNDAY] through [Calendar.SATURDAY]) to the index of that day.
     */
    private fun mapDay(day: Int): Int = (day + 7 - Calendar.MONDAY) % 7

    /**
     * Maps the index of a day back to the [Calendar] weekday ([Calendar.SUNDAY] through [Calendar.SATURDAY]).
     */
    private fun mapBackDay(day: Int): Int = (day + Calendar.MONDAY) % 7
}
