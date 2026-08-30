package com.conexaotradicao.app.util

object Constants {
    const val COLLECTION_USERS = "users"
    const val COLLECTION_EVENTS = "events"
    const val COLLECTION_CUTS = "cuts"
    const val COLLECTION_PARTICIPATIONS = "participations"
    const val COLLECTION_CHATS = "chats"
    const val COLLECTION_RATINGS = "ratings"

    // RNF06 — carregar a listagem de eventos em até 3s mesmo em 3G/4G
    const val EVENT_LIST_TIMEOUT_MS = 3000L
}
