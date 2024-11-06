package com.tonapps.wallet.data.contacts.source

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.tonapps.extensions.currentTimeSeconds
import com.tonapps.sqlite.SQLiteHelper
import com.tonapps.wallet.data.contacts.entities.ContactEntity

internal class DatabaseSource(
    context: Context
): SQLiteHelper(context, DATABASE_NAME, DATABASE_VERSION) {

    private companion object {
        private const val DATABASE_NAME = "contacts"
        private const val DATABASE_VERSION = 2

        private const val CONTACTS_TABLE = "contacts"
        private const val CONTACTS_ID = "_id"
        private const val CONTACTS_NAME = "name"
        private const val CONTACTS_ADDRESS = "address"
        private const val CONTACTS_DATE = "date"
        private const val CONTACTS_TESTNET = "testnet"

        private val contactsField = arrayOf(
            CONTACTS_ID,
            CONTACTS_NAME,
            CONTACTS_ADDRESS,
            CONTACTS_DATE,
            CONTACTS_TESTNET
        ).joinToString(",")

        private const val KEY_HIDDEN = "hidden"
    }

    private val prefs = context.getSharedPreferences("contacts", Context.MODE_PRIVATE)

    override fun create(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $CONTACTS_TABLE (" +
                "$CONTACTS_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                "$CONTACTS_NAME TEXT NOT NULL," +
                "$CONTACTS_ADDRESS TEXT NOT NULL," +
                "$CONTACTS_DATE INTEGER NOT NULL," +
                "$CONTACTS_TESTNET INTEGER NOT NULL DEFAULT 0" +
                ")")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        super.onUpgrade(db, oldVersion, newVersion)
        if (oldVersion == 1 && newVersion == 2) {
            db.execSQL("ALTER TABLE $CONTACTS_TABLE ADD COLUMN $CONTACTS_TESTNET INTEGER NOT NULL DEFAULT 0")
        }
    }

    fun getContacts(): List<ContactEntity> {
        val contacts = mutableListOf<ContactEntity>()
        val query = "SELECT $contactsField FROM $CONTACTS_TABLE LIMIT 100"
        val cursor = readableDatabase.rawQuery(query, null)
        val idIndex = cursor.getColumnIndex(CONTACTS_ID)
        val nameIndex = cursor.getColumnIndex(CONTACTS_NAME)
        val addressIndex = cursor.getColumnIndex(CONTACTS_ADDRESS)
        val dateIndex = cursor.getColumnIndex(CONTACTS_DATE)
        val testnetIndex = cursor.getColumnIndex(CONTACTS_TESTNET)
        while (cursor.moveToNext()) {
            contacts.add(ContactEntity(
                id = cursor.getLong(idIndex),
                name = cursor.getString(nameIndex),
                address = cursor.getString(addressIndex),
                date = cursor.getLong(dateIndex),
                testnet = cursor.getLong(testnetIndex) != 0L
            ))
        }
        cursor.close()
        return contacts
    }

    fun addContact(name: String, address: String, testnet: Boolean): ContactEntity {
        val date = currentTimeSeconds()
        val values = ContentValues().apply {
            put(CONTACTS_NAME, name)
            put(CONTACTS_ADDRESS, address)
            put(CONTACTS_DATE, date)
            put(CONTACTS_TESTNET, if (testnet) 1L else 0L)
        }
        val id = writableDatabase.insert(CONTACTS_TABLE, null, values)
        return ContactEntity(id, name, address, date, testnet)
    }

    fun editContact(id: Long, name: String) {
        val values = ContentValues().apply {
            put(CONTACTS_NAME, name)
        }
        writableDatabase.update(CONTACTS_TABLE, values, "$CONTACTS_ID = ?", arrayOf(id.toString()))
    }

    fun deleteContact(id: Long) {
        writableDatabase.delete(CONTACTS_TABLE, "$CONTACTS_ID = ?", arrayOf(id.toString()))
    }

    private fun prefixHidden(accountId: String, testnet: Boolean): String {
        return "$KEY_HIDDEN:$accountId:${if (testnet) "testnet" else "mainnet"}"
    }

    fun isHidden(accountId: String, testnet: Boolean): Boolean {
        return prefs.getBoolean(prefixHidden(accountId, testnet), false)
    }

    fun setHidden(accountId: String, testnet: Boolean, hidden: Boolean) {
        prefs.edit().putBoolean(prefixHidden(accountId, testnet), hidden).apply()
    }
}