package com.jonaswanke.calendar

import android.content.Context
import android.graphics.Canvas
import android.text.format.DateUtils
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import java.util.*
import kotlin.properties.Delegates

/**
 * TODO: document your custom view class.
 */
class WeekView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    _week: Week? = null
) : LinearLayout(context, attrs, defStyleAttr) {
    companion object {
        internal fun showAsAllDay(event: Event) = event.allDay || event.end - event.start >= DateUtils.DAY_IN_MILLIS
    }

    var onEventClickListener: ((Event) -> Unit)?
            by Delegates.observable<((Event) -> Unit)?>(null) { _, _, new ->
                updateListeners(new, onEventLongClickListener, onAddEventListener)
            }
    var onEventLongClickListener: ((Event) -> Unit)?
            by Delegates.observable<((Event) -> Unit)?>(null) { _, _, new ->
                updateListeners(onEventClickListener, new, onAddEventListener)
            }
    internal var onAddEventViewListener: ((AddEvent) -> Unit)? = null
    var onAddEventListener: ((AddEvent) -> Boolean)?
            by Delegates.observable<((AddEvent) -> Boolean)?>(null) { _, _, new ->
                updateListeners(onEventClickListener, onEventLongClickListener, new)
            }
    var onHeaderHeightChangeListener: ((Int) -> Unit)? = null
    var onScrollChangeListener: ((Int) -> Unit)?
        get() = scrollView.onScrollChangeListener
        set(value) {
            scrollView.onScrollChangeListener = value
        }

    var week: Week = _week ?: Week()
        private set
    private val range: Pair<Day, Day>
        get() {
            return Day(week.year, week.week, cal.firstDayOfWeek) to
                    Day(week.nextWeek.year, week.nextWeek.week, cal.firstDayOfWeek)
        }
    private var _events: List<Event> = emptyList()
    var events: List<Event>
        get() = _events
        set(value) {
            checkEvents(value)
            val sortedEvents = value.sorted()

            allDayEventsView.setEvents(sortedEvents.filter { showAsAllDay(it) })

            val byDays = distributeEvents(sortedEvents.filter { !showAsAllDay(it) })
            for (day in WEEK_DAYS)
                dayViews[day].setEvents(byDays[day])
        }

    private var cal: Calendar

    var headerHeight: Int = 0
        private set
    var hourHeight: Float
        get() = dayViews[0].hourHeight
        set(value) {
            for (day in dayViews)
                day.hourHeight = value
        }
    var hourHeightMin: Float
        get() = dayViews[0].hourHeightMin
        set(value) {
            for (day in dayViews)
                day.hourHeightMin = value
        }
    var hourHeightMax: Float
        get() = dayViews[0].hourHeightMax
        set(value) {
            for (day in dayViews)
                day.hourHeightMax = value
        }

    private val headerView: WeekHeaderView
    private val allDayEventsView: AllDayEventsView
    private val scrollView: ReportingScrollView
    private val dayViews: List<DayView>

    init {
        orientation = VERTICAL
        dividerDrawable = ContextCompat.getDrawable(context, android.R.drawable.divider_horizontal_bright)
        setWillNotDraw(false)

        cal = week.toCalendar()

        headerView = WeekHeaderView(context, _week = week)
        addView(headerView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val range = range
        allDayEventsView = AllDayEventsView(context, _start = range.first, _end = range.second)
        addView(allDayEventsView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val dividerView = View(context).apply {
            setBackgroundResource(android.R.drawable.divider_horizontal_bright)
        }
        addView(dividerView, LayoutParams(LayoutParams.MATCH_PARENT, dividerView.background.intrinsicHeight))

        dayViews = WEEK_DAYS.map {
            DayView(context, _day = Day(week, mapBackDay(it))).also {
                it.onEventClickListener = onEventClickListener
                it.onEventLongClickListener = onEventLongClickListener
            }
        }
        dayViews.forEach {
            it.onAddEventViewListener = { event ->
                for (view in dayViews)
                    if (view != it)
                        view.removeAddEvent()
                onAddEventViewListener?.invoke(event)
            }
        }
        val daysWrapper = LinearLayout(context).apply {
            clipChildren = false
            for (day in dayViews)
                addView(day, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        scrollView = ReportingScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(daysWrapper, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        addView(scrollView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)

        val headerHeightNew = headerView.measuredHeight + allDayEventsView.measuredHeight
        if (headerHeight != headerHeightNew) {
            headerHeight = headerHeightNew
            onHeaderHeightChangeListener?.invoke(headerHeight)
        }
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if (canvas == null)
            return

        val left = paddingLeft
        val top = paddingTop
        val right = width - paddingRight
        val bottom = height - paddingBottom

        val dayWidth = (right.toFloat() - left) / WEEK_IN_DAYS
        dividerDrawable?.also { divider ->
            for (day in WEEK_DAYS) {
                divider.setBounds((left + dayWidth * day).toInt(), top,
                        (left + dayWidth * day + divider.intrinsicWidth).toInt(), bottom)
                divider.draw(canvas)
            }
        }
    }


    fun setWeek(week: Week, events: List<Event> = emptyList()) {
        this.week = week
        cal = week.toCalendar()

        removeAddEvent()
        checkEvents(events)
        val sortedEvents = events.sorted()
        headerView.week = week

        val range = range
        allDayEventsView.setRange(range.first, range.second, sortedEvents.filter { showAsAllDay(it) })

        val byDays = distributeEvents(sortedEvents.filter { !showAsAllDay(it) })
        for (day in WEEK_DAYS)
            dayViews[day].setDay(Day(week, mapBackDay(day)), byDays[day])
        this.events = sortedEvents
    }

    fun scrollTo(pos: Int) {
        scrollView.scrollY = pos
    }

    fun removeAddEvent() {
        for (view in dayViews)
            view.removeAddEvent()
    }


    private fun checkEvents(events: List<Event>) {
        if (events.any { event -> event.end < week.start || event.start >= week.end })
            throw IllegalArgumentException("event starts must all be inside the set week")
    }

    /**
     * Maps a [Calendar] weekday ([Calendar.SUNDAY] through [Calendar.SATURDAY]) to the index of that day.
     */
    private fun mapDay(day: Int): Int = (day + WEEK_IN_DAYS - cal.firstDayOfWeek) % WEEK_IN_DAYS

    /**
     * Maps the index of a day back to the [Calendar] weekday ([Calendar.SUNDAY] through [Calendar.SATURDAY]).
     */
    private fun mapBackDay(day: Int): Int = (day + cal.firstDayOfWeek) % WEEK_IN_DAYS

    private fun updateListeners(
        onEventClickListener: ((Event) -> Unit)?,
        onEventLongClickListener: ((Event) -> Unit)?,
        onAddEventListener: ((AddEvent) -> Boolean)?
    ) {
        allDayEventsView.onEventClickListener = onEventClickListener
        allDayEventsView.onEventLongClickListener = onEventLongClickListener
        for (view in dayViews) {
            view.onEventClickListener = onEventClickListener
            view.onEventLongClickListener = onEventLongClickListener
            view.onAddEventListener = onAddEventListener
        }
    }

    private fun distributeEvents(events: List<Event>): List<List<Event>> {
        val days = WEEK_DAYS.map { mutableListOf<Event>() }

        for (event in events) {
            val start = cal.daysUntil(event.start).coerceAtLeast(0)
            val end = cal.daysUntil(event.end).coerceIn(start until WEEK_IN_DAYS)
            for (day in start..end)
                days[day] += event
        }

        return days
    }
}
