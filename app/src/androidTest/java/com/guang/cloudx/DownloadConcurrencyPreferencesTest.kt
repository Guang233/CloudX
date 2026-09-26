package com.guang.cloudx

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.guang.cloudx.logic.utils.SharedPreferencesUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.UUID

class DownloadConcurrencyPreferencesTest {
    private lateinit var prefs: SharedPreferencesUtils

    @Before fun setUp() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val isolatedName = "concurrency-test-${UUID.randomUUID()}"
        val context =
            object : ContextWrapper(base) {
                override fun getSharedPreferences(
                    name: String,
                    mode: Int,
                ): SharedPreferences = base.getSharedPreferences(isolatedName, mode)
            }
        prefs = SharedPreferencesUtils(context)
    }

    @After fun tearDown() {
        prefs.sharedPreferences
            .edit()
            .clear()
            .commit()
    }

    @Test fun freshPreferencesDefaultToTwoSongsAndTwoParts() {
        assertEquals(2, prefs.getConcurrentDownloads())
        assertEquals(2, prefs.getSimultaneousSongs())
    }

    @Test fun existingPartSettingIsNotResetOrUsedAsSongCount() {
        prefs.sharedPreferences
            .edit()
            .putInt("concurrent_downloads", 6)
            .commit()
        assertEquals(6, prefs.getConcurrentDownloads())
        assertEquals(2, prefs.getSimultaneousSongs())
        prefs.putSimultaneousSongs(3)
        assertEquals(6, prefs.getConcurrentDownloads())
        assertEquals(3, prefs.getSimultaneousSongs())
    }

    @Test fun bothLimitsClampInvalidValuesIndependently() {
        prefs.putConcurrentDownloads(999)
        prefs.putSimultaneousSongs(999)
        assertEquals(8, prefs.getConcurrentDownloads())
        assertEquals(4, prefs.getSimultaneousSongs())
        prefs.putConcurrentDownloads(-1)
        prefs.putSimultaneousSongs(-1)
        assertEquals(1, prefs.getConcurrentDownloads())
        assertEquals(1, prefs.getSimultaneousSongs())
    }
}
