package com.jodel.videopocket

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore

object DownloadDestination {
    fun name(context: Context, tree: Uri): String {
        val document = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return context.contentResolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "Selected folder"
    }
    fun create(context: Context, filename: String, mime: String, tree: String): Uri {
        if (tree.isNotEmpty()) {
            val uri = Uri.parse(tree)
            val document = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
            return DocumentsContract.createDocument(context.contentResolver, document, mime, filename)
                ?: throw IllegalStateException("Could not create the file in the selected folder. Choose the folder again.")
        }
        return context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Video Pocket")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }) ?: throw IllegalStateException("Could not create the file in Downloads.")
    }
    fun finish(context: Context, uri: Uri, tree: String) {
        if (tree.isEmpty()) context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
    }
    fun remove(context: Context, uri: Uri, tree: String) {
        if (tree.isEmpty()) context.contentResolver.delete(uri, null, null) else DocumentsContract.deleteDocument(context.contentResolver, uri)
    }
}
