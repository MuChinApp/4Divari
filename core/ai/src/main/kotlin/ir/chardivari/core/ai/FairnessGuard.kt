package ir.chardivari.core.ai

import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

/**
 * Programmatic fairness floor for the assistant (master prompt: no fairness
 * violations).
 *
 * Two layers:
 *  1. [sanitizeArguments] — hard whitelist: tool arguments only keep keys in
 *     the tool's JSON schema, so a protected/person attribute can NEVER reach
 *     a search filter (SearchFilters has no such field, and unknown keys are
 *     dropped before execution).
 *  2. [detectProtectedCriteria] — detects DIRECTED person-attribute criteria
 *     in the user's text («فقط به زنان», «فقط مسلمان‌ها», nationality, …)
 *     so the turn can carry an explicit fairness notice and analytics.
 *     Detection is conservative: it looks for directive patterns, not every
 *     mention of a word («آپارتمان برای خانوادهٔ ۴نفره» never matches).
 */
class FairnessGuard @Inject constructor() {

    /** Drops keys outside the tool schema; returns cleaned args + dropped keys. */
    fun sanitizeArguments(
        args: JsonObject,
        allowedKeys: Set<String>,
    ): Pair<JsonObject, List<String>> {
        val kept = args.filterKeys { it in allowedKeys }
        val dropped = args.keys.filter { it !in allowedKeys }
        return JsonObject(kept) to dropped
    }

    /**
     * Returns the matched fairness categories (unique, stable order).
     * Empty list = no directed person-attribute criterion detected.
     */
    fun detectProtectedCriteria(userText: String): List<String> {
        val text = userText.lowercase()
        val matched = mutableListOf<String>()
        fun add(category: String) {
            if (category !in matched) matched += category
        }
        if (GENDER.any { text.contains(it) }) add("gender")
        if (MARITAL.any { text.contains(it) }) add("marital_status")
        if (RELIGION.any { text.contains(it) }) add("religion")
        if (NATIONALITY.any { text.contains(it) }) add("nationality")
        return matched
    }

    companion object {
        /** Shown on any turn whose text contains directed person criteria. */
        const val FAIRNESS_NOTICE =
            "چاردیواری در جست‌وجو و نتایج بر اساس ویژگی‌های شخصی افراد " +
                "(جنسیت، مذهب، تابعیت، وضعیت تأهل و مانند آن) تمایز قائل نمی‌شود؛ " +
                "فقط ویژگی‌های ملک معیارند."

        private val GENDER = listOf(
            "فقط به زنان",
            "فقط به مردان",
            "فقط زنان",
            "فقط مردان",
            "مخصوص زنان",
            "مخصوص مردان",
            "only for women",
            "only for men",
            "only women",
            "only men",
        )

        private val MARITAL = listOf(
            "فقط مجردها",
            "فقط مجرد",
            "فقط متأهلها",
            "فقط متأهلین",
            "فقط متأهل",
            "فقط متاهل",
            "only singles",
            "only single",
            "only married",
        )

        private val RELIGION = listOf(
            "فقط مسلمان",
            "فقط مسیحی",
            "فقط یهودی",
            "فقط شیعه",
            "فقط سنی",
            "only muslim",
            "only christian",
            "only jewish",
        )

        private val NATIONALITY = listOf(
            "اتباع خارجی",
            "تبعیت خارجی",
            "فقط ایرانیها",
            "فقط ایرانی",
            "پناهنده",
            "only iranian",
            "nationality",
            "refugee",
            "ethnicity",
        )
    }
}
