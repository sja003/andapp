package com.example.myapplication

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Date

object FirestoreHelper {
    fun saveParsedOcrResult(ocrText: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        Log.d("OCR_PARSE", "원본 텍스트:\n$ocrText")

        // 텍스트를 줄 단위로 분리하고 정리
        val lines = ocrText.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // 1단계: 스마트 카테고리 분류
        val smartCategory = classifyByOcrText(ocrText, lines)

        // 2단계: 메뉴 품목 추출
        val menuItems = mutableListOf<Pair<String, Int>>()
        findMenuItems(lines, menuItems)

        // 3단계: 총합 금액 찾기
        val receiptTotal = findReceiptTotal(ocrText)

        // 4단계: 품목 합계 계산
        val itemsTotal = menuItems.sumOf { it.second }

        // 5단계: 최종 금액 결정
        val finalAmount = if (receiptTotal > 0) receiptTotal else itemsTotal

        // 6단계: 품목 요약 생성
        val itemsSummary = if (menuItems.isNotEmpty()) {
            menuItems.take(5).joinToString(", ") { "${it.first}(${it.second}원)" }
        } else {
            "OCR 인식 항목"
        }

        Log.d("OCR_PARSE", "=== 최종 결과 ===")
        Log.d("OCR_PARSE", "스마트 카테고리: $smartCategory")
        Log.d("OCR_PARSE", "인식된 메뉴: ${menuItems.map { "${it.first}-${it.second}원" }}")
        Log.d("OCR_PARSE", "품목 합계: ${itemsTotal}원")
        Log.d("OCR_PARSE", "영수증 총합: ${receiptTotal}원")
        Log.d("OCR_PARSE", "최종 저장 금액: ${finalAmount}원")

        // Firestore에 저장 (스마트 카테고리 적용)
        saveToFirestore(uid, finalAmount, itemsSummary, menuItems, itemsTotal, receiptTotal, ocrText, smartCategory)
    }

    private fun classifyByOcrText(ocrText: String, lines: List<String>): String {
        Log.d("OCR_CATEGORY", "=== 카테고리 분류 시작 ===")

        val text = ocrText.lowercase()

        // 매장명 기반 분류 (우선순위 높음)
        val storeClassification = classifyByStoreName(text, lines)
        if (storeClassification != "기타") {
            Log.d("OCR_CATEGORY", "매장명 기반 분류: $storeClassification")
            return storeClassification
        }

        // 메뉴/키워드 기반 분류
        val menuClassification = classifyByMenuKeywords(text)
        if (menuClassification != "기타") {
            Log.d("OCR_CATEGORY", "메뉴 기반 분류: $menuClassification")
            return menuClassification
        }

        // 영수증 형태 기반 분류
        val receiptTypeClassification = classifyByReceiptType(text)
        Log.d("OCR_CATEGORY", "영수증 형태 기반 분류: $receiptTypeClassification")

        return receiptTypeClassification
    }

