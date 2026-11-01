package com.softeno.template.testsupport

import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random

/**
 * Static random test-data generator.
 *
 * Every call produces a fresh, unique, self-consistent value that the test keeps
 * a reference to — tests assert against what they generated instead of fixed
 * constants, so no value is shared between tests or between runs.
 */
object R {

    private const val LETTERS = "abcdefghijkmnpqrstuvwxyz"
    private const val HEX = "0123456789abcdef"

    /** Lowercase word of random length in [min, max]. */
    fun word(min: Int = 4, max: Int = 10): String =
        (1..Random.nextInt(min, max + 1)).map { LETTERS[Random.nextInt(LETTERS.length)] }.joinToString("")

    /** Sentence of 2..maxWords random words. */
    fun text(maxWords: Int = 5): String =
        (1..Random.nextInt(2, maxWords + 1)).joinToString(" ") { word() }

    fun uuid(): String = UUID.randomUUID().toString()

    /** Mongo-style 24-char hex ObjectId string. */
    fun objectId(): String = (1..24).map { HEX[Random.nextInt(16)] }.joinToString("")

    /** Unique JWT subject for auth fixtures. */
    fun sub(prefix: String = "user"): String = "$prefix-${uuid()}"

    /** Unique entity name: readable word + uuid suffix. */
    fun name(): String = "${word()}-${uuid()}"

    fun email(): String = "${word()}@${word()}.test"

    /** URL-safe payload value (sample DTOs, gRPC echo, frame content). */
    fun data(): String = "${word(6, 12)}-${Random.nextInt(100_000, 999_999)}"

    fun int(min: Int = 1, max: Int = 10_000): Int = Random.nextInt(min, max)

    fun long(max: Long = 1_000_000_000L): Long = Random.nextLong(max)

    fun localDateTime(): LocalDateTime = LocalDateTime.now().minusSeconds(long(60 * 60 * 24 * 365))
}
