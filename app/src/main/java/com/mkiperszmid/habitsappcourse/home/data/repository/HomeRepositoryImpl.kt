package com.mkiperszmid.habitsappcourse.home.data.repository

import com.mkiperszmid.habitsappcourse.home.data.extension.toStartOfDateTimestamp
import com.mkiperszmid.habitsappcourse.home.data.local.HomeDao
import com.mkiperszmid.habitsappcourse.home.data.mapper.toDomain
import com.mkiperszmid.habitsappcourse.home.data.mapper.toDto
import com.mkiperszmid.habitsappcourse.home.data.mapper.toEntity
import com.mkiperszmid.habitsappcourse.home.data.remote.HomeApi
import com.mkiperszmid.habitsappcourse.home.data.remote.util.resultOf
import com.mkiperszmid.habitsappcourse.home.domain.alarm.AlarmHandler
import com.mkiperszmid.habitsappcourse.home.domain.models.Habit
import com.mkiperszmid.habitsappcourse.home.domain.repository.HomeRepository
import kotlinx.coroutines.flow.*
import java.time.ZonedDateTime

class HomeRepositoryImpl(
    private val dao: HomeDao,
    private val api: HomeApi,
    private val alarmHandler: AlarmHandler
) : HomeRepository {
    override fun getAllHabitsForSelectedDate(date: ZonedDateTime): Flow<List<Habit>> {
        val localFlow = dao.getAllHabitsForSelectedDate(date.toStartOfDateTimestamp())
            .map { it.map { it.toDomain() } }
        val apiFlow = getHabitsFromApi()

        return localFlow.combine(apiFlow) { db, _ ->
            db
        }
    }

    private fun getHabitsFromApi(): Flow<List<Habit>> {
        return flow {
            resultOf {
                val habits = api.getAllHabits().toDomain()
                insertHabits(habits)
            }
            emit(emptyList<Habit>())
        }.onStart {
            emit(emptyList())
        }
    }

    override suspend fun insertHabit(habit: Habit) {
        handleAlarm(habit)
        dao.insertHabit(habit.toEntity())
        resultOf {
            api.insertHabit(habit.toDto())
        }
    }

    private suspend fun insertHabits(habits: List<Habit>) {
        habits.forEach {
            handleAlarm(it)
            dao.insertHabit(it.toEntity())
        }
    }

    private suspend fun handleAlarm(habit: Habit) {
        try {
            val previous = dao.getHabitById(habit.id)
            alarmHandler.cancel(previous.toDomain())
        } catch (e: Exception) { /* Habit doesn't exist */
        }
        alarmHandler.setRecurringAlarm(habit)
    }

    override suspend fun getHabitById(id: String): Habit {
        return dao.getHabitById(id).toDomain()
    }
}
