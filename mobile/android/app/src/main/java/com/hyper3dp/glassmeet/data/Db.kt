package com.hyper3dp.glassmeet.data

import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.result.PostgrestResult
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Small helpers over postgrest-kt so every write sends exactly the JSON we build (no default
// values dropped by the serializer, nulls written as nulls).

/** Updates the rows matching [where] with [values] and returns them. */
suspend fun updateRows(table: String, values: JsonObject, where: io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder.() -> Unit): PostgrestResult =
    supabase.from(table).update(body = values as JsonElement) {
        select()
        filter(where)
    }

/** Inserts one row and returns it. */
suspend fun insertRow(table: String, values: JsonObject): PostgrestResult =
    supabase.from(table).insert(body = JsonArray(listOf(values))) { select() }

/** An edge function's error, as the functions send it: { error: code, message } */
class FunctionException(val code: String, message: String) : Exception(message)

/** POSTs [body] as JSON to an edge function; a failure throws [FunctionException] with the function's code. */
suspend fun callFunction(name: String, body: JsonObject): JsonElement {
    val response = try {
        supabase.functions.invoke(name) {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
    } catch (e: RestException) {
        val detail = runCatching { AppJson.parseToJsonElement(e.error) as? JsonObject }.getOrNull()
        val code = (detail?.get("error") as? JsonPrimitive)?.contentOrNull
            ?: if (e.statusCode == 404) "unavailable" else "error"
        val message = (detail?.get("message") as? JsonPrimitive)?.contentOrNull
            ?: if (code == "unavailable") "This isn't available yet." else "The server didn't respond. Try again."
        throw FunctionException(code, message)
    }
    val text = response.bodyAsText()
    return if (text.isBlank()) JsonNull else runCatching { AppJson.parseToJsonElement(text) }.getOrDefault(JsonNull)
}

fun JsonElement?.field(key: String): String? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull

/** Plain words for a failed request: the screens show this under the action that failed. */
fun Throwable.friendly(fallback: String = "Something went wrong. Try again."): String {
    val text = message.orEmpty()
    return when {
        this is FunctionException -> message ?: fallback
        text.contains("Unable to resolve host", true) || text.contains("timeout", true) ||
            text.contains("failed to connect", true) || this is java.io.IOException ->
            "No connection. Check your internet and try again."
        text.contains("signed out", true) -> text
        else -> text.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() && it.length < 160 } ?: fallback
    }
}
