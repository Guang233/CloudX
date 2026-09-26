package com.guang.cloudx.logic.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

internal data class LocalDocument(
    val uri: String,
    val name: String,
)

internal interface LocalDocuments {
    /** Must throw on permission/provider failure, not return an empty listing. */
    fun children(tree: String): List<LocalDocument>

    fun delete(uri: String): Boolean
}

internal class SafLocalDocuments(
    context: Context,
) : LocalDocuments {
    private val resolver = context.contentResolver

    override fun children(tree: String): List<LocalDocument> {
        val uri = Uri.parse(tree)
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(
                uri,
                DocumentsContract.getTreeDocumentId(uri),
            )
        val projection =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
        return resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            check(!cursor.extras.getBoolean(DocumentsContract.EXTRA_LOADING, false)) {
                "下载目录仍在加载"
            }
            check(cursor.extras.getString(DocumentsContract.EXTRA_ERROR).isNullOrBlank()) {
                "下载目录暂不可用"
            }
            buildList {
                while (cursor.moveToNext()) {
                    if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    add(
                        LocalDocument(
                            DocumentsContract.buildDocumentUriUsingTree(uri, cursor.getString(0)).toString(),
                            cursor.getString(1).orEmpty(),
                        ),
                    )
                }
            }
        } ?: throw IllegalStateException("无法读取下载目录，请检查目录权限")
    }

    override fun delete(uri: String): Boolean = DocumentsContract.deleteDocument(resolver, Uri.parse(uri))
}
