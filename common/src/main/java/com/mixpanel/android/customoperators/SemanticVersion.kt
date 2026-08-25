package com.mixpanel.android.customoperators

import androidx.annotation.RestrictTo

/**
 * Semantic Versioning 2.0.0 comparison, per [semver.org](https://semver.org).
 *
 * Kept free of any JsonLogic type so the same ordering can back either engine.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object SemanticVersion {
    // Using the official semantic versioning 2.0.0 regular expression to handle cross-platform
    // validation differences on other SDK's. For example, some platforms allow leading zeros even
    // though it is not valid as part of the Semver 2.0.0 spec. See https://semver.org/
    private val SEMVER =
        Regex(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)" +
                "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)" +
                "(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?" +
                "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$"
        )

    // SemVer 2.0.0 requires major.minor.patch; partial versions are zero-padded to this.
    private const val SEMVER_PARTS = 3

    /**
     * Orders two versions, returning a negative number, zero, or a positive number.
     *
     * Both sides are normalized first: surrounding whitespace is trimmed, a leading `v` or `V` is
     * dropped, and a partial version is zero-padded. Returns null when either side is still not a
     * valid version, which callers treat as "no match".
     */
    @JvmStatic
    fun compare(
        actual: String,
        target: String
    ): Int? {
        val normalizedActual = normalize(actual)
        val normalizedTarget = normalize(target)
        if (!SEMVER.matches(normalizedActual) || !SEMVER.matches(normalizedTarget)) {
            return null
        }
        return compareValidated(normalizedActual, normalizedTarget)
    }

    private fun normalize(raw: String): String {
        var stripped = raw.trim()
        if (stripped.startsWith("v") || stripped.startsWith("V")) {
            stripped = stripped.substring(1)
        }

        var suffixStart = stripped.length
        for (separator in charArrayOf('-', '+')) {
            val index = stripped.indexOf(separator)
            if (index != -1 && index < suffixStart) {
                suffixStart = index
            }
        }

        val core = stripped.substring(0, suffixStart)
        val suffix = stripped.substring(suffixStart)

        // Reject anything that is not 1-3 all-digit segments, so an empty or malformed core is never
        // padded into a real version such as "0.0.0".
        val segments = core.split(".")
        if (core.isEmpty() || segments.size > SEMVER_PARTS || segments.any { !isNumeric(it) }) {
            return stripped
        }
        val padded = segments + List(SEMVER_PARTS - segments.size) { "0" }
        return padded.joinToString(".") + suffix
    }

    private fun isNumeric(identifier: String): Boolean =
        identifier.isNotEmpty() && identifier.all { it in '0'..'9' }

    // Numeric identifiers carry no leading zeros, so the longer run of digits is the larger number.
    // Comparing them as digits rather than parsing to a Long keeps versions that overflow a 64-bit
    // integer ordered correctly.
    private fun compareNumeric(
        a: String,
        b: String
    ): Int =
        if (a.length != b.length) {
            if (a.length < b.length) -1 else 1
        } else {
            a.compareTo(b).coerceIn(-1, 1)
        }

    // SemVer 2.0.0 section 11.4: digits compare numerically, a numeric identifier ranks below an
    // alphanumeric one, and anything else compares by ASCII order.
    private fun comparePrereleaseIdentifier(
        a: String,
        b: String
    ): Int {
        val aNumeric = isNumeric(a)
        val bNumeric = isNumeric(b)
        return when {
            aNumeric && bNumeric -> compareNumeric(a, b)
            aNumeric -> -1
            bNumeric -> 1
            else -> a.compareTo(b).coerceIn(-1, 1)
        }
    }

    // Ordering per SemVer 2.0.0 section 11. Both operands have already been normalized and matched
    // against the official regex, so the core holds exactly three numeric identifiers and every
    // prerelease field is well-formed; the split needs no error path.
    private fun compareValidated(
        actual: String,
        target: String
    ): Int {
        val (actualCore, actualPrerelease) = split(actual)
        val (targetCore, targetPrerelease) = split(target)

        for (index in actualCore.indices) {
            val result = compareNumeric(actualCore[index], targetCore[index])
            if (result != 0) {
                return result
            }
        }

        // A prerelease ranks below the release it belongs to (section 11.3).
        if (actualPrerelease.isEmpty() && targetPrerelease.isEmpty()) return 0
        if (actualPrerelease.isEmpty()) return 1
        if (targetPrerelease.isEmpty()) return -1

        for (index in 0 until minOf(actualPrerelease.size, targetPrerelease.size)) {
            val result = comparePrereleaseIdentifier(actualPrerelease[index], targetPrerelease[index])
            if (result != 0) {
                return result
            }
        }
        // Every field so far is equal, so the longer list wins (section 11.4.4).
        return actualPrerelease.size.compareTo(targetPrerelease.size)
    }

    // Strip optional build metadata and separate the core version from pre-release identifiers
    private fun split(version: String): Pair<List<String>, List<String>> {
        var remaining = version
        val plus = remaining.indexOf('+')
        if (plus != -1) {
            remaining = remaining.substring(0, plus)
        }
        val dash = remaining.indexOf('-')
        if (dash == -1) {
            return remaining.split(".") to emptyList()
        }
        return remaining.substring(0, dash).split(".") to remaining.substring(dash + 1).split(".")
    }
}
