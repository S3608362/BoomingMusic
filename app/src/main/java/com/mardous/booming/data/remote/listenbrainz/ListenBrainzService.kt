package com.mardous.booming.data.remote.listenbrainz

import android.util.Log
import com.mardous.booming.data.remote.listenbrainz.model.ListenBrainzResponse
import com.mardous.booming.data.remote.listenbrainz.model.ListenBrainzSubmission
import com.mardous.booming.data.remote.listenbrainz.model.ListenBrainzTokenValidationResponse
import com.mardous.booming.util.Constants.USER_AGENT
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.userAgent
import kotlinx.coroutines.CancellationException

sealed class ListenBrainzResult {
    data object Success : ListenBrainzResult()
    data object Unauthorized : ListenBrainzResult()
    data object Retry : ListenBrainzResult()
    data class RateLimited(val retryAfterSeconds: Long? = null) : ListenBrainzResult()
    data class Rejected(val message: String?) : ListenBrainzResult()
}

class ListenBrainzService(
    private val client: HttpClient,
    private val rateLimiter: ListenBrainzRateLimiter
) {

    suspend fun validateToken(token: String): ListenBrainzTokenValidationResponse {
        rateLimiter.await()
        return client.get("${LISTENBRAINZ_API_URL}validate-token") {
            userAgent(USER_AGENT)
            header(HttpHeaders.Authorization, "Token $token")
        }.body()
    }

    suspend fun submitListens(
        token: String,
        submission: ListenBrainzSubmission
    ): ListenBrainzResult {
        return try {
            rateLimiter.await()
            val response = client.post("${LISTENBRAINZ_API_URL}submit-listens") {
                userAgent(USER_AGENT)
                header(HttpHeaders.Authorization, "Token $token")
                contentType(ContentType.Application.Json)
                setBody(submission)
            }

            when {
                response.status.isSuccess() -> {
                    val body = response.body<ListenBrainzResponse>()
                    if (body.status == "ok") {
                        ListenBrainzResult.Success
                    } else {
                        ListenBrainzResult.Rejected(body.error)
                    }
                }

                response.status == HttpStatusCode.Unauthorized -> {
                    ListenBrainzResult.Unauthorized
                }

                response.status == HttpStatusCode.TooManyRequests -> {
                    val retryAfter = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()
                        ?: response.headers["X-RateLimit-Reset-In"]?.toLongOrNull()
                    ListenBrainzResult.RateLimited(retryAfter)
                }

                response.status.value in 400..499 -> {
                    ListenBrainzResult.Rejected(response.bodyAsText())
                }

                else -> {
                    ListenBrainzResult.Retry
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "ListenBrainz: submit listens failed", e)
            ListenBrainzResult.Retry
        }
    }

    companion object {
        private const val TAG = "ListenBrainzService"
        private const val LISTENBRAINZ_API_URL = "https://api.listenbrainz.org/1/"
    }
}
