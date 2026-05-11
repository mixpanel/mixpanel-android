package com.mixpanel.sessionreplaydemo.data

import androidx.compose.ui.graphics.Color

data class Joke(
    val id: String,
    val setup: String,
    val punchline: String,
    val category: String,
    val thumbnailColor: Color,
    val likeCount: Int,
    val comments: List<Comment>
)
