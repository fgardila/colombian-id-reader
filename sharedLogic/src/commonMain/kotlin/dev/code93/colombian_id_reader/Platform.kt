package dev.code93.colombian_id_reader

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform