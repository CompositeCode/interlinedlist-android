package com.interlinedlist.android.feature.integrations.data

import java.io.File

/**
 * Where downloaded CSVs are written. Abstracted behind an interface so the
 * repository can be unit-tested against a temp directory without an Android
 * `Context`; in production it points at the app's cache dir (see the Hilt
 * module), which the FileProvider then exposes to the share sheet.
 */
interface ExportFileStore {
    /** The directory CSV files are written into (created if missing). */
    fun exportsDir(): File
}
