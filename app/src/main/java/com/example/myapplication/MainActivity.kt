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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var photoUri: Uri? = null
    private var photoFile: File? = null

    companion object {
        private const val CAMERA_REQUEST_CODE = 101
        private const val GALLERY_REQUEST_CODE = 102
        private const val CAMERA_PERMISSION_CODE = 100
    }

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
        val options = arrayOf("지출 내역 직접 입력", "카메라로 영수증 촬영", "갤러리에서 영수증 선택")
        AlertDialog.Builder(this)
            .setTitle("추가 방법 선택")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // 직접 입력
                        loadFragment(SpendingFragment())
                    }
                    1 -> {
                        // 카메라 촬영
                        checkCameraPermissionAndLaunch()
                    }
                    2 -> {
                        // 갤러리 선택
                        openGallery()
                    }
                }
            }
            .show()
    }

    private fun checkCameraPermissionAndLaunch() {
        val permission = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), CAMERA_PERMISSION_CODE)
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
            startActivityForResult(intent, CAMERA_REQUEST_CODE)
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        }

        // 갤러리 앱이 있는지 확인
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, GALLERY_REQUEST_CODE)
        } else {
            // 갤러리 앱이 없으면 파일 선택기 사용
            val fileIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(
                Intent.createChooser(fileIntent, "영수증 이미지 선택"),
                GALLERY_REQUEST_CODE
            )
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
        if (requestCode == CAMERA_PERMISSION_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            when (requestCode) {
                CAMERA_REQUEST_CODE -> {
                    // 카메라로 촬영한 경우
                    photoFile?.let { file ->
                        Log.d("OCR", "카메라 촬영 완료: ${file.absolutePath}")
                        processImageFile(file)
                    }
                }

                GALLERY_REQUEST_CODE -> {
                    // 갤러리에서 선택한 경우
                    data?.data?.let { uri ->
                        Log.d("OCR", "갤러리 선택 완료: $uri")
                        processImageUri(uri)
                    }
                }
            }
        }
    }

    private fun processImageFile(file: File) {
        if (file.exists() && file.length() > 0) {
            Log.d("OCR", "파일 처리 시작: ${file.absolutePath}, 크기: ${file.length()} bytes")
            ReceiptOcrProcessor.processImage(this, file)
        } else {
            Log.e("OCR", "유효하지 않은 파일: ${file.absolutePath}")
        }
    }

    private fun processImageUri(uri: Uri) {
        try {
            // URI를 임시 파일로 복사
            val inputStream = contentResolver.openInputStream(uri)
            val tempFile = createImageFile()

            inputStream?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            Log.d("OCR", "갤러리 이미지 복사 완료: ${tempFile.absolutePath}")
            processImageFile(tempFile)

        } catch (e: Exception) {
            Log.e("OCR", "갤러리 이미지 처리 실패: ${e.message}", e)
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}