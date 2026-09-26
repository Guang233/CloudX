package com.guang.cloudx.logic.repository

import com.guang.cloudx.logic.interfaces.UpdateApi
import com.guang.cloudx.logic.model.AppVersion
import com.guang.cloudx.logic.network.UpdateRetrofitClient
import kotlinx.coroutines.CancellationException
import java.net.URLEncoder

class UpdateRepository(
    private val api: UpdateApi = UpdateRetrofitClient.api,
) {
    suspend fun checkForUpdate(currentVersion: String): UpdateResult {
        return try {
            val response = api.checkUpdate()
            when {
                response.code() == 404 -> {
                    UpdateResult.NoRelease
                }

                response.code() == 403 || response.code() == 429 -> {
                    UpdateResult.Error("GitHub 访问受限或请求过于频繁，请稍后再试")
                }

                !response.isSuccessful -> {
                    UpdateResult.Error("GitHub 返回错误（HTTP ${response.code()}）")
                }

                else -> {
                    val release = response.body() ?: return UpdateResult.Error("GitHub 返回的发布信息为空")
                    if (release.draft || release.prerelease) return UpdateResult.NoRelease
                    val tag = release.tagName?.trim().orEmpty()
                    val latest =
                        AppVersion.parse(tag)
                            ?: return UpdateResult.Error("无法识别 GitHub 发布版本，请前往仓库查看")
                    val current =
                        AppVersion.parse(currentVersion, localBuild = true)
                            ?: return UpdateResult.Error("无法识别当前应用版本，请前往 GitHub 查看更新")
                    if (latest <= current) {
                        UpdateResult.UpToDate
                    } else {
                        UpdateResult.Available(
                            AppRelease(
                                tag = tag,
                                name = release.name?.takeIf { it.isNotBlank() } ?: tag,
                                notes = release.body?.takeIf { it.isNotBlank() } ?: "此版本未提供更新说明。",
                                publishedAt = release.publishedAt,
                                // Pin navigation to the official repository, not an arbitrary response URL.
                                pageUrl =
                                    "https://github.com/Guang233/CloudX/releases/tag/" +
                                        URLEncoder.encode(tag, "UTF-8").replace("+", "%20"),
                            ),
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            UpdateResult.Error("无法连接 GitHub，请检查网络后重试")
        }
    }
}

data class AppRelease(
    val tag: String,
    val name: String,
    val notes: String,
    val publishedAt: String?,
    val pageUrl: String,
)

sealed interface UpdateResult {
    data class Available(
        val release: AppRelease,
    ) : UpdateResult

    data object UpToDate : UpdateResult

    data object NoRelease : UpdateResult

    data class Error(
        val message: String,
    ) : UpdateResult
}
