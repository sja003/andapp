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
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.example.myapplication.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
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

        setupUI()
        setupBottomNavigation()
        setupFAB()

        // 기본 화면: 홈
        loadFragment(HomeFragment())
    }

    private fun setupUI() {
        // 툴바 설정
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "💰 스마트 가계부"

        // 시스템 바 색상 설정
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_color)
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.menu_home -> {
                    binding.toolbar.title = "💰 스마트 가계부"
                    HomeFragment()
                }
                R.id.menu_statistics -> {
                    binding.toolbar.title = "📊 지출 분석"
                    AnalysisFragment()
                }
                R.id.menu_assets -> {
                    binding.toolbar.title = "💼 자산 관리"
                    AssetFragment()
                }
                R.id.menu_login -> {
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        binding.toolbar.title = "👤 프로필"
                        MoreFragment()
                    } else {
                        showLoginPrompt()
                        return@setOnItemSelectedListener false
                    }
                }
                else -> return@setOnItemSelectedListener false
            }

            loadFragment(fragment)
            true
        }
    }

    private fun setupFAB() {
        binding.fab.setOnClickListener {
            showModernAddOptionDialog()
        }
    }

    private fun showModernAddOptionDialog() {
        val options = arrayOf(
            "📝 직접 입력",
            "📷 카메라 촬영",
            "🖼️ 갤러리 선택"
        )

        val descriptions = arrayOf(
            "수동으로 지출 내역을 입력합니다",
            "영수증을 촬영하여 자동으로 분석합니다",
            "저장된 영수증 이미지를 선택합니다"
        )

        MaterialAlertDialogBuilder(this)
            .setTitle("💸 지출 추가")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        loadFragment(SpendingFragment())
                        binding.bottomNavigationView.selectedItemId = R.id.menu_home
                        showSnackbar("✏️ 직접 입력 모드가 활성화되었습니다")
                    }
                    1 -> {
                        checkCameraPermissionAndLaunch()
                    }
                    2 -> {
                        openGallery()
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showLoginPrompt() {
        MaterialAlertDialogBuilder(this)
            .setTitle("🔐 로그인 필요")
            .setMessage("이 기능을 사용하려면 로그인이 필요합니다.")
            .setPositiveButton("로그인") { _, _ ->
                startActivity(Intent(this, LoginActivity::class.java))
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

                showSnackbar("📸 영수증을 선명하게 촬영해주세요")
            }
        } catch (e: Exception) {
            Log.e(TAG, "카메라 실행 실패", e)
            showErrorSnackbar("❌ 카메라 실행에 실패했습니다")
        }
    }

    private fun openGallery() {
        try {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                type = "image/*"
            }

            if (intent.resolveActivity(packageManager) != null) {
                startActivityForResult(intent, GALLERY_REQUEST_CODE)
                showSnackbar("🖼️ 영수증 이미지를 선택해주세요")
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
            showErrorSnackbar("❌ 갤러리 실행에 실패했습니다")
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
                showErrorSnackbar("❌ 카메라 권한이 필요합니다")
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
                        showModernProcessingDialog()
                        processImageFile(file)
                    }
                }

                GALLERY_REQUEST_CODE -> {
                    data?.data?.let { uri ->
                        Log.d(TAG, "갤러리 선택 완료: $uri")
                        showModernProcessingDialog()
                        processImageUri(uri)
                    }
                }
            }
        } else {
            showSnackbar("🚫 이미지 선택이 취소되었습니다")
        }
    }

    private fun showModernProcessingDialog() {
        progressDialog = ProgressDialog(this).apply {
            setTitle("🔍 영수증 분석 중")
            setMessage("AI가 영수증을 분석하고 있습니다...\n잠시만 기다려주세요.")
            setCancelable(false)
            show()
        }

        // 타임아웃 설정
        binding.root.postDelayed({
            hideProcessingDialog()
        }, 30000) // 30초 타임아웃
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

                // OCR 처리 완료 후 로딩 다이얼로그 숨기기
                binding.root.postDelayed({
                    hideProcessingDialog()
                }, 5000)

            } catch (e: Exception) {
                hideProcessingDialog()
                Log.e(TAG, "OCR 처리 실패", e)
                showErrorSnackbar("❌ 영수증 분석에 실패했습니다")
            }
        } else {
            hideProcessingDialog()
            Log.e(TAG, "유효하지 않은 파일: ${file.absolutePath}")
            showErrorSnackbar("❌ 유효하지 않은 이미지 파일입니다")
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
            showErrorSnackbar("❌ 이미지 처리에 실패했습니다")
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right
            )
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.info_color))
            .setTextColor(ContextCompat.getColor(this, android.R.color.white))
            .show()
    }

    private fun showErrorSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.error_color))
            .setTextColor(ContextCompat.getColor(this, android.R.color.white))
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        hideProcessingDialog()
    }
}