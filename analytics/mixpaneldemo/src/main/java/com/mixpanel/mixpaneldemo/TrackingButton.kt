package com.mixpanel.mixpaneldemo

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun TrackingButton(
    title: String,
    message: String,
    showDialog: MutableState<Boolean>,
    dialogMessage: MutableState<String>,
    onButtonClick: () -> Unit
) {
    val context = LocalContext.current
    Button(
        onClick = {
            dialogMessage.value = message
            showDialog.value = true
            // Handle button click
            println("Button clicked: $title")
            onButtonClick()
        },
        colors = ButtonDefaults.buttonColors(containerColor = Color(red = 123, green = 128, blue = 255)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(text = title)
    }
}