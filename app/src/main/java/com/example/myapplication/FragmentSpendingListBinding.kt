// FragmentSpendingListBinding.kt (View Binding 클래스)
package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

class FragmentSpendingListBinding private constructor(
    private val rootView: View
) : ViewBinding {

    // 태그로 RecyclerView 찾기
    val recyclerViewSpending: RecyclerView = rootView.findViewWithTag("recycler_view")

    override fun getRoot(): View = rootView

    companion object {
        fun inflate(inflater: LayoutInflater, parent: ViewGroup?, attachToParent: Boolean): FragmentSpendingListBinding {
            val context = inflater.context

            val rootLayout = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setBackgroundColor(android.graphics.Color.parseColor("#F8F9FA"))
            }

            // 헤더 (이전과 동일)
            val headerLayout = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(48, 48, 48, 48)
                setBackgroundColor(android.graphics.Color.WHITE)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val titleText = android.widget.TextView(context).apply {
                text = "💰 지출 내역"
                textSize = 20f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#212121"))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            }

            val hintText = android.widget.TextView(context).apply {
                text = "✏️ 항목을 터치하여 수정"
                textSize = 12f
                setTextColor(android.graphics.Color.parseColor("#757575"))
            }

            headerLayout.addView(titleText)
            headerLayout.addView(hintText)

            // 구분선
            val divider = View(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 3
                )
                setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0"))
            }

            // RecyclerView
            val recyclerView = RecyclerView(context).apply {
                id = View.generateViewId() // 동적 ID 생성
                tag = "recycler_view" // 태그로 식별
                clipToPadding = false
                setPadding(0, 24, 0, 240)
            }

            rootLayout.addView(headerLayout)
            rootLayout.addView(divider)
            rootLayout.addView(recyclerView)

            if (parent != null && attachToParent) {
                parent.addView(rootLayout)
            }

            return FragmentSpendingListBinding(rootLayout)
        }

        fun inflate(inflater: LayoutInflater): FragmentSpendingListBinding {
            return inflate(inflater, null, false)
        }
    }
}