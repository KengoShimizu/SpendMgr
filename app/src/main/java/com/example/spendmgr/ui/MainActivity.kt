package com.example.spendmgr.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.spendmgr.ui.screen.ExpenseEntryScreen
import com.example.spendmgr.ui.theme.SpendMgrTheme
import com.example.spendmgr.viewmodel.ExpenseViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * アプリのエントリーポイントとなる Activity。
 * - @AndroidEntryPoint で Hilt の DI を有効化 (Req 1.1)
 * - ExpenseEntryScreen を表示する (Req 1.1)
 *
 * Requirements: 1.1
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: ExpenseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SpendMgrTheme {
                ExpenseEntryScreen(viewModel = viewModel)
            }
        }
    }
}
