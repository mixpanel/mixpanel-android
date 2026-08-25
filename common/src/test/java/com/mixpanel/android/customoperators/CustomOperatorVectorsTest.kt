package com.mixpanel.android.customoperators

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Runs the custom-operator golden vectors against the comparison helpers this module provides.
 *
 * The vectors are the cross-SDK contract; the canonical copy and its README live in the analytics
 * monorepo. Each case is a `[subject, operator, target, expected]` row. The SDKs reach these
 * helpers through a JsonLogic engine, but the helpers themselves carry no JsonLogic type, so the
 * row is applied directly here. That keeps the module covered by the same cases its consumers run,
 * without tying it to a particular engine.
 */
@RunWith(Parameterized::class)
class CustomOperatorVectorsTest(
    private val testName: String,
    private val operator: String,
    private val subject: Any?,
    private val symbol: String,
    private val target: Any,
    private val want: Boolean
) {
    companion object {
        private fun comparatorMatches(
            cmp: Long,
            symbol: String
        ): Boolean =
            when (symbol) {
                "===" -> cmp == 0L
                "!==" -> cmp != 0L
                "<" -> cmp < 0L
                "<=" -> cmp <= 0L
                ">" -> cmp > 0L
                ">=" -> cmp >= 0L
                else -> false
            }

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun vectors(): Collection<Array<Any?>> = loadVectors("semver") + loadVectors("datetime")

        /** Reads a golden-vector file. String entries are headings, array entries are cases. */
        private fun loadVectors(operator: String): List<Array<Any?>> {
            val fileName = "${operator}_compare_tests.json"
            val stream =
                CustomOperatorVectorsTest::class.java.classLoader?.getResourceAsStream(fileName)
                    ?: error("$fileName not found in test resources")
            val entries = JSONArray(stream.bufferedReader().use { it.readText() })

            var section = ""
            val cases = mutableListOf<Array<Any?>>()
            for (index in 0 until entries.length()) {
                val entry = entries.get(index)
                if (entry is String) {
                    section = entry
                    continue
                }
                val row = entry as JSONArray
                // A null subject means the property is not set. org.json decodes JSON null to the
                // JSONObject.NULL sentinel rather than a Kotlin null, so isNull is the only correct
                // test.
                val subject = if (row.isNull(0)) null else row.get(0)
                val symbol = row.getString(1)
                val target = row.get(2)
                val name = "$index $section: $subject $symbol $target"
                cases.add(arrayOf(name, operator, subject, symbol, target, row.getBoolean(3)))
            }
            return cases
        }
    }

    private fun semverCompare(): Boolean {
        val actual = subject as? String ?: return false
        val wanted = target as? String ?: return false
        val cmp = SemanticVersion.compare(actual, wanted) ?: return false
        return comparatorMatches(cmp.toLong(), symbol)
    }

    private fun datetimeCompare(): Boolean {
        val actual = (subject as? String)?.let { Rfc3339.toUnixSeconds(it) } ?: return false
        val wanted = (target as? Number)?.let { Rfc3339.epochMillisToUnixSeconds(it.toDouble()) } ?: return false
        return comparatorMatches(actual - wanted, symbol)
    }

    @Test
    fun goldenVector() {
        val got =
            when (operator) {
                "semver" -> semverCompare()
                "datetime" -> datetimeCompare()
                else -> error("unknown operator: $operator")
            }
        assertEquals(testName, want, got)
    }
}
