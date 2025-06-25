package com.example.myapplication

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.example.myapplication.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.example.myapplication.ReceiptOcrProcessor


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var photoUri: Uri? = null
    private var photoFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 기본 화면: 홈
        loadFragment(HomeFragment())

        // 하단 탭 처리
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_home -> {
                    loadFragment(HomeFragment())
                    true
                }

                R.id.menu_statistics -> {
                    loadFragment(AnalysisFragment())
                    true
                }

                R.id.menu_assets -> {
                    loadFragment(AssetFragment())
                    true
                }

                R.id.menu_login -> {
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        loadFragment(MoreFragment())
                        true  // ✅ 로그인된 경우: MoreFragment로 이동
                    } else {
                        startActivity(Intent(this, LoginActivity::class.java))
                        false // ✅ 로그인 필요
                    }
                }

                else -> false
            }
        }

        // + 버튼 눌렀을 때
        binding.fab.setOnClickListener {
            showAddOptionDialog()
        }
    }

    private fun showAddOptionDialog() {
        val options = arrayOf("지출 내역 직접 입력", "영수증 인식")
        AlertDialog.Builder(this)
            .setTitle("추가 방법 선택")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        loadFragment(SpendingFragment())
                    }
                    1 -> {
                        checkCameraPermissionAndLaunch()
                    }
                }
            }
            .show()
    }

    private fun checkCameraPermissionAndLaunch() {
        val permission = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), 100)
        } else {
            launchCamera()
        }
    }

    private fun launchCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        photoFile = createImageFile()

        photoFile?.let { file ->
            photoUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            startActivityForResult(intent, 101)
        }
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "receipt_$timeStamp.jpg"
        val storageDir: File = externalCacheDir ?: cacheDir
        return File(storageDir, fileName)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 101 && resultCode == RESULT_OK) {
            photoFile?.let { file ->
                Log.d("OCR", "촬영된 이미지 경로: ${file.absolutePath}")
                Log.d("OCR", "이미지 파일 존재 여부: ${file.exists()}, 크기: ${file.length()} bytes")

                ReceiptOcrProcessor.processImage(this, file)
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
