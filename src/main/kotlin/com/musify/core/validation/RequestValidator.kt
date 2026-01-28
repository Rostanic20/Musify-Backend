package com.musify.core.validation

import com.musify.core.exceptions.ValidationException
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID as JavaUUID
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

/**
 * Request validation framework
 */
object RequestValidator {
    
    /**
     * Validate a request object based on its annotations
     * @throws ValidationException if validation fails
     */
    fun <T : Any> validate(request: T) {
        val errors = mutableMapOf<String, MutableList<String>>()
        val kClass = request::class
        
        // Check if class has @ValidRequest annotation
        if (kClass.findAnnotation<ValidRequest>() == null) {
            return // Skip validation if not marked
        }
        
        kClass.memberProperties.forEach { property ->
            @Suppress("UNCHECKED_CAST")
            val prop = property as KProperty1<T, *>
            val value = prop.get(request)
            val fieldName = prop.name
            
            // Validate each annotation on the property
            prop.annotations.forEach { annotation ->
                when (annotation) {
                    is NotBlank -> validateNotBlank(value, fieldName, annotation, errors)
                    is Email -> validateEmail(value, fieldName, annotation, errors)
                    is Min -> validateMin(value, fieldName, annotation, errors)
                    is Max -> validateMax(value, fieldName, annotation, errors)
                    is Size -> validateSize(value, fieldName, annotation, errors)
                    is Pattern -> validatePattern(value, fieldName, annotation, errors)
                    is OneOf -> validateOneOf(value, fieldName, annotation, errors)
                    is Password -> validatePassword(value, fieldName, annotation, errors)
                    is Username -> validateUsername(value, fieldName, annotation, errors)
                    is Url -> validateUrl(value, fieldName, annotation, errors)
                    is Phone -> validatePhone(value, fieldName, annotation, errors)
                    is DateFormat -> validateDateFormat(value, fieldName, annotation, errors)
                    is Future -> validateFuture(value, fieldName, annotation, errors)
                    is Past -> validatePast(value, fieldName, annotation, errors)
                    is UUID -> validateUUID(value, fieldName, annotation, errors)
                }
            }
        }
        
        if (errors.isNotEmpty()) {
            throw ValidationException(errors)
        }
    }
    
    private fun validateNotBlank(value: Any?, fieldName: String, annotation: NotBlank, errors: MutableMap<String, MutableList<String>>) {
        if (value == null || (value as? String)?.isBlank() == true) {
            errors.getOrPut(fieldName) { mutableListOf() }.add(annotation.message)
        }
    }
    
