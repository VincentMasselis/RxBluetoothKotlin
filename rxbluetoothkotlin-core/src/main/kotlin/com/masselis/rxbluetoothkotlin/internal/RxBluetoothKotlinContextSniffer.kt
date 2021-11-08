package com.masselis.rxbluetoothkotlin.internal

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri

private lateinit var _appContext: Context
internal val appContext get() = _appContext

internal class RxBluetoothKotlinContextSniffer : ContentProvider() {

    override fun onCreate(): Boolean {
        _appContext = context!!.applicationContext
        return true
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

}