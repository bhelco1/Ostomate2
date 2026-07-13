package com.ostomate.app.ui.screenshot

import android.os.Looper
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.ostomate.app.data.ChangeEventRepository
import com.ostomate.app.data.SupplyRepository
import com.ostomate.app.ui.FakeChangeEventDao
import com.ostomate.app.ui.FakeSupplyTypeDao
import com.ostomate.app.ui.theme.OstomateTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/** Every screenshot renders at this instant, in UTC. Nothing here may read the wall clock. */
val FIXED_INSTANT: Instant = Instant.parse("2026-03-15T12:00:00Z")
val FIXED_MILLIS: Long = FIXED_INSTANT.toEpochMilliseconds()
val FIXED_TODAY: LocalDate = LocalDate(2026, 3, 15)

const val DAY_MS: Long = 86_400_000L

/** Pinned stand-in for [Clock.System] — see FIXED_INSTANT. */
object FixedClock : Clock {
    override fun now(): Instant = FIXED_INSTANT
}

/**
 * Roborazzi screenshot tests (2.5.7). Robolectric renders the real composables on the JVM
 * host with native graphics; the images in `composeApp/screenshots/` are the baselines and
 * `testAndroidHostTest` verifies them on every PR.
 *
 * Determinism rules for anything captured here:
 *  - time comes from [FixedClock] / [FIXED_MILLIS], never `Clock.System`;
 *  - the JVM default time zone is pinned to UTC, so a LocalDate never shifts under the
 *    runner's zone;
 *  - the device is pinned by @Config qualifiers, so image size/density is host-independent;
 *  - ViewModel state is primed to a known value *before* capture (see [awaitState]), because
 *    a StateFlow that has not emitted yet renders the empty initial state.
 *
 * Subclasses get fake DAOs and the real repositories on top of them — the same boundary the
 * ViewModel tests fake at, so what is rendered is state the app can actually produce.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
abstract class ScreenshotTest {
    protected val supplyDao = FakeSupplyTypeDao()
    protected val eventDao = FakeChangeEventDao(supplyDao)
    protected val supplyRepository = SupplyRepository(supplyDao)
    protected val eventRepository = ChangeEventRepository(eventDao, supplyDao, clock = { FIXED_MILLIS })

    private val mainScope = CoroutineScope(Dispatchers.Main)
    private var originalTimeZone: TimeZone? = null

    @Before
    fun pinTimeZone() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() {
        mainScope.cancel()
        originalTimeZone?.let { TimeZone.setDefault(it) }
    }

    /** Seeds the fake DAOs. Repositories are plain suspend functions, so this is synchronous. */
    protected fun seed(block: suspend () -> Unit) = runBlocking { block() }

    /**
     * Subscribes to [state] and drains the main looper until [until] holds, then leaves the
     * subscription open for the capture. ViewModel `uiState` flows are
     * `SharingStarted.WhileSubscribed`, so without a live collector they sit at their empty
     * initial value and the screenshot would be of an empty screen that passes forever.
     */
    protected fun <T> awaitState(
        state: StateFlow<T>,
        until: (T) -> Boolean,
    ) {
        mainScope.launch { state.collect {} }
        repeat(MAX_LOOPER_DRAINS) {
            shadowOf(Looper.getMainLooper()).idle()
            if (until(state.value)) return
        }
        error("uiState never reached the expected value; last value was ${state.value}")
    }

    /** Renders [content] in the app theme and records or verifies `screenshots/<name>.png`. */
    protected fun capture(
        name: String,
        content: @Composable () -> Unit,
    ) {
        captureRoboImage(filePath = "$name.png", roborazziOptions = ROBORAZZI_OPTIONS) {
            OstomateTheme { content() }
        }
    }

    private companion object {
        const val MAX_LOOPER_DRAINS = 50

        /**
         * Baselines live in `roborazzi.output.dir` (Gradle points it at `composeApp/screenshots`);
         * failure artifacts — actual + side-by-side diff — go to `roborazzi.compare.output.dir`
         * under `build/`, so a failing run never dirties the committed baselines.
         *
         * changeThreshold = 0: any changed pixel fails. A tolerance here is what turns a
         * screenshot suite into decoration.
         */
        val ROBORAZZI_OPTIONS =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f),
            )
    }
}
