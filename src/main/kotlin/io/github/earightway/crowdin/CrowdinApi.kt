package io.github.earightway.crowdin

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.gradle.api.GradleException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration

/**
 * Thin client over the parts of the Crowdin API v2 the translation tasks need.
 * Only a handful of calls are involved, so this avoids pulling the Crowdin SDK into the build.
 */
internal class CrowdinApi(
    baseUrl: String,
    private val token: String,
) {
    private val root = baseUrl.trimEnd('/')
    private val http =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    private val mapper = ObjectMapper()

    fun get(path: String): JsonNode = send(authorized(path).GET().build(), "GET", path)

    fun post(
        path: String,
        body: Map<String, Any?>,
    ): JsonNode = send(authorized(path).header("Content-Type", "application/json").POST(json(body)).build(), "POST", path)

    fun put(
        path: String,
        body: Map<String, Any?>,
    ): JsonNode = send(authorized(path).header("Content-Type", "application/json").PUT(json(body)).build(), "PUT", path)

    /** Uploads raw bytes to Crowdin's temporary storage and returns the storage id. */
    fun addToStorage(
        fileName: String,
        content: ByteArray,
    ): Long {
        val request =
            authorized("/storages")
                .header("Crowdin-API-FileName", fileName)
                .header("Content-Type", "application/octet-stream")
                .POST(BodyPublishers.ofByteArray(content))
                .build()
        return send(request, "POST", "/storages").requireField("data").requireField("id").asLong()
    }

    /** Downloads a temporary export URL. That URL is pre-signed, so it must not carry the token. */
    fun download(url: String): ByteArray {
        val request =
            HttpRequest
                .newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(READ_TIMEOUT_MINUTES))
                .GET()
                .build()
        val response = http.send(request, BodyHandlers.ofByteArray())
        if (response.statusCode() !in SUCCESS) {
            throw GradleException("Crowdin download failed with HTTP ${response.statusCode()}")
        }
        return response.body()
    }

    private fun authorized(path: String): HttpRequest.Builder =
        HttpRequest
            .newBuilder(URI.create(root + path))
            .timeout(Duration.ofMinutes(READ_TIMEOUT_MINUTES))
            .header("Authorization", "Bearer $token")

    private fun json(body: Map<String, Any?>): HttpRequest.BodyPublisher = BodyPublishers.ofString(mapper.writeValueAsString(body))

    private fun send(
        request: HttpRequest,
        method: String,
        path: String,
    ): JsonNode {
        val response = http.send(request, BodyHandlers.ofString())
        if (response.statusCode() !in SUCCESS) {
            throw GradleException(
                "Crowdin $method $path failed with HTTP ${response.statusCode()}: ${response.body().take(ERROR_BODY_LIMIT)}",
            )
        }
        return if (response.body().isEmpty()) mapper.createObjectNode() else mapper.readTree(response.body())
    }

    private companion object {
        val SUCCESS = 200..299
        const val CONNECT_TIMEOUT_SECONDS = 30L
        const val READ_TIMEOUT_MINUTES = 5L
        const val ERROR_BODY_LIMIT = 1000
    }
}

internal fun JsonNode.requireField(field: String): JsonNode =
    get(field) ?: throw GradleException("Unexpected Crowdin response: no '$field' in ${toString().take(RESPONSE_EXCERPT)}")

private const val RESPONSE_EXCERPT = 500
