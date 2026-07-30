package com.example.mondialer

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat

/**
 * Champ de saisie capable de recevoir des contenus riches depuis le clavier :
 * c'est ainsi que la recherche de GIF intégrée au clavier (Gboard et autres)
 * transmet l'image choisie à l'application, sans aucune clé d'API.
 */
class RichEditText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : EditText(context, attrs, defStyle) {

    /** Appelé avec l'URI du contenu reçu (GIF, image, autocollant). */
    var onRichContent: ((Uri, String) -> Unit)? = null

    override fun onCreateInputConnection(editorInfo: EditorInfo): InputConnection? {
        val ic = super.onCreateInputConnection(editorInfo) ?: return null
        EditorInfoCompat.setContentMimeTypes(editorInfo,
            arrayOf("image/gif", "image/png", "image/jpeg", "image/webp"))

        val callback = InputConnectionCompat.OnCommitContentListener {
            info: InputContentInfoCompat, flags: Int, _: Bundle? ->
            // Sur les versions récentes, il faut demander l'accès au contenu
            if (flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION != 0) {
                try { info.requestPermission() } catch (e: Exception) { return@OnCommitContentListener false }
            }
            val mime = info.description.getMimeType(0) ?: "image/gif"
            onRichContent?.invoke(info.contentUri, mime)
            true
        }
        return InputConnectionCompat.createWrapper(ic, editorInfo, callback)
    }
}