    private fun classifyByStoreName(text: String, lines: List<String>): String {
        // 대표적인 매장명 패턴들
        val storePatterns = mapOf(
            // 카페/커피
            "카페" to listOf(
                "스타벅스", "starbucks", "투썸플레이스", "twosome", "이디야", "ediya",
                "메가커피", "mega", "빽다방", "paik", "커피빈", "coffeebean", "할리스", "hollys",
                "엔젤인어스", "angel", "카페베네", "caffe bene", "드롭탑", "탐앤탐스", "tom n toms",
                "블루보틀", "blue bottle", "폴바셋", "paul bassett", "원커피", "더벤티", "venti"
            ),

            // 음식점/레스토랑
            "음식점" to listOf(
                "맥도날드", "mcdonald", "버거킹", "burger king", "kfc", "롯데리아", "lotteria",
                "서브웨이", "subway", "피자헛", "pizza hut", "도미노피자", "domino", "피자알볼로",
                "치킨플러스", "bhc", "교촌치킨", "kyochon", "굽네치킨", "goobne",
                "김밥천국", "김밥나라", "죽여죠", "본죽", "백종원", "빕스", "vips",
                "아웃백", "outback", "탐앤탐스", "더본코리아", "청담동", "강남", "홍대"
            ),

            // 편의점
            "편의점" to listOf(
                "gs25", "cu", "세븐일레븐", "7-eleven", "이마트24", "emart24",
                "미니스톱", "ministop", "편의점"
            ),

            // 마트/쇼핑
            "쇼핑" to listOf(
                "이마트", "emart", "롯데마트", "lotte mart", "홈플러스", "homeplus",
                "코스트코", "costco", "하나로마트", "농협", "백화점", "아울렛", "outlet",
                "올리브영", "olive young", "왓슨스", "watsons", "부츠", "boots"
            ),

            // 교통
            "교통" to listOf(
                "지하철", "버스", "택시", "주차", "톨게이트", "기름", "주유소", "gs칼텍스",
                "sk에너지", "s-oil", "현대오일뱅크", "알뜰주유소"
            )
        )

        // 줄별로 매장명 패턴 검색
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

        return "기타"
    }

