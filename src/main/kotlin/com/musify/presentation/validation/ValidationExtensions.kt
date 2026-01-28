package com.musify.presentation.validation

import com.musify.core.exceptions.ValidationException
import io.konform.validation.Invalid
import io.konform.validation.Validation
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

/**
 * Extension function to receive and validate request body
 */
suspend inline fun <reified T : Any> ApplicationCall.receiveAndValidate(
    validation: Validation<T>
): T? {
    val dto = receive<T>()
    val result = validation(dto)
    
    return when (result) {
        is Invalid -> {
            val errors = result.errors.groupBy(
                { it.dataPath.removePrefix(".") },
                { it.message }
            )
            respond(HttpStatusCode.BadRequest, mapOf("errors" to errors))
            null
        }
        else -> dto
    }
}

/**
 * Convert validation result to exception
 */
fun <T> io.konform.validation.ValidationResult<T>.toException(): ValidationException? {
    return when (this) {
        is Invalid -> {
            val errors = this.errors.groupBy(
                { it.dataPath.removePrefix(".") },
                { it.message }
            )
            ValidationException(errors)
        }
        else -> null
    }
}