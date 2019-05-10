package com.vincentmasselis.rxbluetoothkotlin.internal

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/** Inspired by LeakSentryInstaller.kt */
internal class RxBluetoothKotlinContextSniffer : ContentProvider() {

    override fun onCreate(): Boolean {
        ContextHolder.install(context!!.applicationContext)
        return true
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

}