package com.guang.cloudx

import com.guang.cloudx.logic.model.AppVersion
import org.junit.Assert.*
import org.junit.Test

class AppVersionTest {
    private fun version(
        value: String,
        local: Boolean = false,
    ) = requireNotNull(AppVersion.parse(value, local))

    @Test fun comparesNumericComponentsInsteadOfStrings() {
        assertTrue(version("v1.10.0") > version("1.9.9"))
        assertTrue(version("2.0.0") > version("1.999.0"))
        assertTrue(version("1.5.3") > version("1.5.2"))
        assertEquals(0, version("1.5").compareTo(version("1.5.0")))
    }

    @Test fun localGitSuffixAndBuildMetadataDoNotTriggerSameVersionUpdates() {
        for (current in listOf("1.5.2-bdd7f3d", "1.5.2-1234567", "1.5.2-unknown", "1.5.2+build.17")) {
            assertEquals(current, 0, version(current, local = true).compareTo(version("v1.5.2")))
        }
    }

    @Test fun realPrereleasesRemainOlderThanStableAndHaveNumericOrdering() {
        assertTrue(version("1.5.2-rc.2", local = true) < version("1.5.2"))
        assertTrue(version("1.5.2-rc.10") > version("1.5.2-rc.2"))
        assertTrue(version("1.5.2-beta") < version("1.5.2-rc"))
        assertTrue(version("1.5.2-1") < version("1.5.2-alpha"))
    }

    @Test fun invalidInputsAreRejectedAndLargeComponentsDoNotOverflow() {
        for (value in listOf("", "latest", "vNext", "1..2", "1.2.3-", "1.2.3/../../", "9".repeat(300))) {
            assertNull(value, AppVersion.parse(value))
        }
        assertTrue(version("99999999999999999999.0.0") > version("2.0.0"))
    }
}
