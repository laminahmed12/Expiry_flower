package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * التصنيف الأساسي للمواد:
 * FOOD = أغذية
 * CONSUMABLES = استهلاكية
 * MEDICINE = أدوية
 */
enum class ItemCategory(val code: String, val titleAr: String) {
    FOOD("FOOD", "أغذية"),
    CONSUMABLES("CONSUMABLES", "مواد استهلاكية"),
    MEDICINE("MEDICINE", "أدوية طبية");

    companion object {
        fun fromCode(code: String): ItemCategory =
            entries.find { it.code.equals(code, ignoreCase = true) } ?: FOOD
    }
}

@Entity(tableName = "stored_items")
data class StoredItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val category: String, // FOOD, CONSUMABLES, MEDICINE
    val expiryDate: String, // YYYY-MM-DD
    val quantity: Int = 1,
    val storageLocation: String = "", // مثل: الثلاجة، خزانة الأدوية، المستودع
    val imageUri: String? = null,
    val barcode: String? = null,
    val notes: String = "",
    val isSensitive: Boolean = false, // خاص بالمواد الحساسة مثل الأدوية
    val isConsumed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
