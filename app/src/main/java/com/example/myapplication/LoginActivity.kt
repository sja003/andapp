package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.databinding.ActivityLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider


class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    companion object {
        private const val RC_SIGN_IN = 9001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Firebase Auth 초기화
        auth = FirebaseAuth.getInstance()

        // 🔐 Google 로그인 옵션 설정 (Calendar 범위 포함)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // Firebase 콘솔에서 발급받은 client ID
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/calendar"))
            .build()

        // GoogleSignInClient 생성
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // 이메일 로그인
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "이메일과 비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "로그인 성공!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, "로그인 실패: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        // 회원가입 이동
        binding.tvGoToRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        // 🔐 Google 로그인 버튼
        binding.btnGoogleLogin.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            if (task.isSuccessful) {
                val account = task.result
                firebaseAuthWithGoogle(account)
            } else {
                Toast.makeText(this, "Google 로그인 실패: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount?) {
        if (account == null) return

        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // 🔐 Firebase 인증 성공
                    val user = auth.currentUser
                    if (user != null) {
                        val userInfo = hashMapOf(
                            "uid" to user.uid,
                            "email" to user.email,
                            "name" to user.displayName
                        )

                        // 🔥 Firestore에 사용자 정보 저장
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        val userRef = db.collection("users").document(user.uid)

                        userRef.get().addOnSuccessListener { document ->
                            if (!document.exists()) {
                                userRef.set(userInfo)
                                    .addOnSuccessListener {
                                        android.util.Log.d("Firestore", "사용자 정보 저장 완료")
                                    }
                                    .addOnFailureListener { e ->
                                        android.util.Log.e("Firestore", "저장 실패: ${e.message}")
                                    }
                            }
                        }
                    }

                    Toast.makeText(this, "Google 로그인 성공!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Firebase 인증 실패: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

}
