package com.example.psyhealthapp.core

import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.psyhealthapp.db.DB
import com.example.psyhealthapp.db.DBProvider
import com.example.psyhealthapp.user.testing.results.*
import com.example.psyhealthapp.util.LocalDateTimeAdapter
import com.google.gson.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TestResultsHolder @Inject constructor(
    private val dbProvider: DBProvider
) : ViewModel() {
    private val db: DB by lazy(LazyThreadSafetyMode.NONE) {
        dbProvider.getDB(DB_TAG)
    }

    class Keeper<T : TestResult>(
        private val TEST_TAG: String,
        private val COUNTER_TAG: String,
        val db: DB,
        val getResult: (String) -> T?
    ) : ViewModel() {
        private var counter = INVALID_COUNTER
        private var initialized = false
        private val pendingList = mutableListOf<T>()
        private var resultList: MutableList<T>? = null
        private val resultsByDay = ResultsByDay(sortedMapOf())

        fun getResultsByDay(): ResultsByDay {
            return resultsByDay
        }

        fun getTestResults(): MutableList<T> {
            return resultList ?: pendingList
        }

        fun putTestResult(result: T) {
            resultsByDay.addResult(result.date)
            if (!initialized) {
                pendingList.add(result)
                return
            }
            resultList?.add(result)
            saveTestResultsToDB()
        }

        private fun saveTestResultsToDB() {
            resultList?.let {
                for ((localCounter, result) in it.withIndex()) {
                    viewModelScope.launch {
                        db.putStringsAsync(
                            listOf(
                                Pair(TEST_TAG + localCounter, gson.toJson(result))
                            )
                        )
                    }
                }
                counter = it.size
                viewModelScope.launch {
                    db.putStringsAsync(
                        listOf(
                            Pair(COUNTER_TAG, counter.toString())
                        )
                    )
                }
            }
        }

        fun initialize() {
            if (initialized) {
                return
            }

            viewModelScope.launch {
                counter = db.getString(COUNTER_TAG)?.toInt() ?: INVALID_COUNTER
                val server = mutableListOf<T>()
                for (i in 0 until counter) {
                    val result = getResult(TEST_TAG + i)
                    result?.let {
                        server.add(result)
                    }
                }

                handler.post {
                    resultList = if (server.size != 0) {
                        pendingList.forEach {
                            server.add(it)
                        }
                        server
                    } else {
                        pendingList
                    }
                    if (server.size != 0) {
                        server.forEach {
                            resultsByDay.addResult(it.date)
                        }
                    }
                    saveTestResultsToDB()
                    initialized = true
                }
            }
        }
    }

    private val reactionTestResultsKeeper = Keeper<ReactionTestResult>(
        REACTION_TAG,
        REACTION_COUNTER_TAG,
        db,
        {
            db.getParcelable(it, ReactionTestResult::class.java, gson)
        }
    )

    private val complexReactionTestResultsKeeper = Keeper<ReactionTestResult>(
        COMPLEX_REACTION_TAG,
        COMPLEX_REACTION_COUNTER_TAG,
        db,
        {
            db.getParcelable(it, ReactionTestResult::class.java, gson)
        }
    )

    private val movingReactionTestResultKeeper = Keeper<MovingReactionTestResult>(
        MOVING_TAG,
        MOVING_COUNTER_TAG,
        db,
        {
            db.getParcelable(it, MovingReactionTestResult::class.java, gson)
        }
    )

    private val tappingTestResultsKeeper = Keeper<TappingTestResult>(
        TAPPING_TAG,
        TAPPING_COUNTER_TAG,
        db,
        {
            db.getParcelable(it, TappingTestResult::class.java, gson)
        }
    )

    fun initialize() {
        movingReactionTestResultKeeper.initialize()
        reactionTestResultsKeeper.initialize()
        complexReactionTestResultsKeeper.initialize()
        tappingTestResultsKeeper.initialize()
    }

    fun putMovingReactionTestResult(result: MovingReactionTestResult) {
        movingReactionTestResultKeeper.putTestResult(result)
    }

    fun getMovingReactionTestResults(): MovingReactionTestResultList {
        return MovingReactionTestResultList(movingReactionTestResultKeeper.getTestResults())
    }

    fun putReactionTestResult(result: ReactionTestResult) {
        reactionTestResultsKeeper.putTestResult(result)
    }

    fun getReactionTestResults(): ReactionTestResultList {
        return ReactionTestResultList(reactionTestResultsKeeper.getTestResults())
    }

    fun putComplexReactionTestResult(result: ReactionTestResult) {
        complexReactionTestResultsKeeper.putTestResult(result)
    }

    fun getComplexReactionTestResults(): ReactionTestResultList {
        return ReactionTestResultList(complexReactionTestResultsKeeper.getTestResults())
    }

    fun putTappingTestResult(result: TappingTestResult) {
        tappingTestResultsKeeper.putTestResult(result)
    }

    fun getLastTappingTestResult(): TappingTestResult? {
        return tappingTestResultsKeeper.getTestResults().lastOrNull()
    }

    fun getResultsCountByDays(): ResultsByDay {
        val resultsByDay = tappingTestResultsKeeper.getResultsByDay().clone()
        resultsByDay.mergeWith(reactionTestResultsKeeper.getResultsByDay())
        resultsByDay.mergeWith(complexReactionTestResultsKeeper.getResultsByDay())
        resultsByDay.mergeWith(movingReactionTestResultKeeper.getResultsByDay())

        return resultsByDay
    }

    companion object {
        private const val DB_TAG = "testingResults"

        private const val REACTION_TAG = "reaction"
        private const val REACTION_COUNTER_TAG = "reaction_counter"

        private const val COMPLEX_REACTION_TAG = "complex_reaction"
        private const val COMPLEX_REACTION_COUNTER_TAG = "complex_reaction_counter"

        private const val TAPPING_TAG = "tapping"
        private const val TAPPING_COUNTER_TAG = "tapping_counter"

        private const val MOVING_TAG = "moving"
        private const val MOVING_COUNTER_TAG = "moving_counter"

        private const val INVALID_COUNTER = 0
        private val handler = Handler(Looper.getMainLooper())
        private val gson = GsonBuilder().setPrettyPrinting()
            .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
            .create()
    }
}