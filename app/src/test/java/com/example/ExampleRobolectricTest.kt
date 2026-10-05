package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("تنبيه الصلاحية", appName)
  }

  @Test
  fun `test encrypted backup with default key`() {
    val sampleJson = """{"items":[{"id":1,"name":"حليب طازج"}],"timestamp":123456}"""
    val encrypted = com.example.util.BackupCryptoUtils.encryptBackup(sampleJson, null, 1)

    assertTrue("Encrypted output should contain JSON wrapper", encrypted.contains("EXPIRY_GUARD_ENCRYPTED_BACKUP"))

    val result = com.example.util.BackupCryptoUtils.decryptBackup(encrypted, null)
    assertTrue("Decryption should succeed", result is com.example.util.BackupCryptoUtils.DecryptResult.Success)
    val success = result as com.example.util.BackupCryptoUtils.DecryptResult.Success
    assertEquals(sampleJson, success.plainJson)
  }

  @Test
  fun `test encrypted backup with custom user password`() {
    val sampleJson = """{"items":[{"id":2,"name":"بنادول إكسترا"}],"timestamp":654321}"""
    val userPass = "MySecret123"
    val encrypted = com.example.util.BackupCryptoUtils.encryptBackup(sampleJson, userPass, 1)

    // Attempting without password should require password
    val reqResult = com.example.util.BackupCryptoUtils.decryptBackup(encrypted, null)
    assertTrue("Should indicate password required", reqResult is com.example.util.BackupCryptoUtils.DecryptResult.RequiresPassword)

    // Decrypting with wrong password should fail
    val failResult = com.example.util.BackupCryptoUtils.decryptBackup(encrypted, "WrongPassword")
    assertTrue("Should fail with wrong password", failResult is com.example.util.BackupCryptoUtils.DecryptResult.Error)

    // Decrypting with correct password should succeed
    val okResult = com.example.util.BackupCryptoUtils.decryptBackup(encrypted, userPass)
    assertTrue("Should succeed with correct password", okResult is com.example.util.BackupCryptoUtils.DecryptResult.Success)
    val okSuccess = okResult as com.example.util.BackupCryptoUtils.DecryptResult.Success
    assertEquals(sampleJson, okSuccess.plainJson)
  }

  @Test
  fun `test fallback product notification bitmap generation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val item = com.example.data.model.StoredItem(
        id = 101L,
        name = "حليب قليل الدسم",
        category = com.example.data.model.ItemCategory.FOOD.code,
        expiryDate = "2026-10-05",
        quantity = 3,
        storageLocation = "الثلاجة"
    )

    val bitmap = com.example.util.ImageNotificationUtils.createFallbackProductBitmap(context, item, 640, 360)
    org.junit.Assert.assertNotNull("Bitmap should be created", bitmap)
    assertEquals(640, bitmap.width)
    assertEquals(360, bitmap.height)
  }

  @Test
  fun `test loadBitmapFromUri handles null or empty uri safely`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val resultNull = com.example.util.ImageNotificationUtils.loadBitmapFromUri(context, null)
    org.junit.Assert.assertNull("Null URI should yield null", resultNull)

    val resultEmpty = com.example.util.ImageNotificationUtils.loadBitmapFromUri(context, "")
    org.junit.Assert.assertNull("Empty URI should yield null", resultEmpty)
  }
}
