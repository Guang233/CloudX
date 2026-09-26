package com.guang.cloudx

import com.google.gson.Gson
import com.guang.cloudx.logic.interfaces.GitHubRelease
import com.guang.cloudx.logic.interfaces.UpdateApi
import com.guang.cloudx.logic.repository.UpdateRepository
import com.guang.cloudx.logic.repository.UpdateResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import retrofit2.http.GET
import java.io.IOException

class UpdateRepositoryTest {
    private fun repository(block: suspend () -> Response<GitHubRelease>) =
        UpdateRepository(
            object : UpdateApi {
                override suspend fun checkUpdate() = block()
            },
        )

    @Test fun usesOfficialLatestReleaseEndpointAndParsesGitHubFields() {
        val endpoint =
            UpdateApi::class.java.methods
                .single { it.name == "checkUpdate" }
                .getAnnotation(GET::class.java)
        assertEquals("repos/Guang233/CloudX/releases/latest", requireNotNull(endpoint).value)
        val release =
            Gson().fromJson(
                """{"tag_name":"v1.6.0","name":"CloudX v1.6.0","body":"Fixes","published_at":"2026-09-26T00:00:00Z","draft":false,"prerelease":false}""",
                GitHubRelease::class.java,
            )
        assertEquals("v1.6.0", release.tagName)
        assertEquals("Fixes", release.body)
        assertEquals("2026-09-26T00:00:00Z", release.publishedAt)
    }

    @Test fun newerReleaseContainsNotesAndPinnedGitHubLink() =
        runBlocking {
            val result =
                repository { Response.success(GitHubRelease(tagName = "v1.6.0", body = "Changes")) }
                    .checkForUpdate("1.5.2-bdd7f3d") as UpdateResult.Available
            assertEquals("Changes", result.release.notes)
            assertEquals("v1.6.0", result.release.name)
            assertEquals("https://github.com/Guang233/CloudX/releases/tag/v1.6.0", result.release.pageUrl)
        }

    @Test fun sameVersionWithLocalCommitHashIsNotAnUpdate() =
        runBlocking {
            val repo = repository { Response.success(GitHubRelease(tagName = "v1.5.2")) }
            assertEquals(UpdateResult.UpToDate, repo.checkForUpdate("1.5.2-bdd7f3d"))
            assertEquals(UpdateResult.UpToDate, repo.checkForUpdate("1.6.0-abcdef1"))
        }

    @Test fun excludesDraftsAndPrereleases() =
        runBlocking {
            for (release in listOf(
                GitHubRelease(tagName = "v2.0.0", draft = true),
                GitHubRelease(tagName = "v2.0.0-beta.1", prerelease = true),
            )) {
                assertEquals(UpdateResult.NoRelease, repository { Response.success(release) }.checkForUpdate("1.5.2"))
            }
        }

    @Test fun errorsAndMissingReleasesAreNotReportedAsLatestVersion() =
        runBlocking {
            fun error(code: Int) = Response.error<GitHubRelease>(code, "{}".toResponseBody("application/json".toMediaType()))
            assertEquals(UpdateResult.NoRelease, repository { error(404) }.checkForUpdate("1.5.2"))
            for (code in listOf(403, 429, 500)) {
                assertTrue(repository { error(code) }.checkForUpdate("1.5.2") is UpdateResult.Error)
            }
            assertTrue(repository { throw IOException("offline") }.checkForUpdate("1.5.2") is UpdateResult.Error)
            assertTrue(repository { Response.success<GitHubRelease>(null) }.checkForUpdate("1.5.2") is UpdateResult.Error)
        }

    @Test fun invalidVersionsAreNotAssumedToBeUpdates() =
        runBlocking {
            assertTrue(repository { Response.success(GitHubRelease(tagName = "latest")) }.checkForUpdate("1.5.2") is UpdateResult.Error)
            assertTrue(repository { Response.success(GitHubRelease(tagName = "v1.6.0")) }.checkForUpdate("unknown") is UpdateResult.Error)
            assertTrue(repository { Response.success(GitHubRelease()) }.checkForUpdate("1.5.2") is UpdateResult.Error)
        }

    @Test fun cancellationPropagatesRatherThanBecomingNetworkError() =
        runBlocking {
            try {
                repository { throw CancellationException("cancel") }.checkForUpdate("1.5.2")
                fail("Expected cancellation")
            } catch (_: CancellationException) {
                // Expected: dismissing an in-flight manual check must not show a failure popup.
            }
        }
}
