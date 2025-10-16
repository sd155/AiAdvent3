package io.github.sd155.aiadvent3.utils

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Represents the result of an operation that can either succeed with a value of type [S],
 * or fail with an error of type [F].
 */
sealed class Result<out F, out S> private constructor() {
    /**
     * Represents a successful result containing a value of type [T].
     */
    data class Success<T>(val value: T) : Result<Nothing, T>()
    /**
     * Represents a failed result containing an error of type [T].
     */
    data class Failure<T>(val error: T) : Result<T, Nothing>()

    /**
     * If this is a [Success], invokes [block] with the value.
     * Returns this result unchanged.
     */
    fun withSuccess(block: (S) -> Unit): Result<F, S> {
        if (this is Success)
            block(value)
        return this
    }
}

/**
 * Folds a [Result] by applying [onSuccess] if it is a [Result.Success],
 * or [onFailure] if it is a [Result.Failure].
 *
 * @param onSuccess Function to apply if this is a success
 * @param onFailure Function to apply if this is a failure
 * @return The result of the applied function
 */
@OptIn(ExperimentalContracts::class)
suspend fun <F, S, R> Result<F, S>.fold(
    onSuccess: suspend (value: S) -> R,
    onFailure: suspend (error: F) -> R
): R {
    contract {
        callsInPlace(onSuccess, InvocationKind.AT_MOST_ONCE)
        callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE)
    }
    return when (this) {
        is Result.Success -> onSuccess(value)
        is Result.Failure -> onFailure(error)
    }
}

/**
 * Wraps this value in a [Result.Success].
 */
fun <T> T.asSuccess() = Result.Success(this)

/**
 * Wraps this value in a [Result.Failure].
 */
fun <T> T.asFailure() = Result.Failure(this)

/**
 * If this is a [Result.Success], applies [block] to the value and returns the result.
 * If this is a [Result.Failure], returns the failure unchanged.
 */
@OptIn(ExperimentalContracts::class)
suspend fun <F, S, R> Result<F, S>.next(block: suspend (S) -> Result<F, R>): Result<F, R> {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }
    return  when(this) {
        is Result.Success -> block(value)
        is Result.Failure -> error.asFailure()
    }
}
