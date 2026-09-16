package com.interlinedlist.android.core.appsettings.device

import android.os.Build
import com.interlinedlist.android.core.common.device.DeviceLabelProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's one device label, derived from `Build.MODEL`.
 *
 * This is the single producer of the string that used to be built inline in
 * `DefaultAuthRepository` for `sync-token`'s `deviceLabel`; `:feature:auth` now injects
 * [DeviceLabelProvider] instead, so Settings → Sessions and Settings → Applications
 * name the same phone identically. The format is unchanged
 * (`InterlinedList Android · Pixel 8`) so existing sessions keep reading the same.
 */
@Singleton
class BuildDeviceLabelProvider @Inject constructor() : DeviceLabelProvider {

    override val deviceLabel: String = labelFor(Build.MODEL)

    companion object {
        /** The app half of the label, used when the device reports no model. */
        const val APP_NAME = "InterlinedList Android"

        /**
         * Builds `"<app> · <model>"`, trimmed to the registry's 120-character limit and
         * falling back to the app name alone when the model is missing or blank.
         */
        fun labelFor(model: String?): String {
            val trimmedModel = model?.trim().orEmpty()
            val label = if (trimmedModel.isEmpty()) APP_NAME else "$APP_NAME · $trimmedModel"
            return label.take(DeviceLabelProvider.MAX_LENGTH)
        }
    }
}
