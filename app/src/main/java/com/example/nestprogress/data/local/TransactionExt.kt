package com.example.nestprogress.data.local

import androidx.room.withTransaction

/**
 * Thin wrapper around room-ktx's suspend [withTransaction] so callers don't import
 * the extension directly and the name stays greppable across the codebase.
 */
suspend fun <R> NestDatabase.withTransactionCompat(block: suspend () -> R): R =
    this.withTransaction(block)
