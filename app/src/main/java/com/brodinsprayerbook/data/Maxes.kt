package com.brodinsprayerbook.data

/** The one place a one-rep max is changed, so every change lands in the history. */
object Maxes {
    fun set(dao: PrayerBookDao, exerciseId: Long, oneRepMax: Double, date: String, source: String) {
        val existing = dao.maxFor(exerciseId)
        if (existing != null && existing.oneRepMax == oneRepMax) return
        dao.upsertMax(UserMax(
            id = existing?.id ?: 0, exerciseId = exerciseId, oneRepMax = oneRepMax,
            trainingMaxPct = existing?.trainingMaxPct ?: 0.90, updatedDate = date
        ))
        dao.insertMaxHistory(MaxHistory(exerciseId = exerciseId, date = date, oneRepMax = oneRepMax, source = source))
    }
}
