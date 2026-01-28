package com.musify.core.validation

/**
 * Validation annotations for request validation
 */

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class NotBlank(
    val message: String = "Field must not be blank"
)

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Email(
    val message: String = "Invalid email format"
)

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Min(
    val value: Long,
    val message: String = "Value must be at least {value}"
)

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Max(
    val value: Long,
    val message: String = "Value must be at most {value}"
)

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Size(
    val min: Int = 0,
    val max: Int = Int.MAX_VALUE,
    val message: String = "Size must be between {min} and {max}"
)

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Pattern(
    val regex: String,
    val message: String = "Value must match pattern {regex}"
)

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class OneOf(
    val values: Array<String>,
    val message: String = "Value must be one of {values}"
)

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Password(
    val minLength: Int = 8,
    val requireUppercase: Boolean = true,
    val requireLowercase: Boolean = true,
    val requireDigit: Boolean = true,
    val requireSpecialChar: Boolean = false,
    val message: String = "Password does not meet requirements"
)

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Username(
    val minLength: Int = 3,
    val maxLength: Int = 30,
    val pattern: String = "^[a-zA-Z0-9_-]+$",
    val message: String = "Username must be {minLength}-{maxLength} characters and contain only letters, numbers, hyphens, and underscores"
)

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Url(
    val protocols: Array<String> = ["http", "https"],
    val message: String = "Invalid URL format"
)

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Phone(
    val pattern: String = "^\\+?[1-9]\\d{1,14}$", // E.164 format
    val message: String = "Invalid phone number format"
)

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class DateFormat(
    val pattern: String = "yyyy-MM-dd",
    val message: String = "Date must be in format {pattern}"
)

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Future(
    val message: String = "Date must be in the future"
)

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Past(
    val message: String = "Date must be in the past"
)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ValidRequest

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class UUID(
    val message: String = "Invalid UUID format"
)