package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Firebase 인증 객체 초기화
        auth = FirebaseAuth.getInstance()

        // 회원가입 버튼 클릭 이벤트
        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            // 비어 있는지 확인
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "이메일과 비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Firebase 회원가입 요청
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val uid = auth.currentUser?.uid ?: return@addOnCompleteListener
                        val db = Firebase.firestore

                        // 저장할 사용자 정보
                        val user = hashMapOf(
                            "email" to email,
                            "createdAt" to com.google.firebase.Timestamp.now()
                        )

                        db.collection("users").document(uid)
                            .set(user)
                            .addOnSuccessListener {
                                Log.d("Firestore", "사용자 정보 저장 성공")
                            }
                            .addOnFailureListener { e ->
                                Log.w("Firestore", "사용자 정보 저장 실패", e)
                            }

                        Toast.makeText(this, "회원가입 성공!", Toast.LENGTH_SHORT).show()
                        finish() // 현재 액티비티 종료 (이전 화면으로)
                    } else {
                        Toast.makeText(this, "회원가입 실패: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }
}
