package ir.chardivari.feature.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IranianPhoneTest {

    @Test
    fun `local 09 format normalizes to e164`() {
        assertThat(IranianPhone.normalizeToE164("09123456789"))
            .isEqualTo("+989123456789")
    }

    @Test
    fun `9-digit 912 format normalizes`() {
        assertThat(IranianPhone.normalizeToE164("9123456789"))
            .isEqualTo("+989123456789")
    }

    @Test
    fun `98 prefix normalizes`() {
        assertThat(IranianPhone.normalizeToE164("989123456789"))
            .isEqualTo("+989123456789")
    }

    @Test
    fun `plus 98 normalizes`() {
        assertThat(IranianPhone.normalizeToE164("+989123456789"))
            .isEqualTo("+989123456789")
    }

    @Test
    fun `invalid numbers rejected`() {
        assertThat(IranianPhone.isValidIranMobile("12345")).isFalse()
        assertThat(IranianPhone.isValidIranMobile("")).isFalse()
        assertThat(IranianPhone.isValidIranMobile("08123456789")).isFalse()
    }
}
