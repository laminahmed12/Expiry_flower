package com.example

import com.example.data.model.ItemCategory
import com.example.util.ExpiryStatus
import com.example.util.ExpiryUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ExpiryLogicTest {

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @Test
    fun testExpiryCalculation_PastDate_IsExpired() {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -5) }
        val pastDate = sdf.format(cal.time)

        val days = ExpiryUtils.calculateDaysRemaining(pastDate)
        assertTrue("Days remaining should be negative for past date", days < 0)

        val status = ExpiryUtils.getExpiryStatus(pastDate, 30)
        assertEquals(ExpiryStatus.EXPIRED, status)
    }

    @Test
    fun testExpiryCalculation_NearFuture_IsExpiringSoon() {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 10) }
        val nearDate = sdf.format(cal.time)

        val days = ExpiryUtils.calculateDaysRemaining(nearDate)
        assertTrue("Days remaining should be around 10", days in 9..11)

        val status = ExpiryUtils.getExpiryStatus(nearDate, 15)
        assertEquals(ExpiryStatus.EXPIRING_SOON, status)
    }

    @Test
    fun testExpiryCalculation_FarFuture_IsSafe() {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 90) }
        val farDate = sdf.format(cal.time)

        val status = ExpiryUtils.getExpiryStatus(farDate, 30)
        assertEquals(ExpiryStatus.SAFE, status)
    }

    @Test
    fun testBarcodePresetCatalog() {
        val preset = ExpiryUtils.lookupBarcode("6281001234567")
        assertNotNull("Preset for paracetamol should exist", preset)
        assertEquals(ItemCategory.MEDICINE, preset?.category)
        assertTrue(preset?.isSensitive == true)
    }

    @Test
    fun testWorkerConstants() {
        assertEquals("expiry_alerts_channel", com.example.service.ExpiryCheckWorker.CHANNEL_ID)
        assertEquals("periodic_expiry_check_work", com.example.service.ExpiryCheckWorker.WORK_NAME_PERIODIC)
    }
}
