package com.conexaotradicao.app.data.repository

import com.conexaotradicao.app.data.local.UserDao
import com.conexaotradicao.app.data.model.User
import com.conexaotradicao.app.data.model.UserRole
import com.conexaotradicao.app.util.Constants
import com.conexaotradicao.app.util.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Cadastro/login (RF01) via Firebase Authentication (e-mail/senha e Google).
 * Ao logar/cadastrar com sucesso, garante o documento do usuário no Firestore
 * e espelha em Room para uso offline (RF02, RNF02).
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val userDao: UserDao
) {

    val currentUserId: String? get() = auth.currentUser?.uid

    suspend fun login(email: String, password: String): Resource<Unit> = try {
        auth.signInWithEmailAndPassword(email, password).await()
        syncCurrentUserToLocal()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.message ?: "Não foi possível entrar. Verifique e-mail e senha.", e)
    }

    /**
     * Garante que o usuário autenticado no momento tenha os dados espelhados no Room local.
     * Cobre dois casos que antes ficavam sem isso: login comum de e-mail/senha (que só
     * autentica, sem copiar o perfil do Firestore) e reabrir o app com uma sessão do
     * Firebase já persistida num aparelho onde o banco local foi limpo/recriado (troca de
     * versão do schema, app reinstalado, etc.). Sem isso, telas que dependem do Room pro
     * usuário logado (Editar Perfil, nome de quem mandou mensagem no chat) ficam com dado
     * em branco/genérico mesmo a pessoa estando autenticada normalmente.
     */
    suspend fun syncCurrentUserToLocal() {
        val uid = auth.currentUser?.uid ?: return
        if (userDao.getById(uid) != null) return
        runCatching {
            val snapshot = firestore.collection(Constants.COLLECTION_USERS).document(uid).get().await()
            val user = snapshot.toObject(User::class.java) ?: return@runCatching
            userDao.upsert(user)
        }
    }

    suspend fun register(name: String, email: String, password: String): Resource<Unit> = try {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: throw IllegalStateException("Falha ao criar usuário")
        val user = User(id = uid, name = name, email = email, role = UserRole.COMPRADOR)
        firestore.collection(Constants.COLLECTION_USERS).document(uid).set(user).await()
        userDao.upsert(user)
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.message ?: "Não foi possível concluir o cadastro.", e)
    }

    /** RF01 — login/cadastro com Google: cria o usuário no Firestore/Room só no primeiro acesso. */
    suspend fun signInWithGoogle(idToken: String): Resource<Unit> = try {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        val firebaseUser = result.user ?: throw IllegalStateException("Falha ao entrar com Google")
        val uid = firebaseUser.uid

        val existing = firestore.collection(Constants.COLLECTION_USERS).document(uid).get().await()
        val user = if (existing.exists()) {
            existing.toObject(User::class.java) ?: User(id = uid)
        } else {
            val newUser = User(
                id = uid,
                name = firebaseUser.displayName ?: "Usuário",
                email = firebaseUser.email ?: "",
                photoUrl = firebaseUser.photoUrl?.toString(),
                role = UserRole.COMPRADOR
            )
            firestore.collection(Constants.COLLECTION_USERS).document(uid).set(newUser).await()
            newUser
        }
        userDao.upsert(user)
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.message ?: "Não foi possível entrar com o Google.", e)
    }

    fun logout() = auth.signOut()
}