    private fun validateEmail(value: Any?, fieldName: String, annotation: Email, errors: MutableMap<String, MutableList<String>>) {
        val email = value as? String
        if (email != null && !email.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))) {
            errors.getOrPut(fieldName) { mutableListOf() }.add(annotation.message)
        }
    }
    
    private fun validateMin(value: Any?, fieldName: String, annotation: Min, errors: MutableMap<String, MutableList<String>>) {
        when (value) {
            is Number -> {
                if (value.toLong() < annotation.value) {
                    errors.getOrPut(fieldName) { mutableListOf() }
                        .add(annotation.message.replace("{value}", annotation.value.toString()))
                }
            }
            is String -> {
                value.toLongOrNull()?.let { num ->
                    if (num < annotation.value) {
                        errors.getOrPut(fieldName) { mutableListOf() }
                            .add(annotation.message.replace("{value}", annotation.value.toString()))
                    }
                }
            }
        }
    }
    
    private fun validateMax(value: Any?, fieldName: String, annotation: Max, errors: MutableMap<String, MutableList<String>>) {
        when (value) {
            is Number -> {
                if (value.toLong() > annotation.value) {
                    errors.getOrPut(fieldName) { mutableListOf() }
                        .add(annotation.message.replace("{value}", annotation.value.toString()))
                }
            }
            is String -> {
                value.toLongOrNull()?.let { num ->
                    if (num > annotation.value) {
                        errors.getOrPut(fieldName) { mutableListOf() }
                            .add(annotation.message.replace("{value}", annotation.value.toString()))
                    }
                }
            }
        }
    }
    
    private fun validateSize(value: Any?, fieldName: String, annotation: Size, errors: MutableMap<String, MutableList<String>>) {
        val size = when (value) {
            is String -> value.length
            is Collection<*> -> value.size
            is Array<*> -> value.size
            else -> return
        }
        
        if (size < annotation.min || size > annotation.max) {
            errors.getOrPut(fieldName) { mutableListOf() }
                .add(annotation.message
                    .replace("{min}", annotation.min.toString())
                    .replace("{max}", annotation.max.toString()))
        }
    }
    
    private fun validatePattern(value: Any?, fieldName: String, annotation: Pattern, errors: MutableMap<String, MutableList<String>>) {
        val str = value as? String
        if (str != null && !str.matches(Regex(annotation.regex))) {
            errors.getOrPut(fieldName) { mutableListOf() }
                .add(annotation.message.replace("{regex}", annotation.regex))
        }
    }
    
    private fun validateOneOf(value: Any?, fieldName: String, annotation: OneOf, errors: MutableMap<String, MutableList<String>>) {
        val str = value?.toString()
        if (str != null && str !in annotation.values) {
            errors.getOrPut(fieldName) { mutableListOf() }
                .add(annotation.message.replace("{values}", annotation.values.joinToString(", ")))
        }
    }
    
    private fun validatePassword(value: Any?, fieldName: String, annotation: Password, errors: MutableMap<String, MutableList<String>>) {
        val password = value as? String ?: return
        
        val validationErrors = mutableListOf<String>()
        
        if (password.length < annotation.minLength) {
            validationErrors.add("Password must be at least ${annotation.minLength} characters long")
        }
        
        if (annotation.requireUppercase && !password.any { it.isUpperCase() }) {
            validationErrors.add("Password must contain at least one uppercase letter")
        }
        
        if (annotation.requireLowercase && !password.any { it.isLowerCase() }) {
            validationErrors.add("Password must contain at least one lowercase letter")
        }
        
        if (annotation.requireDigit && !password.any { it.isDigit() }) {
            validationErrors.add("Password must contain at least one digit")
        }
        
        if (annotation.requireSpecialChar && !password.any { !it.isLetterOrDigit() }) {
            validationErrors.add("Password must contain at least one special character")
        }
        
        if (validationErrors.isNotEmpty()) {
            errors.getOrPut(fieldName) { mutableListOf() }.addAll(validationErrors)
        }
    }
    
    private fun validateUsername(value: Any?, fieldName: String, annotation: Username, errors: MutableMap<String, MutableList<String>>) {
        val username = value as? String
        if (username != null) {
            if (username.length < annotation.minLength || username.length > annotation.maxLength) {
                errors.getOrPut(fieldName) { mutableListOf() }
                    .add(annotation.message
                        .replace("{minLength}", annotation.minLength.toString())
                        .replace("{maxLength}", annotation.maxLength.toString()))
            } else if (!username.matches(Regex(annotation.pattern))) {
                errors.getOrPut(fieldName) { mutableListOf() }
                    .add(annotation.message
                        .replace("{minLength}", annotation.minLength.toString())
                        .replace("{maxLength}", annotation.maxLength.toString()))
            }
        }
    }
    
    private fun validateUrl(value: Any?, fieldName: String, annotation: Url, errors: MutableMap<String, MutableList<String>>) {
        val url = value as? String
        if (url != null) {
            try {
                val parsedUrl = URL(url)
                if (parsedUrl.protocol !in annotation.protocols) {
                    errors.getOrPut(fieldName) { mutableListOf() }
                        .add("URL protocol must be one of: ${annotation.protocols.joinToString(", ")}")
                }
            } catch (e: Exception) {
                errors.getOrPut(fieldName) { mutableListOf() }.add(annotation.message)
            }
        }
    }
    
    private fun validatePhone(value: Any?, fieldName: String, annotation: Phone, errors: MutableMap<String, MutableList<String>>) {
        val phone = value as? String
        if (phone != null && !phone.matches(Regex(annotation.pattern))) {
            errors.getOrPut(fieldName) { mutableListOf() }.add(annotation.message)
        }
    }
    
    private fun validateDateFormat(value: Any?, fieldName: String, annotation: DateFormat, errors: MutableMap<String, MutableList<String>>) {
        val dateStr = value as? String
        if (dateStr != null) {
            try {
                DateTimeFormatter.ofPattern(annotation.pattern).parse(dateStr)
            } catch (e: Exception) {
                errors.getOrPut(fieldName) { mutableListOf() }
                    .add(annotation.message.replace("{pattern}", annotation.pattern))
            }
        }
    }
    
    private fun validateFuture(value: Any?, fieldName: String, annotation: Future, errors: MutableMap<String, MutableList<String>>) {
        when (value) {
            is LocalDate -> {
                if (!value.isAfter(LocalDate.now())) {
                    errors.getOrPut(fieldName) { mutableListOf() }.add(annotation.message)
                }
            }
            is String -> {
                try {
                    val date = LocalDate.parse(value)
                    if (!date.isAfter(LocalDate.now())) {
                        errors.getOrPut(fieldName) { mutableListOf() }.add(annotation.message)
                    }
                } catch (e: Exception) {
                    // Ignore parsing errors, they should be caught by DateFormat
                }
            }
        }
    }
    
    private fun validatePast(value: Any?, fieldName: String, annotation: Past, errors: MutableMap<String, MutableList<String>>) {
        when (value) {
            is LocalDate -> {
                if (!value.isBefore(LocalDate.now())) {
                    errors.getOrPut(fieldName) { mutableListOf() }.add(annotation.message)
                }
            }
            is String -> {
                try {
                    val date = LocalDate.parse(value)
                    if (!date.isBefore(LocalDate.now())) {
                        errors.getOrPut(fieldName) { mutableListOf() }.add(annotation.message)
                    }
                } catch (e: Exception) {
                    // Ignore parsing errors, they should be caught by DateFormat
                }
            }
        }
    }
    
    private fun validateUUID(value: Any?, fieldName: String, annotation: UUID, errors: MutableMap<String, MutableList<String>>) {
        val uuid = value as? String
        if (uuid != null) {
            try {
                JavaUUID.fromString(uuid)
            } catch (e: IllegalArgumentException) {
                errors.getOrPut(fieldName) { mutableListOf() }.add(annotation.message)
            }
        }
    }
}

/**
 * Extension function to validate requests easily
 */
inline fun <reified T : Any> T.validate() {
    RequestValidator.validate(this)
}