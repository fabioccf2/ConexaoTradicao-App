package com.conexaotradicao.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.google.firebase.auth.FirebaseAuth

/**
 * Ponto de entrada do app: hospeda o fluxo de Login/Cadastro (RF01, Tela 1).
 * Se já existir uma sessão válida, pula direto para a MainActivity.
 */
class AuthActivity : AppCompatActivity(R.layout.activity_auth) {

    override fun onStart() {
        super.onStart()
        if (FirebaseAuth.getInstance().currentUser != null) {
            goToMain()
        }
    }

    fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        fun navHost(activity: AuthActivity) =
            (activity.supportFragmentManager.findFragmentById(R.id.auth_nav_host) as NavHostFragment)
    }
}
