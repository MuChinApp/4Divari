package ir.chardivari.core.ai

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

class FairnessGuardTest {

    private val guard = FairnessGuard()

    @Test
    fun `sanitize keeps only schema-whitelisted keys`() {
        val args = buildJsonObject {
            put("city", "تهران")
            put("nationality", "iranian")
            put("gender", "female")
            put("max_price_rial", "100")
        }
        val (clean, dropped) = guard.sanitizeArguments(
            args = args,
            allowedKeys = setOf("city", "max_price_rial"),
        )
        assertThat(clean.keys).containsExactly("city", "max_price_rial")
        assertThat(dropped).containsExactly("nationality", "gender")
    }

    @Test
    fun `sanitize with no drops returns same keys`() {
        val args = buildJsonObject { put("city", "تهران") }
        val (clean, dropped) = guard.sanitizeArguments(args, setOf("city"))
        assertThat(clean.keys).containsExactly("city")
        assertThat(dropped).isEmpty()
    }

    @Test
    fun `detects directed gender criteria`() {
        val hits = guard.detectProtectedCriteria("فقط به زنان بفروشید")
        assertThat(hits).contains("gender")
    }

    @Test
    fun `detects directed religion criteria`() {
        val hits = guard.detectProtectedCriteria("فقط مسلمان‌ها می‌توانند بخرند")
        assertThat(hits).contains("religion")
    }

    @Test
    fun `detects nationality criteria`() {
        val hits = guard.detectProtectedCriteria("فقط ایرانی‌ها")
        assertThat(hits).contains("nationality")
    }

    @Test
    fun `detects marital criteria`() {
        val hits = guard.detectProtectedCriteria("فقط مجردها")
        assertThat(hits).contains("marital_status")
    }

    @Test
    fun `legit property criteria never match`() {
        val hits = guard.detectProtectedCriteria(
            "آپارتمان ۸۰ متری برای خانوادهٔ ۴نفره در تهران زیر ۵ میلیارد",
        )
        assertThat(hits).isEmpty()
    }

    @Test
    fun `combined criteria return unique categories`() {
        val hits = guard.detectProtectedCriteria("فقط به مردان و فقط متأهل")
        assertThat(hits).containsExactly("gender", "marital_status")
    }
}
