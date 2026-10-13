package com.abdullah.lockapp.data

import com.abdullah.lockapp.core.Clock
import com.abdullah.lockapp.core.DayMath
import com.abdullah.lockapp.data.db.GateEventDao
import com.abdullah.lockapp.data.db.GateEventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class EventType {
    /** The question gate appeared. */
    GATE_SHOWN,

    /** Left the question gate without unlocking. */
    TURNED_BACK,
    UNLOCKED,
    BLOCKED_FOCUS,
    BLOCKED_LIMIT,
    FOCUS_STARTED,
    SERVICE_ON,
    SERVICE_OFF,
    TIME_CHANGED,
}

class EventLog(
    private val dao: GateEventDao,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    fun log(type: EventType, pkg: String? = null, answered: Int = 0, correct: Int = 0) {
        val now = clock.now()
        val event = GateEventEntity(
            timestamp = now,
            day = DayMath.epochDay(now, clock.zone()),
            type = type.name,
            packageName = pkg,
            answered = answered,
            correct = correct,
        )
        scope.launch(Dispatchers.IO) { dao.insert(event) }
    }
}
