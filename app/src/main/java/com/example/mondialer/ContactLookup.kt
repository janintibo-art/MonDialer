package com.example.mondialer

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract

/**
 * Retrouve le nom et la photo d'un correspondant à partir de son numéro.
 * Sert aux notifications, pour afficher « Maman » plutôt que 0645511828.
 */
object ContactLookup {

    private val nameCache = HashMap<String, String?>()

    /** Nom enregistré, ou null si le numéro est inconnu ou l'accès refusé. */
    fun name(ctx: Context, number: String?): String? {
        if (number.isNullOrBlank()) return null
        nameCache[number]?.let { return it }
        if (ctx.checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            var found: String? = null
            ctx.contentResolver.query(uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null)?.use { c ->
                if (c.moveToFirst()) found = c.getString(0)
            }
            if (!found.isNullOrBlank()) nameCache[number] = found
            found
        } catch (e: Exception) {
            null
        }
    }

    /** Nom si connu, sinon le numéro tel quel : toujours quelque chose à afficher. */
    fun displayName(ctx: Context, number: String?): String =
        name(ctx, number) ?: number ?: ctx.getString(R.string.hidden_number)

    /** Photo du contact, pour illustrer la notification. */
    fun photo(ctx: Context, number: String?): Bitmap? {
        if (number.isNullOrBlank()) return null
        if (ctx.checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            var photoUri: String? = null
            ctx.contentResolver.query(uri,
                arrayOf(ContactsContract.PhoneLookup.PHOTO_URI),
                null, null, null)?.use { c ->
                if (c.moveToFirst()) photoUri = c.getString(0)
            }
            val p = photoUri ?: return null
            ctx.contentResolver.openInputStream(Uri.parse(p))?.use {
                BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun clearCache() = nameCache.clear()
}
