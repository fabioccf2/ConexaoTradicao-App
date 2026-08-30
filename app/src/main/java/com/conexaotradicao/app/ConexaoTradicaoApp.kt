package com.conexaotradicao.app

import android.app.Application
import com.conexaotradicao.app.data.local.AppDatabase

class ConexaoTradicaoApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        // FirebaseApp.initializeApp(this) é chamado automaticamente pelo content provider
        // do SDK do Firebase a partir do google-services.json presente em app/.
    }
}
