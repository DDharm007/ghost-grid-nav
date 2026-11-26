package com.example.ghostgrid.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ghostgrid.ui.theme.GhostBorderAccent
import com.example.ghostgrid.ui.theme.GhostTextPrimary
import java.io.File

@Composable
fun ProfileAvatar(
    photoPath: String,
    displayName: String,
    size: Dp = 36.dp,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val fileTimestamp = remember(photoPath) {
        if (photoPath.isNotBlank()) File(photoPath).lastModified() else 0L
    }
    val bitmap = remember(photoPath, fileTimestamp) {
        if (photoPath.isNotBlank() && File(photoPath).exists()) {
            runCatching { BitmapFactory.decodeFile(photoPath)?.asImageBitmap() }.getOrNull()
        } else null
    }

    val initials = remember(displayName) {
        displayName.trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(listOf(Color(0xFF2B2B2F), Color(0xFF141416)))
            )
            .border(1.dp, if (bitmap != null) Color.White else GhostBorderAccent, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Local profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else if (initials.isNotEmpty()) {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelMedium,
                color = GhostTextPrimary
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = "Local profile",
                tint = Color(0xFFE4E4E7),
                modifier = Modifier.size(size * 0.54f)
            )
        }
    }
}
