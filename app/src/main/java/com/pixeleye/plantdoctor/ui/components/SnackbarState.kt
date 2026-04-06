package com.pixeleye.plantdoctor.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.vector.ImageVector

enum class SnackbarType {
    SUCCESS,
    ERROR,
    INFO,
    WARNING
}

data class SnackbarState(
    val message: String,
    val type: SnackbarType = SnackbarType.INFO
)

fun SnackbarType.getIcon(): ImageVector {
    return when (this) {
        SnackbarType.SUCCESS -> Icons.Default.CheckCircle
        SnackbarType.ERROR -> Icons.Default.ErrorOutline
        SnackbarType.INFO -> Icons.Default.Info
        SnackbarType.WARNING -> Icons.Default.Info
    }
}
