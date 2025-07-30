package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.databinding.ActivityLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    companion object {
        private const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Firebase Auth 초기화
        auth = FirebaseAuth.getInstance()

        // Google Sign-In Launcher 설정 (최신 방식)
        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            Log.d(TAG, "Google Sign-In 결과: ${result.resultCode}")

            if (result.resultCode == RESULT_OK) {
                try {
                    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    val account = task.getResult(ApiException::class.java)

                    Log.d(TAG, "Google Sign-In 성공: ${account?.email}")
                    firebaseAuthWithGoogle(account)

                } catch (e: ApiException) {
                    Log.e(TAG, "Google Sign-In 실패: ${e.statusCode}", e)
                    handleGoogleSignInError(e)
                }
            } else {
                Log.w(TAG, "Google Sign-In 취소됨")
                Toast.makeText(this, "Google 로그인이 취소되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        // Google Sign-In 설정
        setupGoogleSignIn()

        // 버튼 이벤트 설정
        setupClickListeners()
    }

    private fun setupGoogleSignIn() {
        try {
            // Web Client ID 확인
            val webClientId = getString(R.string.default_web_client_id)
            Log.d(TAG, "Web Client ID: $webClientId")

            if (webClientId.isEmpty() || webClientId == "YOUR_WEB_CLIENT_ID_HERE.apps.googleusercontent.com") {
                throw IllegalStateException("Web Client ID가 설정되지 않았습니다. strings.xml을 확인하세요.")
            }

            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .requestProfile()
                .requestScopes(Scope("https://www.googleapis.com/auth/calendar"))
                .build()

            googleSignInClient = GoogleSignIn.getClient(this, gso)

            Log.d(TAG, "Google Sign-In 설정 완료")

        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In 설정 실패", e)
            Toast.makeText(this, "Google 로그인 설정 오류: ${e.message}", Toast.LENGTH_LONG).show()

            // Google 로그인 버튼 비활성화
            setButtonState(binding.btnGoogleLogin, false, "Google 로그인 설정 오류")
        }
    }

    private fun setupClickListeners() {
        // 이메일 로그인
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "이메일과 비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 버튼 상태 변경 - 안전한 방법
            setButtonState(binding.btnLogin, false, "로그인 중...")

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    setButtonState(binding.btnLogin, true, "로그인")

                    if (task.isSuccessful) {
                        Log.d(TAG, "이메일 로그인 성공")
                        Toast.makeText(this, "로그인 성공!", Toast.LENGTH_SHORT).show()
                        navigateToMain()
                    } else {
                        Log.e(TAG, "이메일 로그인 실패", task.exception)
                        Toast.makeText(this, "로그인 실패: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        // 회원가입 이동
        binding.tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Google 로그인
        binding.btnGoogleLogin.setOnClickListener {
            if (binding.btnGoogleLogin.isEnabled) {
                signInWithGoogle()
            }
        }
    }

    private fun signInWithGoogle() {
        try {
            setButtonState(binding.btnGoogleLogin, false, "Google 로그인 중...")

            // 기존 계정 로그아웃 후 새로 로그인
            googleSignInClient.signOut().addOnCompleteListener {
                Log.d(TAG, "이전 Google 계정 로그아웃 완료")

                val signInIntent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)

                Log.d(TAG, "Google Sign-In Intent 실행")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In 시작 실패", e)
            Toast.makeText(this, "Google 로그인 시작 실패: ${e.message}", Toast.LENGTH_LONG).show()
            resetGoogleLoginButton()
        }
    }

    private fun handleGoogleSignInError(e: ApiException) {
        val errorMessage = when (e.statusCode) {
            12501 -> "사용자가 로그인을 취소했습니다."
            12502 -> "네트워크 오류가 발생했습니다. 인터넷 연결을 확인하세요."
            12500 -> "내부 오류가 발생했습니다. 앱 설정을 확인해주세요."
            10 -> "개발자 콘솔에서 SHA-1 인증서를 확인하세요."
            else -> "Google 로그인 실패 (코드: ${e.statusCode}): ${e.message}"
        }

        Log.e(TAG, "Google Sign-In 오류: $errorMessage")
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        resetGoogleLoginButton()
    }

    private fun resetGoogleLoginButton() {
        setButtonState(binding.btnGoogleLogin, true, "Google로 로그인")
    }

    // 안전한 버튼 상태 변경 함수
    private fun setButtonState(button: android.view.View, enabled: Boolean, text: String) {
        try {
            button.isEnabled = enabled

            // 버튼 타입에 따라 다른 방법 시도
            when (button) {
                is android.widget.Button -> {
                    button.text = text
                }
                is com.google.android.material.button.MaterialButton -> {
                    button.text = text
                }
                is androidx.appcompat.widget.AppCompatButton -> {
                    button.text = text
                }
                else -> {
                    // 리플렉션을 사용한 안전한 방법
                    try {
                        val setTextMethod = button.javaClass.getMethod("setText", CharSequence::class.java)
                        setTextMethod.invoke(button, text)
                    } catch (e: Exception) {
                        Log.w(TAG, "버튼 텍스트 설정 실패: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "버튼 상태 변경 실패: ${e.message}")
        }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount?) {
        if (account == null) {
            Log.e(TAG, "Google 계정 정보가 null입니다")
            resetGoogleLoginButton()
            return
        }

        Log.d(TAG, "Firebase 인증 시작: ${account.email}")

        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    Log.d(TAG, "Firebase 인증 성공: ${user?.email}")

                    // Firestore에 사용자 정보 저장
                    saveUserToFirestore(user?.uid, user?.email, user?.displayName, user?.photoUrl?.toString())

                    Toast.makeText(this, "Google 로그인 성공!", Toast.LENGTH_SHORT).show()
                    navigateToMain()

                } else {
                    Log.e(TAG, "Firebase 인증 실패", task.exception)
                    Toast.makeText(this, "Firebase 인증 실패: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    resetGoogleLoginButton()
                }
            }
    }

    private fun saveUserToFirestore(uid: String?, email: String?, name: String?, photoUrl: String?) {
        if (uid == null) return

        val userInfo = hashMapOf(
            "uid" to uid,
            "email" to email,
            "name" to name,
            "photoUrl" to photoUrl,
            "loginMethod" to "google",
            "createdAt" to com.google.firebase.Timestamp.now()
        )

        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(uid)

        userRef.get().addOnSuccessListener { document ->
            if (!document.exists()) {
                userRef.set(userInfo)
                    .addOnSuccessListener {
                        Log.d(TAG, "사용자 정보 Firestore 저장 완료")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Firestore 저장 실패", e)
                    }
            } else {
                Log.d(TAG, "사용자 정보가 이미 존재함")
            }
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onStart() {
        super.onStart()
        // 이미 로그인된 사용자 확인
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "이미 로그인된 사용자: ${currentUser.email}")
            navigateToMain()
        }
    }
}