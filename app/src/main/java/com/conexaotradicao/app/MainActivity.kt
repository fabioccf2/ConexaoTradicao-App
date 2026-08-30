package com.conexaotradicao.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.conexaotradicao.app.data.repository.ChatNotifier
import com.conexaotradicao.app.data.repository.RepositoryProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Tela principal pós-login: navegação inferior entre Eventos (Home, RF03/RF04)
 * e Perfil (RF02/RF10). Detalhe do evento e chat são empilhados por cima do fluxo Home.
 */
class MainActivity : AppCompatActivity(R.layout.activity_main) {

    private var chatNotifier: ChatNotifier? = null
    // Escopo próprio (em vez de lifecycleScope, que exigiria adicionar a dependência
    // lifecycle-runtime-ktx) só pra sincronizar o perfil local — cancelado junto com onStop.
    private var syncJob = Job()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* segue sem notificação se negar */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.main_nav_host) as NavHostFragment
        val navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setupWithNavController(navController)

        // RF11 — permissão de notificação é necessária a partir do Android 13 (API 33).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        // RF11 — liga o "ouvinte" de mensagens novas do chat enquanto o app está em uso.
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            chatNotifier = ChatNotifier(applicationContext, uid).also { it.start() }
            // Garante que o perfil do usuário logado esteja espelhado no Room local — cobre
            // o caso de uma sessão do Firebase já persistida (login feito antes) reabrir
            // num banco local vazio/recriado, sem passar de novo pela tela de login.
            // Mesma lógica pras participações (RF07): sem isso, uma carneação agendada de
            // verdade no Firestore podia sumir de "Meus Próximos Eventos"/"Histórico" se o
            // banco local fosse recriado (ver EventRepository.syncParticipationsToLocal).
            syncJob = Job()
            CoroutineScope(Dispatchers.Main.immediate + syncJob).launch {
                RepositoryProvider.authRepository(applicationContext).syncCurrentUserToLocal()
                RepositoryProvider.eventRepository(applicationContext).syncParticipationsToLocal(uid)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        chatNotifier?.stop()
        chatNotifier = null
        syncJob.cancel()
    }
}
