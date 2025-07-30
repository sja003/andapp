package com.example.myapplication

import android.Manifest
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
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
    private var progressDialog: ProgressDialog? = null

    companion object {
        private const val CAMERA_REQUEST_CODE = 101
        private const val GALLERY_REQUEST_CODE = 102
        private const val CAMERA_PERMISSION_CODE = 100
        private const val TAG = "MainActivity"
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
                        true
                    } else {
                        startActivity(Intent(this, LoginActivity::class.java))
                        false
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
        val options = arrayOf(
            "📝 지출 내역 직접 입력",
            "📷 카메라로 영수증 촬영",
            "🖼️ 갤러리에서 영수증 선택"
        )

        AlertDialog.Builder(this)
            .setTitle("💰 지출 추가 방법")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // 직접 입력
                        loadFragment(SpendingFragment())
                        Toast.makeText(this, "✏️ 직접 입력 모드", Toast.LENGTH_SHORT).show()
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
            .setNegativeButton("취소", null)
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
        try {
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

                Toast.makeText(this, "📸 영수증을 촬영해주세요", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "카메라 실행 실패", e)
            Toast.makeText(this, "❌ 카메라 실행 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openGallery() {
        try {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                type = "image/*"
            }

            if (intent.resolveActivity(packageManager) != null) {
                startActivityForResult(intent, GALLERY_REQUEST_CODE)
                Toast.makeText(this, "🖼️ 영수증 이미지를 선택해주세요", Toast.LENGTH_SHORT).show()
            } else {
                val fileIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                startActivityForResult(
                    Intent.createChooser(fileIntent, "영수증 이미지 선택"),
                    GALLERY_REQUEST_CODE
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "갤러리 실행 실패", e)
            Toast.makeText(this, "❌ 갤러리 실행 실패: ${e.message}", Toast.LENGTH_LONG).show()
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
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCamera()
            } else {
                Toast.makeText(this, "❌ 카메라 권한이 필요합니다", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            when (requestCode) {
                CAMERA_REQUEST_CODE -> {
                    photoFile?.let { file ->
                        Log.d(TAG, "카메라 촬영 완료: ${file.absolutePath}")
                        showProcessingDialog()
                        processImageFile(file)
                    }
                }

                GALLERY_REQUEST_CODE -> {
                    data?.data?.let { uri ->
                        Log.d(TAG, "갤러리 선택 완료: $uri")
                        showProcessingDialog()
                        processImageUri(uri)
                    }
                }
            }
        } else {
            Toast.makeText(this, "🚫 이미지 선택이 취소되었습니다", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showProcessingDialog() {
        progressDialog = ProgressDialog(this).apply {
            setTitle("🔍 영수증 분석 중")
            setMessage("잠시만 기다려주세요...")
            setCancelable(false)
            show()
        }

        // 5초 후 자동으로 닫기 (API 응답이 없을 경우 대비)
        binding.root.postDelayed({
            hideProcessingDialog()
        }, 5000)
    }

    private fun hideProcessingDialog() {
        progressDialog?.let {
            if (it.isShowing) {
                it.dismiss()
            }
        }
        progressDialog = null
    }

    private fun processImageFile(file: File) {
        if (file.exists() && file.length() > 0) {
            Log.d(TAG, "파일 처리 시작: ${file.absolutePath}, 크기: ${file.length()} bytes")

            try {
                ReceiptOcrProcessor.processImage(this, file)

                // OCR 처리 완료 후 로딩 다이얼로그 숨기기 (3초 후)
                binding.root.postDelayed({
                    hideProcessingDialog()
                }, 3000)

            } catch (e: Exception) {
                hideProcessingDialog()
                Log.e(TAG, "OCR 처리 실패", e)
                Toast.makeText(this, "❌ OCR 처리 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            hideProcessingDialog()
            Log.e(TAG, "유효하지 않은 파일: ${file.absolutePath}")
            Toast.makeText(this, "❌ 유효하지 않은 이미지 파일", Toast.LENGTH_LONG).show()
        }
    }

    private fun processImageUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val tempFile = createImageFile()

            inputStream?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "갤러리 이미지 복사 완료: ${tempFile.absolutePath}")
            processImageFile(tempFile)

        } catch (e: Exception) {
            hideProcessingDialog()
            Log.e(TAG, "갤러리 이미지 처리 실패", e)
            Toast.makeText(this, "❌ 이미지 처리 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onDestroy() {
        super.onDestroy()
        hideProcessingDialog()
    }
}