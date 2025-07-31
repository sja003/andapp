package com.example.myapplication

import android.app.AlertDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.Date
import android.graphics.Bitmap

object ReceiptOcrProcessor {

    // 네이버 클로바 OCR Secret Key
    private const val CLIENT_SECRET = "Q1h2UVhkdFNBYkxIQ1dXVEVtS0d6eHBlWVJTWHhraVQ="

    fun processImage(context: Context, imageFile: File) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("OCR_DEBUG", "=== OCR 처리 시작 ===")
                Log.d("OCR_DEBUG", "파일 존재: ${imageFile.exists()}")
                Log.d("OCR_DEBUG", "파일 크기: ${imageFile.length()} bytes")
                Log.d("OCR_DEBUG", "파일 경로: ${imageFile.absolutePath}")

                // UI에 처리 시작 알림
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "🔍 영수증 분석 중...", Toast.LENGTH_SHORT).show()
                }

                if (!imageFile.exists() || imageFile.length() == 0L) {
                    Log.e("OCR_ERROR", "❌ 파일이 존재하지 않거나 비어있습니다")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "❌ 이미지 파일 오류", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // 이미지 로드 및 압축
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 2
                }

                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath, options)
                if (bitmap == null) {
                    Log.e("OCR_ERROR", "❌ 이미지 디코딩 실패")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "❌ 이미지 읽기 실패", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                Log.d("OCR_DEBUG", "✅ 이미지 로드 성공: ${bitmap.width}x${bitmap.height}")

                // JPEG로 압축
                val outputStream = ByteArrayOutputStream()
                val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)

                if (!compressed) {
                    Log.e("OCR_ERROR", "❌ 이미지 압축 실패")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "❌ 이미지 압축 실패", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val imageBytes = outputStream.toByteArray()
                val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

                Log.d("OCR_DEBUG", "✅ Base64 인코딩 완료: ${base64Image.length} chars")

                // OCR API 요청 JSON
                val requestId = UUID.randomUUID().toString()
                val timestamp = System.currentTimeMillis()

                val jsonObject = mapOf(
                    "version" to "V2",
                    "requestId" to requestId,
                    "timestamp" to timestamp,
                    "images" to listOf(
                        mapOf(
                            "name" to "receipt_${timestamp}",
                            "format" to "jpg",
                            "data" to base64Image
                        )
                    )
                )

                val json = Gson().toJson(jsonObject)
                Log.d("OCR_DEBUG", "✅ JSON 생성 완료")

                // Retrofit 요청
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val requestBody: RequestBody = json.toRequestBody(mediaType)

                val retrofit = RetrofitInstance.getInstance()
                val service = retrofit.create(NaverOcrService::class.java)

                Log.d("OCR_DEBUG", "🚀 API 호출 시작...")

                val call = service.requestOcr(
                    body = requestBody,
                    secret = CLIENT_SECRET
                )

                call.enqueue(object : Callback<OcrResponse> {
                    override fun onResponse(call: Call<OcrResponse>, response: Response<OcrResponse>) {
                        Log.d("OCR_DEBUG", "=== API 응답 수신 ===")
                        Log.d("OCR_DEBUG", "상태 코드: ${response.code()}")

                        if (response.isSuccessful) {
                            val result = response.body()

                            if (result?.images?.isNotEmpty() == true) {
                                val extractedTexts = mutableListOf<String>()

                                result.images.forEach { image ->
                                    image.fields.forEach { field ->
                                        val text = field.inferText.trim()
                                        if (text.isNotEmpty()) {
                                            extractedTexts.add(text)
                                        }
                                    }
                                }

                                val fullOcrText = extractedTexts.joinToString("\n")

                                Log.d("OCR_SUCCESS", "✅ OCR 성공!")
                                Log.d("OCR_SUCCESS", "추출된 텍스트 수: ${extractedTexts.size}")
                                Log.d("OCR_SUCCESS", "전체 OCR 결과:\n$fullOcrText")

                                if (fullOcrText.isNotEmpty()) {
                                    // UI에 결과 표시
                                    CoroutineScope(Dispatchers.Main).launch {
                                        showOcrResultDialog(context, fullOcrText, extractedTexts.size)
                                    }
                                } else {
                                    CoroutineScope(Dispatchers.Main).launch {
                                        Toast.makeText(context, "⚠️ 텍스트를 인식하지 못했습니다", Toast.LENGTH_LONG).show()
                                    }
                                }

                            } else {
                                Log.w("OCR_WARNING", "⚠️ OCR 결과 이미지가 없습니다")
                                CoroutineScope(Dispatchers.Main).launch {
                                    Toast.makeText(context, "⚠️ 영수증에서 텍스트를 찾을 수 없습니다", Toast.LENGTH_LONG).show()
                                }
                            }

                        } else {
                            val errorBody = response.errorBody()?.string()
                            Log.e("OCR_ERROR", "❌ API 호출 실패")
                            Log.e("OCR_ERROR", "상태 코드: ${response.code()}")
                            Log.e("OCR_ERROR", "에러 바디: $errorBody")

                            CoroutineScope(Dispatchers.Main).launch {
                                Toast.makeText(context, "❌ OCR API 오류: ${response.code()}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                    override fun onFailure(call: Call<OcrResponse>, t: Throwable) {
                        Log.e("OCR_ERROR", "❌ 네트워크 오류")
                        Log.e("OCR_ERROR", "오류 메시지: ${t.message}")
                        t.printStackTrace()

                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, "❌ 네트워크 오류: ${t.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                })

            } catch (e: Exception) {
                Log.e("OCR_ERROR", "❌ 처리 중 예외 발생")
                Log.e("OCR_ERROR", "예외 메시지: ${e.message}")
                e.printStackTrace()

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "❌ 처리 오류: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showOcrResultDialog(context: Context, fullText: String, textCount: Int) {
        // OCR 결과를 파싱
        val parsedData = parseOcrText(fullText)

        val dialogMessage = buildString {
            append("🎉 영수증 분석이 완료되었습니다!\n\n")

            // 카테고리 정보
            append("📂 **카테고리**\n")
            append("   ${parsedData.detectedCategory}\n\n")

            // 총액 정보
            append("💰 **총액**\n")
            append("   ${String.format("%,d", parsedData.finalAmount)}원\n\n")

            // 메뉴 정보
            if (parsedData.menuItems.isNotEmpty()) {
                append("🍽️ **인식된 메뉴**\n")
                parsedData.menuItems.forEachIndexed { index, item ->
                    if (index < 5) { // 최대 5개만 표시
                        append("   • ${item.name}: ${String.format("%,d", item.price)}원\n")
                    }
                }
                if (parsedData.menuItems.size > 5) {
                    append("   • 외 ${parsedData.menuItems.size - 5}개 항목\n")
                }
                append("\n")
            } else {
                append("🍽️ **메뉴**\n")
                append("   개별 메뉴를 인식하지 못했습니다\n\n")
            }

            // 상세 정보
            append("📊 **상세 정보**\n")
            append("   • 개별 메뉴 합계: ${String.format("%,d", parsedData.itemsTotal)}원\n")
            if (parsedData.receiptTotal > 0) {
                append("   • 영수증 총액: ${String.format("%,d", parsedData.receiptTotal)}원\n")
            }
            append("   • 인식된 텍스트: ${textCount}개\n\n")

            append("✅ 가계부에 자동 저장되었습니다!")
        }

        AlertDialog.Builder(context)
            .setTitle("📄 영수증 분석 결과")
            .setMessage(dialogMessage)
            .setPositiveButton("확인") { dialog, _ ->
                dialog.dismiss()
                // 저장 완료 토스트를 더 상세하게
                val successMessage = "${parsedData.detectedCategory} • ${String.format("%,d", parsedData.finalAmount)}원 저장 완료!"
                Toast.makeText(context, successMessage, Toast.LENGTH_LONG).show()
            }
            .setNeutralButton("전체 텍스트 보기") { _, _ ->
                showFullTextDialog(context, fullText)
            }
            .setNegativeButton("수정하기") { _, _ ->
                // 수정 다이얼로그 호출
                showEditDialog(context, parsedData)
            }
            .setCancelable(false)
            .show()

        // 자동으로 Firestore에 저장
        saveToFirestore(parsedData)
    }

    // 수정 다이얼로그 추가
    private fun showEditDialog(context: Context, parsedData: ParsedOcrData) {
        // 간단한 LinearLayout을 코드로 생성 (레이아웃 파일 없이)
        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        // 카테고리 레이블
        val categoryLabel = android.widget.TextView(context).apply {
            text = "카테고리"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        }

        // 카테고리 스피너
        val categorySpinner = Spinner(context).apply {
            val categories = listOf("식비", "카페", "교통", "쇼핑", "문화생활", "의료", "기타")
            val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, categories)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            this.adapter = adapter

            // 현재 값으로 초기화
            val currentCategoryIndex = categories.indexOf(parsedData.detectedCategory)
            if (currentCategoryIndex >= 0) {
                setSelection(currentCategoryIndex)
            }
            setPadding(0, 0, 0, 30)
        }

        // 금액 레이블
        val amountLabel = android.widget.TextView(context).apply {
            text = "금액"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        }

        // 금액 입력
        val amountEditText = EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "금액을 입력하세요"
            setText(parsedData.finalAmount.toString())
            setPadding(0, 0, 0, 30)
        }

        // 메모 레이블
        val memoLabel = android.widget.TextView(context).apply {
            text = "메모"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        }

        // 메모 입력
        val memoEditText = EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            hint = "메모를 입력하세요"
            maxLines = 3

            // 메뉴 요약을 메모에 설정
            val menuSummary = if (parsedData.menuItems.isNotEmpty()) {
                parsedData.menuItems.take(3).joinToString(", ") { it.name }
            } else {
                "OCR 인식 항목"
            }
            setText(menuSummary)
        }

        // 레이아웃에 뷰 추가
        layout.addView(categoryLabel)
        layout.addView(categorySpinner)
        layout.addView(amountLabel)
        layout.addView(amountEditText)
        layout.addView(memoLabel)
        layout.addView(memoEditText)

        AlertDialog.Builder(context)
            .setTitle("✏️ 지출 내역 수정")
            .setView(layout)
            .setPositiveButton("저장") { _, _ ->
                val newCategory = categorySpinner.selectedItem.toString()
                val newAmount = amountEditText.text.toString().toIntOrNull() ?: parsedData.finalAmount
                val newMemo = memoEditText.text.toString()

                // 수정된 데이터로 다시 저장
                updateFirestoreData(context, newCategory, newAmount, newMemo, parsedData)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // Firestore 업데이트 함수
    private fun updateFirestoreData(
        context: Context,
        category: String,
        amount: Int,
        memo: String,
        originalData: ParsedOcrData
    ) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val data = mapOf(
            "amount" to amount,
            "category" to category,
            "asset" to "OCR 인식",
            "memo" to memo,
            "date" to Date(),
            "ocrDetails" to mapOf(
                "items" to originalData.menuItems.map { mapOf("name" to it.name, "price" to it.price) },
                "itemsTotal" to originalData.itemsTotal,
                "receiptTotal" to originalData.receiptTotal,
                "originalCategory" to originalData.detectedCategory,
                "userModified" to true
            )
        )

        Firebase.firestore
            .collection("users")
            .document(uid)
            .collection("spending")
            .add(data)
            .addOnSuccessListener {
                Toast.makeText(context, "✅ 수정된 내역이 저장되었습니다!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "❌ 저장 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showFullTextDialog(context: Context, fullText: String) {
        AlertDialog.Builder(context)
            .setTitle("📝 인식된 전체 텍스트")
            .setMessage(fullText)
            .setPositiveButton("닫기") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun parseOcrText(ocrText: String): ParsedOcrData {
        val lines = ocrText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val menuItems = mutableListOf<MenuItem>()

        Log.d("OCR_PARSE", "=== 메뉴 추출 시작 ===")
        Log.d("OCR_PARSE", "전체 줄 수: ${lines.size}")

        // 개선된 메뉴 추출 로직
        findMenuItemsImproved(lines, menuItems)

        // 총합 찾기 (기존 로직)
        val totalPatterns = listOf(
            Regex("합\\s*계[:\\s]*([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{3,})"),
            Regex("총\\s*계[:\\s]*([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{3,})"),
            Regex("총\\s*액[:\\s]*([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{3,})"),
            Regex("결제금액[:\\s]*([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{3,})")
        )

        var receiptTotal = 0
        for (pattern in totalPatterns) {
            val totalMatch = pattern.find(ocrText)
            if (totalMatch != null) {
                try {
                    receiptTotal = totalMatch.groupValues[1].replace(",", "").toInt()
                    Log.d("OCR_PARSE", "총합 발견: ${receiptTotal}원")
                    break
                } catch (e: NumberFormatException) {
                    continue
                }
            }
        }

        val itemsTotal = menuItems.sumOf { it.price }
        val finalAmount = if (receiptTotal > 0) receiptTotal else itemsTotal

        // 카테고리 자동 감지
        val detectedCategory = classifyByOcrText(ocrText, lines)

        Log.d("OCR_PARSE", "=== 최종 파싱 결과 ===")
        Log.d("OCR_PARSE", "추출된 메뉴: ${menuItems.size}개")
        menuItems.forEach { Log.d("OCR_PARSE", "- ${it.name}: ${it.price}원") }
        Log.d("OCR_PARSE", "카테고리: $detectedCategory")
        Log.d("OCR_PARSE", "최종 금액: ${finalAmount}원")

        return ParsedOcrData(menuItems, itemsTotal, receiptTotal, finalAmount, detectedCategory)
    }

    private fun findMenuItemsImproved(lines: List<String>, menuItems: MutableList<MenuItem>) {
        // 제외할 키워드들 (확장)
        val excludeKeywords = listOf(
            "주문번호", "Tel", "전화", "사업자", "영수증", "신용카드", "현금", "부가세",
            "과세", "합계", "총액", "소계", "결제", "승인", "문의", "쿠폰", "할인",
            "매출", "식별", "번호", "일시", "지점", "점포", "주소", "광역시", "구",
            "동", "층", "POS", "http", "www", "kr", "go", "hometax", "Mad",
            "챙겨주세요", "가입하세요", "쌓이는", "하트로", "무료", "앱", "소득공제"
        )

        for (i in lines.indices) {
            val line = lines[i].trim()
            Log.d("OCR_PARSE", "라인 $i 검사: '$line'")

            // 빈 줄이나 너무 짧은 줄 제외
            if (line.isEmpty() || line.length < 2) {
                continue
            }

            // 제외 키워드 체크
            var shouldSkip = false
            for (keyword in excludeKeywords) {
                if (line.contains(keyword, ignoreCase = true)) {
                    shouldSkip = true
                    Log.d("OCR_PARSE", "제외 키워드 '$keyword' 발견, 스킵")
                    break
                }
            }
            if (shouldSkip) continue

            // 숫자나 특수문자만 있는 라인 제외
            if (line.matches(Regex("^[0-9\\-\\(\\)\\*\\[\\]/:]+$"))) {
                Log.d("OCR_PARSE", "숫자/특수문자만 있는 라인 스킵")
                continue
            }

            // 패턴 1: 메뉴명에 가격이 포함된 경우 (예: "ice아메리카노디카L 5,800")
            val menuWithPricePattern = Regex("^([가-힣a-zA-Z\\s]{3,}[가-힣a-zA-Z])\\s+([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{3,})$")
            val menuWithPriceMatch = menuWithPricePattern.find(line)
            if (menuWithPriceMatch != null) {
                val menuName = menuWithPriceMatch.groupValues[1].trim()
                val priceStr = menuWithPriceMatch.groupValues[2].replace(",", "")

                try {
                    val price = priceStr.toInt()
                    if (isValidMenuItem(menuName, price)) {
                        menuItems.add(MenuItem(menuName, price))
                        Log.d("OCR_PARSE", "✅ 한줄 메뉴+가격 추출: $menuName - ${price}원")
                        continue
                    }
                } catch (e: NumberFormatException) {
                    Log.d("OCR_PARSE", "가격 변환 실패: $priceStr")
                }
            }

            // 패턴 2: 메뉴명만 있는 경우, 다음 줄들에서 가격 찾기
            if (isLikelyMenuName(line)) {
                Log.d("OCR_PARSE", "메뉴명 후보 발견: '$line'")

                val menuName = cleanMenuName(line)
                var price = 0

                // 다음 3줄까지 확인해서 가격 찾기
                for (j in 1..3) {
                    if (i + j >= lines.size) break

                    val nextLine = lines[i + j].trim()
                    Log.d("OCR_PARSE", "  다음 라인 검사: '$nextLine'")

                    // "1개", "2개" 같은 수량 라인은 스킵
                    if (nextLine.matches(Regex("^\\d+개$"))) {
                        Log.d("OCR_PARSE", "  수량 라인 스킵")
                        continue
                    }

                    // 순수 숫자 가격 패턴
                    val pricePattern = Regex("^([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{3,})$")
                    val priceMatch = pricePattern.find(nextLine)
                    if (priceMatch != null) {
                        try {
                            price = priceMatch.groupValues[1].replace(",", "").toInt()
                            Log.d("OCR_PARSE", "  가격 발견: ${price}원")
                            break
                        } catch (e: NumberFormatException) {
                            Log.d("OCR_PARSE", "  가격 변환 실패")
                        }
                    }
                }

                // 유효한 메뉴인지 확인 후 추가
                if (price > 0 && isValidMenuItem(menuName, price)) {
                    menuItems.add(MenuItem(menuName, price))
                    Log.d("OCR_PARSE", "✅ 분리형 메뉴 추출 성공: $menuName - ${price}원")
                } else {
                    Log.d("OCR_PARSE", "❌ 유효하지 않은 메뉴: $menuName - ${price}원")
                }
            }
        }

        // 중복 제거
        val uniqueItems = menuItems.distinctBy { it.name }
        menuItems.clear()
        menuItems.addAll(uniqueItems)

        Log.d("OCR_PARSE", "=== 최종 메뉴 목록 ===")
        menuItems.forEach { (name, price) ->
            Log.d("OCR_PARSE", "✅ $name - ${String.format("%,d", price)}원")
        }
    }

    private fun isLikelyMenuName(line: String): Boolean {
        // 메뉴명일 가능성이 높은지 판단
        return line.length >= 3 &&                                    // 최소 3글자
                line.length <= 30 &&                                   // 최대 30글자
                line.any { it.isLetter() } &&                          // 문자 포함
                !line.matches(Regex("^[0-9\\-\\(\\)\\*\\[\\]/:]+$")) && // 숫자/특수문자만이 아님
                !line.matches(Regex("^\\d+개$")) &&                     // "1개" 형태가 아님
                !line.contains("번호") &&                               // 각종 번호가 아님
                !line.contains("일시") &&                               // 날짜/시간이 아님
                !line.contains("Tel") &&                               // 전화번호가 아님
                !line.contains("/")                                     // 날짜 형식이 아님
    }

    private fun cleanMenuName(menuName: String): String {
        // 메뉴명 정리 (불필요한 문자 제거)
        return menuName
            .replace(Regex("[\\d]+개$"), "")        // 끝의 "1개" 제거
            .replace(Regex("^-+"), "")              // 앞의 대시 제거
            .replace(Regex("-+$"), "")              // 뒤의 대시 제거
            .trim()
    }

    private fun isValidMenuItem(itemName: String, price: Int): Boolean {
        // 메뉴명 및 가격 유효성 검사 (기준 완화)
        return itemName.length >= 2 &&                          // 최소 2글자
                itemName.length <= 50 &&                         // 최대 50글자
                price >= 100 &&                                  // 최소 100원 (더 관대하게)
                price <= 100000 &&                               // 최대 100,000원
                !itemName.matches(Regex(".*[0-9]{4,}.*")) &&     // 긴 숫자 포함 안함
                itemName.any { it.isLetter() } &&                // 최소 하나의 문자 포함
                !itemName.contains("합계") &&                     // 합계 관련 단어 제외
                !itemName.contains("총액") &&
                !itemName.contains("부가세") &&
                !itemName.contains("과세")
    }

    // 카테고리 분류 로직
    private fun classifyByOcrText(ocrText: String, lines: List<String>): String {
        val text = ocrText.lowercase()

        // 매장명 기반 분류
        val storePatterns = mapOf(
            "카페" to listOf(
                "스타벅스", "starbucks", "투썸플레이스", "twosome", "이디야", "ediya",
                "메가커피", "mega", "빽다방", "paik", "커피빈", "coffeebean", "할리스", "hollys",
                "엔젤인어스", "angel", "카페베네", "caffe bene", "드롭탑", "탐앤탐스", "tom n toms"
            ),
            "음식점" to listOf(
                "맥도날드", "mcdonald", "버거킹", "burger king", "kfc", "롯데리아", "lotteria",
                "서브웨이", "subway", "피자헛", "pizza hut", "도미노피자", "domino", "피자알볼로",
                "치킨플러스", "bhc", "교촌치킨", "kyochon", "굽네치킨", "goobne"
            ),
            "편의점" to listOf(
                "gs25", "cu", "세븐일레븐", "7-eleven", "이마트24", "emart24",
                "미니스톱", "ministop", "편의점"
            ),
            "쇼핑" to listOf(
                "이마트", "emart", "롯데마트", "lotte mart", "홈플러스", "homeplus",
                "코스트코", "costco", "하나로마트", "농협", "백화점", "아울렛", "outlet",
                "올리브영", "olive young", "왓슨스", "watsons"
            ),
            "교통" to listOf(
                "지하철", "버스", "택시", "주차", "톨게이트", "기름", "주유소", "gs칼텍스",
                "sk에너지", "s-oil", "현대오일뱅크", "알뜰주유소"
            )
        )

        for (line in lines) {
            val lineText = line.lowercase()
            for ((category, patterns) in storePatterns) {
                for (pattern in patterns) {
                    if (lineText.contains(pattern)) {
                        Log.d("OCR_CATEGORY", "매장명 패턴 발견: '$pattern' → $category")
                        return category
                    }
                }
            }
        }

        // 메뉴 키워드 기반 분류
        val menuKeywords = mapOf(
            "카페" to listOf(
                "아메리카노", "americano", "라떼", "latte", "카푸치노", "cappuccino",
                "에스프레소", "espresso", "마끼아또", "macchiato", "모카", "mocha",
                "프라푸치노", "frappuccino", "케이크", "머핀", "쿠키", "베이글"
            ),
            "음식점" to listOf(
                "햄버거", "burger", "피자", "pizza", "치킨", "chicken", "프라이드",
                "파스타", "pasta", "스테이크", "steak", "김밥", "라면", "우동",
                "냉면", "비빔밥", "불고기", "갈비", "삼겹살"
            )
        )

        for ((category, keywords) in menuKeywords) {
            for (keyword in keywords) {
                if (text.contains(keyword)) {
                    Log.d("OCR_CATEGORY", "메뉴 키워드 발견: '$keyword' → $category")
                    return category
                }
            }
        }

        // 영수증 형태 기반 분류
        return when {
            text.contains("주차") || text.contains("parking") -> "교통"
            text.contains("주유") || text.contains("기름") || text.contains("리터") -> "교통"
            text.contains("병원") || text.contains("의원") || text.contains("pharmacy") -> "의료"
            text.contains("영화") || text.contains("cgv") || text.contains("롯데시네마") -> "문화생활"
            text.contains("노래방") || text.contains("pc방") || text.contains("게임") -> "문화생활"
            else -> "기타"
        }
    }

    // 자동 저장 함수
    private fun saveToFirestore(parsedData: ParsedOcrData) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // 메뉴 요약 생성
        val itemsSummary = if (parsedData.menuItems.isNotEmpty()) {
            parsedData.menuItems.take(5).joinToString(", ") { "${it.name}(${it.price}원)" }
        } else {
            "OCR 인식 항목"
        }

        val data = mapOf(
            "amount" to parsedData.finalAmount,
            "category" to parsedData.detectedCategory,
            "asset" to "OCR 인식",
            "memo" to itemsSummary,
            "date" to Date(),
            "ocrDetails" to mapOf(
                "items" to parsedData.menuItems.map { mapOf("name" to it.name, "price" to it.price) },
                "itemsTotal" to parsedData.itemsTotal,
                "receiptTotal" to parsedData.receiptTotal,
                "detectedCategory" to parsedData.detectedCategory,
                "userModified" to false
            )
        )

        Firebase.firestore
            .collection("users")
            .document(uid)
            .collection("spending")
            .add(data)
            .addOnSuccessListener {
                Log.d("OCR", "✅ OCR 결과 자동 저장 완료: ${it.id}")
            }
            .addOnFailureListener {
                Log.e("OCR", "❌ OCR 자동 저장 실패: ${it.message}")
            }
    }

    data class MenuItem(val name: String, val price: Int)

    data class ParsedOcrData(
        val menuItems: List<MenuItem>,
        val itemsTotal: Int,
        val receiptTotal: Int,
        val finalAmount: Int,
        val detectedCategory: String
    )
}