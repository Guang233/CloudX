package com.guang.cloudx

import com.guang.cloudx.logic.repository.AppRelease
import com.guang.cloudx.logic.repository.UpdateResult
import com.guang.cloudx.ui.update.UpdateController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class UpdateControllerTest {
    private val ignored = mutableSetOf<String>()

    private fun available(tag: String = "v1.6.0") =
        UpdateResult.Available(
            AppRelease(tag, tag, "changes", null, "https://github.com/Guang233/CloudX/releases/tag/$tag"),
        )

    private fun controller(
        scope: CoroutineScope,
        check: suspend () -> UpdateResult,
    ) = UpdateController(
        scope = scope,
        check = check,
        ignoredTags = { ignored.toSet() },
        saveIgnoredTag = { ignored += it },
    )

    @Test fun launchChecksOnceButNextLaunchChecksAgain() =
        runBlocking {
            var checks = 0
            val model =
                controller(this) {
                    checks++
                    available()
                }
            model.checkOnLaunch()
            yield()
            model.dismiss()
            model.checkOnLaunch()
            yield()
            assertEquals(1, checks)
            val nextLaunch =
                controller(this) {
                    checks++
                    available()
                }
            nextLaunch.checkOnLaunch()
            yield()
            assertEquals(2, checks)
            assertNotNull(nextLaunch.state.value.release)
        }

    @Test fun ignoreSurvivesNewControllerAndOnlySuppressesThatRelease() =
        runBlocking {
            val first = controller(this) { available() }
            first.checkOnLaunch()
            yield()
            first.ignoreThisVersion()
            assertTrue("v1.6.0" in ignored)
            val second = controller(this) { available() }
            second.checkOnLaunch()
            yield()
            assertNull(second.state.value.release)
            val newer = controller(this) { available("v1.7.0") }
            newer.checkOnLaunch()
            yield()
            assertEquals(
                "v1.7.0",
                newer.state.value.release
                    ?.tag,
            )
        }

    @Test fun manualCheckCanSeeIgnoredReleaseWithoutChangingPreference() =
        runBlocking {
            ignored += "v1.6.0"
            val model = controller(this) { available() }
            model.checkManually()
            yield()
            assertEquals(
                "v1.6.0",
                model.state.value.release
                    ?.tag,
            )
            assertEquals(setOf("v1.6.0"), ignored)
        }

    @Test fun startupFailuresAreQuietButManualFailuresAreVisible() =
        runBlocking {
            val model = controller(this) { UpdateResult.Error("offline") }
            model.checkOnLaunch()
            yield()
            assertNull(model.state.value.message)
            assertFalse(model.state.value.checking)
            model.checkManually()
            yield()
            assertEquals("offline", model.state.value.message)
        }

    @Test fun missingReleaseAndUpToDateHaveDistinctManualFeedback() =
        runBlocking {
            val noRelease = controller(this) { UpdateResult.NoRelease }
            noRelease.checkManually()
            yield()
            assertTrue(
                noRelease.state.value.message!!
                    .contains("暂无"),
            )
            val latest = controller(this) { UpdateResult.UpToDate }
            latest.checkManually()
            yield()
            assertEquals("当前已是最新版本", latest.state.value.message)
        }

    @Test fun manualClickAdoptsStartupRequestAndBypassesIgnore() =
        runBlocking {
            ignored += "v1.6.0"
            var checks = 0
            val result = CompletableDeferred<UpdateResult>()
            val model =
                controller(this) {
                    checks++
                    result.await()
                }
            model.checkOnLaunch()
            yield()
            model.checkManually()
            model.checkManually()
            assertTrue(model.state.value.checking)
            assertTrue(model.state.value.manual)
            result.complete(available())
            yield()
            assertEquals(1, checks)
            assertNotNull(model.state.value.release)
        }

    @Test fun dismissCancelsInFlightCheckWithoutLateDialog() =
        runBlocking {
            val result = CompletableDeferred<UpdateResult>()
            val model = controller(this) { result.await() }
            model.checkManually()
            yield()
            model.dismiss()
            result.complete(available())
            yield()
            assertNull(model.state.value.release)
            assertNull(model.state.value.message)
            assertFalse(model.state.value.checking)
        }
}
