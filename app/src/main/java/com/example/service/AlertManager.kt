package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.data.model.ItemCategory
import com.example.data.model.StoredItem
import java.util.Locale

class AlertManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var toneGenerator: ToneGenerator? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("ar"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("AlertManager", "Arabic language not supported by TTS, falling back to default")
                tts?.setLanguage(Locale.getDefault())
            }
            tts?.setSpeechRate(0.95f)
            isTtsReady = true
        } else {
            Log.e("AlertManager", "Failed to initialize TextToSpeech")
        }
    }

    private fun getToneGenerator(): ToneGenerator? {
        if (toneGenerator == null) {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 85)
            } catch (e: Exception) {
                Log.w("AlertManager", "ToneGenerator not available on this device", e)
            }
        }
        return toneGenerator
    }

    /**
     * تشغيل صفارة إنذار خاصة بالمواد الحساسة والأدوية بأمان مع تفادي تسريب الموارد الصوتية
     */
    fun playSensitiveAlarmTone() {
        try {
            getToneGenerator()?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 900)
        } catch (e: Exception) {
            Log.w("AlertManager", "Error playing tone", e)
        }
    }

    /**
     * تشغيل تنبيه صوتي وتحدث نصي (TTS) لمادة معينة
     */
    fun speakItemAlert(item: StoredItem, daysRemaining: Long) {
        if (item.category == ItemCategory.MEDICINE.code || item.isSensitive) {
            playSensitiveAlarmTone()
        }

        val speechText = when {
            daysRemaining < 0 -> {
                "تحذير فوري: ${if (item.category == ItemCategory.MEDICINE.code) "دواء" else "مادة"} ${item.name} منتهية الصلاحية منذ ${-daysRemaining} يوماً! يرجى إتلافها والتخلص منها فوراً."
            }
            daysRemaining == 0L -> {
                "تنبيه عاجل: ${item.name} تنتهي صلاحيتها اليوم! يرجى الانتباه."
            }
            daysRemaining <= 3 -> {
                "تنبيه طارئ: متبقي فقط $daysRemaining أيام على انتهاء صلاحية ${item.name}. ${if (item.category == ItemCategory.MEDICINE.code) "يرجى توخي الحذر الشديد واستشارة الصيدلي." else ""}"
            }
            else -> {
                "تنبيه باقتراب الصلاحية: متبقي $daysRemaining يوماً على انتهاء ${item.name} المخزنة في ${if (item.storageLocation.isNotBlank()) item.storageLocation else "مكان التخزين"}."
            }
        }

        speak(speechText)
    }

    /**
     * نطق نص تجريبي للتأكد من عمل الصوت
     */
    fun speakTestAlert() {
        playSensitiveAlarmTone()
        speak("تنبيه نظام إدارة ومتابعة الصلاحية: النظام الصوتي والتنبيهات للأدوية والأغذية تعمل بنجاح وبأعلى دقة.")
    }

    /**
     * نطق ملخص للمواد التي أوشكت على الانتهاء
     */
    fun speakSummaryAlert(expiringCount: Int, expiredCount: Int, medicineCount: Int) {
        if (medicineCount > 0) {
            playSensitiveAlarmTone()
        }
        val builder = StringBuilder("تقرير الصلاحية الصوتي: ")
        if (expiredCount > 0) {
            builder.append("يوجد $expiredCount مواد منتهية الصلاحية. ")
        }
        if (expiringCount > 0) {
            builder.append("وهناك $expiringCount مواد أوشكت على الانتهاء. ")
        }
        if (medicineCount > 0) {
            builder.append("انتبه: من بينها $medicineCount أدوية طبية حساسة تتطلب فحصاً فورياً.")
        }
        speak(builder.toString())
    }

    fun speak(text: String) {
        if (isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "alert_${System.currentTimeMillis()}")
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (_: Exception) {}
    }
}
