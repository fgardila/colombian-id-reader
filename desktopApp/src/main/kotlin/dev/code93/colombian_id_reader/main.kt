package dev.code93.colombian_id_reader

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "colombian-id-reader",
    ) {
        App()
    }
}