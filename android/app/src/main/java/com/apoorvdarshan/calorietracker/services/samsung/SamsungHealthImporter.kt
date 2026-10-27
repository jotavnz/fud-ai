package com.apoorvdarshan.calorietracker.services.samsung

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.apoorvdarshan.calorietracker.AppContainer
import com.apoorvdarshan.calorietracker.services.health.ExternalBodyFat
import com.apoorvdarshan.calorietracker.services.health.ExternalWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** What one Samsung Health import added. */
data class SamsungImportResult(
    val files: Int,
    val stepDays: Int,
    val nights: Int,
    val workouts: Int,
    val weights: Int,
    val oldest: LocalDate?
)

/**
 * Imports the folder Samsung Health writes with "Download personal data" (picked by the user
 * with the system folder picker). Every value keeps a stable id, so importing the same or a
 * newer download again adds only what is new.
 */
class SamsungHealthImporter(private val container: AppContainer) {

    suspend fun import(tree: Uri): SamsungImportResult {
        val context = container.appContext
        val (data, files) = withContext(Dispatchers.IO) { read(context, tree) }
        val prefs = container.prefs
        prefs.mergeSamsungDailySteps(data.stepsByDay)
        prefs.addImportedSleep(data.sleep)
        prefs.addSamsungWorkouts(data.workouts)
        container.weightRepository.addMissingExternalWeights(
            data.weights.map { (time, kg) -> ExternalWeight(time, kg, clientRecordId = "samsung-weight-${time.epochSecond}") }
        )
        container.bodyFatRepository.addMissingExternalBodyFats(
            data.bodyFats.map { (time, fraction) -> ExternalBodyFat(time, fraction, clientRecordId = "samsung-bodyfat-${time.epochSecond}") }
        )
        // Steps, sleep and recovery read these on their next pass.
        container.healthRefreshTick.value += 1
        return SamsungImportResult(
            files = files,
            stepDays = data.stepsByDay.size,
            nights = data.sleep.size,
            workouts = data.workouts.size,
            weights = data.weights.size,
            oldest = data.oldest
        )
    }

    /** Every CSV the app understands in the folder and one level of subfolders. */
    private fun read(context: Context, tree: Uri): Pair<SamsungHealthData, Int> {
        var data = SamsungHealthData()
        var files = 0
        fun visit(documentId: String, depth: Int) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
            context.contentResolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val name = cursor.getString(1).orEmpty()
                    val mime = cursor.getString(2)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (depth < 2) visit(id, depth + 1)
                        continue
                    }
                    val kind = SamsungHealthExport.kindOf(name) ?: continue
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    val text = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: continue
                    data += SamsungHealthExport.parse(kind, text)
                    files++
                }
            }
        }
        visit(DocumentsContract.getTreeDocumentId(tree), 0)
        return data to files
    }
}
