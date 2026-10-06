package com.example

import com.example.util.LicenseDisplayUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class LicenseDisplayUtilsTest {

    @Test
    fun formatExpiryDate_formatsIsoUtcDate() {
        assertEquals("07/04/2027", LicenseDisplayUtils.formatExpiryDate("2027-04-07T18:36:02.443Z"))
    }

    @Test
    fun maskLicenseCode_hidesSecretPart() {
        assertEquals("AD6-D58••••••", LicenseDisplayUtils.maskLicenseCode("AD6-D5822EED6E25912B"))
    }

    @Test
    fun maskLicenseCode_handlesShortCode() {
        assertEquals("ABC123", LicenseDisplayUtils.maskLicenseCode("ABC123"))
    }
}
