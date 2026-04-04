package com.pixeleye.plantdoctor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CustomSnackbar(
    snackbarData: SnackbarData,
    type: SnackbarType = SnackbarType.INFO
) {
    val backgroundColor = when (type) {
        SnackbarType.SUCCESS -> Color(0xFF2E7D32) // Forest Green
        SnackbarType.ERROR -> Color(0xFFD32F2F)   // Material Red
        SnackbarType.INFO -> MaterialTheme.colorScheme.inverseSurface
    }

    val iconColor = if (type == SnackbarType.INFO) 
        MaterialTheme.colorScheme.inverseOnSurface 
    else 
        Color.White

    val textColor = if (type == SnackbarType.INFO) 
        MaterialTheme.colorScheme.inverseOnSurface 
    else 
        Color.White

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = type.getIcon(),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = snackbarData.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = textColor,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}