    private fun classifyByMenuKeywords(text: String): String {
        val menuKeywords = mapOf(
            "카페" to listOf(
                "아메리카노", "americano", "라떼", "latte", "카푸치노", "cappuccino",
                "에스프레소", "espresso", "마끼아또", "macchiato", "모카", "mocha",
                "프라푸치노", "frappuccino", "아이스크림", "케이크", "머핀", "쿠키",
                "베이글", "bagel", "샌드위치", "sandwich", "스무디", "smoothie"
            ),

            "음식점" to listOf(
                "햄버거", "burger", "피자", "pizza", "치킨", "chicken", "프라이드",
                "파스타", "pasta", "스테이크", "steak", "김밥", "라면", "우동",
                "냉면", "비빔밥", "불고기", "갈비", "삼겹살", "찜닭", "떡볶이"
            ),

            "편의점" to listOf(
                "삼각김밥", "도시락", "컵라면", "음료수", "과자", "사탕", "초콜릿",
                "아이스크림", "우유", "요구르트", "빵", "담배", "라이터"
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

        return "기타"
    }

    private fun classifyByReceiptType(text: String): String {
        // 영수증 형태나 결제 방식으로 추정
        return when {
            text.contains("주차") || text.contains("parking") -> "교통"
            text.contains("주유") || text.contains("기름") || text.contains("리터") -> "교통"
            text.contains("병원") || text.contains("의원") || text.contains("pharmacy") -> "의료"
            text.contains("영화") || text.contains("cgv") || text.contains("롯데시네마") -> "문화생활"
            text.contains("노래방") || text.contains("pc방") || text.contains("게임") -> "문화생활"
            else -> "기타"
        }
    }

    private fun findMenuItems(lines: List<String>, menuItems: MutableList<Pair<String, Int>>) {
        Log.d("OCR_PARSE", "=== 메뉴 추출 시작 ===")

        // 제외할 키워드들
        val excludeKeywords = listOf(
            "주문번호", "Tel", "전화", "사업자", "영수증", "신용카드", "현금", "부가세",
            "과세", "합계", "총액", "소계", "결제", "승인", "문의", "쿠폰", "할인",
            "매출", "식별", "번호", "일시", "지점", "점포", "주소", "광역시", "구",
            "동", "층", "POS", "http", "www", "kr", "go", "hometax", "Mad"
        )

        for (i in lines.indices) {
            val line = lines[i].trim()
            Log.d("OCR_PARSE", "라인 $i: '$line'")

            // 빈 줄이나 너무 짧은 줄 제외
            if (line.isEmpty() || line.length < 2) {
                continue
            }

            // 제외 키워드 체크
            var shouldSkip = false
            for (keyword in excludeKeywords) {
                if (line.contains(keyword, ignoreCase = true)) {
                    shouldSkip = true
                    Log.d("OCR_PARSE", "제외 키워드 '$keyword' 발견, 스킵: $line")
                    break
                }
            }
            if (shouldSkip) {
                continue
            }

            // 숫자나 특수문자만 있는 라인 제외
            if (line.matches(Regex("^[0-9\\-\\(\\)\\*\\[\\]/:]+$"))) {
                Log.d("OCR_PARSE", "숫자/특수문자만 있는 라인 스킵: $line")
                continue
            }

            // 단순히 "개"만 있는 라인 제외
            if (line.matches(Regex("^\\d+개$"))) {
                Log.d("OCR_PARSE", "개수만 있는 라인 스킵: $line")
                continue
            }

            // 패턴 1: 한글/영문으로 된 메뉴명 같은 라인 찾기
            if (line.matches(Regex("^[가-힣a-zA-Z\\s]+$")) && line.length >= 3) {
                Log.d("OCR_PARSE", "메뉴명 후보 발견: $line")

                // 다음 몇 줄을 확인해서 개수나 가격 찾기
                val menuName = line
                var price = 0

                // 다음 3줄까지 확인
                for (j in 1..3) {
                    if (i + j >= lines.size) break

                    val nextLine = lines[i + j].trim()
                    Log.d("OCR_PARSE", "  다음 라인 $j: '$nextLine'")

                    // "1개" 같은 라인은 스킵
                    if (nextLine.matches(Regex("^\\d+개$"))) {
                        Log.d("OCR_PARSE", "  개수 라인 스킵: $nextLine")
                        continue
                    }

                    // 가격 패턴 찾기
                    val priceMatch = Regex("^([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{3,})$").find(nextLine)
                    if (priceMatch != null) {
                        try {
                            price = priceMatch.groupValues[1].replace(",", "").toInt()
                            Log.d("OCR_PARSE", "  가격 발견: ${price}원")
                            break
                        } catch (e: NumberFormatException) {
                            Log.d("OCR_PARSE", "  가격 변환 실패: $nextLine")
                        }
                    }
                }

                // 유효한 메뉴인지 확인
                if (price > 0 && isValidMenuItem(menuName, price)) {
                    menuItems.add(Pair(menuName, price))
                    Log.d("OCR_PARSE", "✅ 메뉴 추출 성공: $menuName - ${price}원")
                } else {
                    Log.d("OCR_PARSE", "❌ 유효하지 않은 메뉴: $menuName - ${price}원")
                }
            }

            // 패턴 2: "메뉴명 개수 가격" 한 줄에 모두 있는 경우
            val oneLinePattern = Regex("^([가-힣a-zA-Z\\s]+)\\s+(\\d+개?)\\s+([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{3,})$")
            val oneLineMatch = oneLinePattern.find(line)
            if (oneLineMatch != null) {
                val menuName = oneLineMatch.groupValues[1].trim()
                val priceStr = oneLineMatch.groupValues[3].replace(",", "")

                try {
                    val price = priceStr.toInt()
                    if (isValidMenuItem(menuName, price)) {
                        menuItems.add(Pair(menuName, price))
                        Log.d("OCR_PARSE", "✅ 한줄 메뉴 추출: $menuName - ${price}원")
                    }
                } catch (e: NumberFormatException) {
                    Log.d("OCR_PARSE", "❌ 한줄 메뉴 가격 변환 실패: $priceStr")
                }
            }

            // 패턴 3: "메뉴명 가격" 한 줄에 있는 경우
            val menuPricePattern = Regex("^([가-힣a-zA-Z\\s]{3,})\\s+([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{3,})$")
            val menuPriceMatch = menuPricePattern.find(line)
            if (menuPriceMatch != null) {
                val menuName = menuPriceMatch.groupValues[1].trim()
                val priceStr = menuPriceMatch.groupValues[2].replace(",", "")

                try {
                    val price = priceStr.toInt()
                    if (isValidMenuItem(menuName, price)) {
                        menuItems.add(Pair(menuName, price))
                        Log.d("OCR_PARSE", "✅ 메뉴-가격 추출: $menuName - ${price}원")
                    }
                } catch (e: NumberFormatException) {
                    Log.d("OCR_PARSE", "❌ 메뉴-가격 변환 실패: $priceStr")
                }
            }
        }

        // 중복 제거
        val uniqueItems = menuItems.distinctBy { it.first }
        menuItems.clear()
        menuItems.addAll(uniqueItems)

        Log.d("OCR_PARSE", "=== 메뉴 추출 완료: ${menuItems.size}개 ===")
        menuItems.forEach { (name, price) ->
            Log.d("OCR_PARSE", "최종 메뉴: $name - ${price}원")
        }
    }

    private fun isValidMenuItem(itemName: String, price: Int): Boolean {
        // 메뉴명 유효성 검사
        return itemName.length >= 2 &&                    // 최소 2글자
                itemName.length <= 30 &&                   // 최대 30글자
                price >= 1000 &&                           // 최소 1000원
                price <= 50000 &&                          // 최대 50000원
                !itemName.matches(Regex(".*[0-9]{3,}.*")) && // 긴 숫자 포함 안함
                itemName.any { it.isLetter() }             // 최소 하나의 문자 포함
    }

    private fun findReceiptTotal(ocrText: String): Int {
        // 총합을 찾기 위한 다양한 패턴들
        val totalPatterns = listOf(
            Regex("합\\s*계\\s*([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{3,})"),
            Regex("총\\s*계\\s*([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{3,})"),
            Regex("총\\s*액\\s*([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{3,})"),
            Regex("결제금액\\s*([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{3,})"),
            // 특별한 경우: 줄바꿈으로 분리된 경우
            Regex("합\\s*\\n?\\s*계\\s*\\n?\\s*([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{3,})"),
            Regex("총\\s*\\n?\\s*액\\s*\\n?\\s*([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{3,})")
        )

        for (pattern in totalPatterns) {
            val match = pattern.find(ocrText)
            if (match != null) {
                try {
                    val total = match.groupValues[1].replace(",", "").toInt()
                    Log.d("OCR_PARSE", "영수증 총합 발견: ${total}원 (패턴: ${pattern.pattern})")
                    return total
                } catch (e: NumberFormatException) {
                    continue
                }
            }
        }

        return 0
    }

    private fun saveToFirestore(
        uid: String,
        finalAmount: Int,
        itemsSummary: String,
        menuItems: List<Pair<String, Int>>,
        itemsTotal: Int,
        receiptTotal: Int,
        ocrText: String,
        smartCategory: String  // 스마트 카테고리 추가
    ) {
        val data = mapOf(
            "amount" to finalAmount,
            "category" to smartCategory,  // OCR 대신 스마트 카테고리 사용
            "asset" to "OCR 인식",
            "memo" to itemsSummary,
            "date" to Date(),
            "ocrDetails" to mapOf(
                "items" to menuItems.map { mapOf("name" to it.first, "price" to it.second) },
                "itemsTotal" to itemsTotal,
                "receiptTotal" to receiptTotal,
                "detectedCategory" to smartCategory,  // 감지된 카테고리 저장
                "rawText" to ocrText.take(1000) // 원본 텍스트 일부 저장
            )
        )

        Firebase.firestore
            .collection("users")
            .document(uid)
            .collection("spending")
            .add(data)
            .addOnSuccessListener {
                Log.d("OCR", "✅ OCR 상세 정보 저장 완료: ${it.id}")
                Log.d("OCR", "저장된 내용: 카테고리=$smartCategory, 금액=${finalAmount}원, 품목=${menuItems.size}개")
            }
            .addOnFailureListener {
                Log.e("OCR", "❌ OCR 저장 실패: ${it.message}")
            }
    }
}