# Design Document: SpendMgr

## Overview

SpendMgrは、個人の日常経費を素早く記録し、GoogleスプレッドシートへAuto同期するAndroidアプリである。Kotlin + Jetpack Composeで構築し、MVVM (Model-View-ViewModel) アーキテクチャを採用する。

アプリはシングルスクリーン構成で、トップ画面 (Expense_Entry_Screen) にすべての機能を集める。トップ画面には以下の要素が配置される：
- 今年・今月の合計経費エリア（お小遣い設定時はタップで設定トグル切り替え）
- 金額入力テキストフィールド（OS標準の数値キーボード、起動時に自動フォーカス）
- 日付入力（本日がデフォルト設定済み、カレンダーUIで変更可能）
- カテゴリ入力（履歴から補完、日本語テキストキーボード）
- 記録ボタン
- 右上にスプレッドシートを開くアイコン（Settings_Iconの左）とギアアイコン（お小遣い設定ダイアログを開く）

主な操作フロー：
1. アプリ起動 → トップ画面を前面に表示（金額欄と同時に表示・金額入力に自動フォーカス）
2. トップ画面上の操作（金額・日付・カテゴリ） → エンターキーで次に移動 → 日付はデフォルト設定に応じて変更
3. 記録ボタンタップ → バリデーション → Google Driveの上のフォルダ・スプレッドシートを取得 → 当月シートに追記
4. 成功時はUndo Snackbarを表示（記録内容付き）、失敗時はローカルキャッシュ展開版（その日に先に記し後で上書き）、失敗時はローカルに一時保存

Google連携（Auth認証）実行後、アプリはGoogle Driveのトップに「SpendMgr」フォルダを自動作成し、年別スプレッドシート（例: SpendMgr/2026）を管理する。各スプレッドシートは月別シート（1月〜12月）と「まとめ」シートで構成される。各月別シートは1行目にヘッダー行（A列: 日付、B列: 金額、C列: カテゴリ）を持ち、データは2行目以降に追記される。まとめシートのSUM関数は各月シートのB列（金額列）を参照する。

合計額の取得はアプリ起動時にスプレッドシートからフェッチしてDataStoreにキャッシュし、記録成功・取り消し成功時はローカルで加減算する。アプリkill後の再起動時はDataStoreから前回の値を即座に表示し、バックグラウンドでスプレッドシートから最新値を取得する。プルトゥリフレッシュでスプレッドシートから再取得も可能。これにより合計取得のAPI呼び出しは起動時とプルトゥリフレッシュ時のみに限定される。

## Architecture

### 全体構成

```mermaid
graph TB
    subgraph UI Layer
        A[ExpenseEntryScreen] --> B[AmountInputTextField]
        A --> C[DatePicker]
        A --> D[CategoryInput]
        A --> E[RecordButton]
        A --> F[OpenSpreadsheetButton]
        A --> P2[ExpenseSummaryArea]
        A --> P3[SettingsIcon]
        A --> P4[UndoSnackbar]
        A --> P5[AllowanceDialog]
    end

    subgraph ViewModel Layer
        G[ExpenseViewModel]
    end

    subgraph Domain Layer
        I[ExpenseValidator]
        J[CategorySuggestionEngine]
        K2[SpreadsheetResolver]
        K4[SummaryFetcher]
        K5[SummaryCache]
    end

    subgraph Data Layer
        K[GoogleSheetsRepository]
        K3[GoogleDriveRepository]
        L[CategoryHistoryRepository]
        M[SettingsRepository]
        N[PendingExpenseRepository]
    end

    subgraph External
        O[Google Sheets API]
        O2[Google Drive API]
        P[Room Database]
        Q[DataStore]
    end

    A --> G
    G --> I
    G --> J
    G --> K2
    K2 --> K3
    G --> K4
    K4 --> K
    G --> K5
    G --> L
    G --> N
    K --> O
    K3 --> O2
    L --> P
    M --> Q
    N --> P
end
```

### 技術スタック

| レイヤー | 技術 |
|---------|------|
| UI | Jetpack Compose + Material 3 |
| ViewModel | AndroidX ViewModel + StateFlow |
| DI | Hilt |
| ローカルDB | Room |
| 設定保存 | DataStore (Preferences) |
| API連携 | Google Sheets API v4, Google Drive API v3 |
| 認証 | Google Sign-In (Credential Manager) |
| ビルド | Gradle (Kotlin DSL)、OAuthクライアントIDは `local.properties` → `BuildConfig` 経由で注入 |

## Components and Interfaces

### 1. UI Components

#### ExpenseEntryScreen
アプリ起動時に表示されるメイン画面。全入力欄とスプレッドシート遷移ボタンを配置する。プルトゥリフレッシュ（画面を下に引っ張る操作）で合計額をスプレッドシートから再取得できる。

```kotlin
@Composable
fun ExpenseEntryScreen(viewModel: ExpenseViewModel)
```

#### OpenSpreadsheetButton
直近の年のYearly_SpreadsheetをSheetsアプリ優先で開くアイコンボタン。TopAppBarの右上（Settings_Iconの左）に配置される。`Icons.Default.OpenInNew` を使用する。

```kotlin
// TopAppBar の actions 内に配置
IconButton(onClick = { viewModel.onOpenSpreadsheetClick() }) {
    Icon(imageVector = Icons.Default.OpenInNew, contentDescription = "スプレッドシートを開く")
}
```

#### AmountInputTextField
金額入力用のテキストフィールド。OS標準の数値キーボード (keyboardType = KeyboardType.Number) を使用する。アプリ起動時に自動フォーカスし、数値キーボードが画面下から立ち上がる。IMEアクションはNext (imeAction = ImeAction.Next) で、エンターキータップ時にCategory_Inputにフォーカスを移動する。

```kotlin
@Composable
fun AmountInputTextField(
    amount: String,
    onAmountChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onNext: () -> Unit    // IMEアクションNextでCategory_Inputにフォーカス移動
)
```

#### CategoryInput
カテゴリ入力フィールド。入力に応じて候補リストを表示する。キーボードタイプはText (keyboardType = KeyboardType.Text、日本語入力キーボード)。IMEアクションはDone (imeAction = ImeAction.Done)。

```kotlin
@Composable
fun CategoryInput(
    category: String,
    currentSuggestions: List<String>,
    onCategoryChange: (String) -> Unit,
    onSuggestionSelect: (String) -> Unit,
    focusRequester: FocusRequester,
    errorMessage: String? = null,
    onDone: () -> Unit = {}
)
```

#### SettingsIcon
Expense_Entry_Screenの右上に配置されるギアアイコン。タップするとAllowance_Dialogを表示する。

```kotlin
@Composable
fun SettingsIcon(
    onClick: () -> Unit
)
```

#### AllowanceDialog
お小遣い額を設定するためのダイアログ。最初から編集可能な状態で表示し、金額は `¥10,000` 形式でリアルタイム表示する。金額を入力して「決定」で保存、金額を空にして「決定」でnull（無制限）として保存する。

```kotlin
@Composable
fun AllowanceDialog(
    currentAmount: Int?,        // 現在のお小遣い額（未設定時はnull）
    onConfirm: (Int?) -> Unit,  // 決定ボタンコールバック（nullはお小遣い無制限）
    onDismiss: () -> Unit       // ダイアログ閉じるコールバック
)
```

#### UndoSnackbar
直前記録成功後に画面下部に表示されるMaterial 3 Snackbar。`ExpenseEntryScreen` の `Scaffold.snackbarHost` に配置した `SnackbarHostState` を通じて表示することで、Record_Button、Open_Spreadsheet_Button、Amount_Inputに重ならない位置に配置される。5秒後の自動消去はViewModelの `autoDismissJob` が担当し、`showUndoSnackbar` を false にすることで Snackbar を dismiss する。

```kotlin
// ExpenseEntryScreen 内での使用パターン
LaunchedEffect(uiState.showUndoSnackbar) {
    if (uiState.showUndoSnackbar) {
        val result = snackbarHostState.showSnackbar(
            message = uiState.undoExpenseLabel,
            actionLabel = "取り消し",
            duration = SnackbarDuration.Indefinite
        )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.onUndoClick()
            SnackbarResult.Dismissed -> viewModel.onSnackbarDismiss()
        }
    } else {
        snackbarHostState.currentSnackbarData?.dismiss()
    }
}
```

#### ExpenseSummaryArea
今年の合計額と今月の合計額を表示するエリア。データ取得中はローディング表示、取得失敗時やオフライン時は「—」プレースホルダーを表示する。お小遣い設定時はタップで通常表示・残額表示をトグル切り替えできる。残額表示モード時はラベルを「今年の残額」「今月の残額」に変更し、テキスト色をprimaryカラーに変えて視覚的に区別する。アプリkill後の再起動時もDataStoreから前回の値を即座に表示する。

```kotlin
@Composable
fun ExpenseSummaryArea(
    yearlyTotal: Int?,          // 今年の合計額（null時は「—」表示）
    monthlyTotal: Int?,         // 今月の合計額（null時は「—」表示）
    isLoading: Boolean,
    isToggleEnabled: Boolean,   // お小遣い設定済みの場合はtrue
    isRemainingMode: Boolean,   // 残額表示モードの場合はtrue
    allowanceAmount: Int?,      // お小遣い額（残額計算に使用）
    onToggleClick: () -> Unit   // トグル切り替えコールバック
)
```

### 2. ViewModel

#### ExpenseViewModel

```kotlin
@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val expenseValidator: ExpenseValidator,
    private val categorySuggestionEngine: CategorySuggestionEngine,
    private val spreadsheetResolver: SpreadsheetResolver,
    private val googleSheetsRepository: GoogleSheetsRepository,
    private val categoryHistoryRepository: CategoryHistoryRepository,
    private val pendingExpenseRepository: PendingExpenseRepository,
    private val settingsRepository: SettingsRepository,
    private val summaryFetcher: SummaryFetcher,
    private val summaryCache: SummaryCache,
    private val googleAuthManager: GoogleAuthManager
) : ViewModel() {

    val uiState: StateFlow<ExpenseUiState>

    fun onAmountChange(text: String)
    fun onDateChange(date: LocalDate)
    fun onCategoryChange(text: String)
    fun onSuggestionSelect(category: String)
    fun onRecordClick()
    fun onOpenSpreadsheetClick()
    fun onSignIn(context: Context)  // Activity Contextが必要（Credential Manager UIのため）
    fun onSnackbarDismiss()
    fun refreshSummary()
    fun onPullToRefresh()
    fun onSummaryAreaClick()
    fun onAllowanceChange(amount: Int?)
    fun onSettingsIconClick()
    fun onAllowanceDialogDismiss()
}
```

### 3. Domain Layer

#### ExpenseValidator
入力バリデーションロジック。金額・カテゴリ・認証状態の検証を行う。

```kotlin
class ExpenseValidator {
    fun validate(amount: String, category: String, isAuthenticated: Boolean): ValidationResult
}

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val error: ValidationError) : ValidationResult()
}

enum class ValidationError {
    AMOUNT_EMPTY,
    CATEGORY_EMPTY,
    NOT_AUTHENTICATED
}
```

#### CategorySuggestionEngine
過去の入力履歴から前方一致でカテゴリ候補を提示する。

```kotlin
class CategorySuggestionEngine(
    private val categoryHistoryRepository: CategoryHistoryRepository
) {
    suspend fun suggest(prefix: String): List<String>
    suspend fun recordCategory(category: String)
}
```

#### SpreadsheetResolver
経費記録の日付に基づいて、対応するスプレッドシートIDとシート名を解決する。フォルダ・スプレッドシートが存在しない場合は自動作成する。各Monthly_Sheetは1行目にヘッダー行（A列: 日付、B列: 金額、C列: カテゴリ）を持ち、データは2行目以降に追記される。まとめシートのSUM関数は各月シートのB列（金額列）を参照する。

```kotlin
class SpreadsheetResolver(
    private val googleDriveRepository: GoogleDriveRepository,
    private val googleSheetsRepository: GoogleSheetsRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend fun resolve(date: LocalDate): SpreadsheetTarget
    suspend fun ensureFolder(): String
    suspend fun getCurrentYearSpreadsheetUrl(): String?
}

data class SpreadsheetTarget(
    val spreadsheetId: String,
    val sheetName: String    // 例: "4月"
)
```

#### SummaryFetcher
Summary_Sheet上のMonthly_Sheetから合計額を取得する。アプリ起動時とプルトゥリフレッシュ時のみスプレッドシートからフェッチし、SummaryCacheに保存する。スプレッドシート未作成時やオフライン時はnullを返すが、**キャッシュは上書きしない**（DataStoreの値を保持する）。これによりkill後の再起動時も前回の値が即座に表示される。

```kotlin
class SummaryFetcher(
    private val googleSheetsRepository: GoogleSheetsRepository,
    private val spreadsheetResolver: SpreadsheetResolver,
    private val settingsRepository: SettingsRepository,
    private val summaryCache: SummaryCache
) {
    /**
     * 今年の合計額と今月の合計額をスプレッドシートから取得し、SummaryCacheに保存する。
     * アプリ起動時とプルトゥリフレッシュ時に呼び出される。
     * スプレッドシートが存在しない場合、認証未完了の場合。
     * またはネットワークエラーの場合はnullを返す。
     */
    suspend fun fetchSummary(): SummaryResult
}
```

#### SummaryCache
合計額のローカルキャッシュを管理する。DataStoreに永続化するためアプリkill後も値が保持される。起動時はDataStoreから即座に値を復元して表示し、バックグラウンドでスプレッドシートから最新値を取得する。記録成功後・取り消し成功時はローカルで加減算し、API呼び出しを最小化する。

```kotlin
class SummaryCache(
    private val settingsRepository: SettingsRepository
) {
    val yearlyTotal: StateFlow<Int?>
    val monthlyTotal: StateFlow<Int?>

    /**
     * スプレッドシートから取得した合計額でキャッシュを更新し、DataStoreに永続化する。
     */
    fun update(yearlyTotal: Int?, monthlyTotal: Int?)

    /**
     * 新規記録または取り消し時にキャッシュを加減算し、DataStoreに永続化する。
     * 正の値で加算（記録成功時）、負の値で減算（取り消し成功時）。
     * adjustYearly: 今年の合計を更新するか（今年以外の記録はfalse）
     * adjustMonthly: 今月の合計を更新するか（今月以外の記録はfalse）
     */
    fun adjust(delta: Int, adjustYearly: Boolean = true, adjustMonthly: Boolean = true)

    /**
     * キャッシュをクリアする（nullにリセット）。
     */
    fun clear()
}
```
### 4. Data Layer

#### GoogleAuthManager
Credential Manager APIを使用したGoogle Sign-In実装。認証成功後に `GoogleAuthUtil.getToken()` でアクセストークンを取得し `TokenProvider` に設定する。アクセストークンは1時間で期限切れになるが、`TokenProvider` が401エラー時に自動リフレッシュするため再ログイン不要。

OAuthクライアントIDの管理:
- Android用クライアントID: Google Cloud ConsoleでパッケージとSHA-1を登録（コードへの記載不要）
- ウェブ用クライアントID: `local.properties` の `google.web.client.id` に設定（Gitには上がらない）
- ビルド時に `BuildConfig.GOOGLE_WEB_CLIENT_ID` として注入される

```kotlin
class GoogleAuthManager(
    private val settingsRepository: SettingsRepository,
    private val tokenProvider: TokenProvider
) {
    suspend fun signIn(context: Context): AuthResult  // Activity Contextが必要
    suspend fun signOut()
    suspend fun isAuthenticated(): Boolean  // 再起動後にTokenProviderへトークンを再設定する
}
```

#### TokenProvider
Google APIリクエストにBearerトークンを付与する `HttpRequestInitializer`。401エラー時に `GoogleAuthUtil` でトークンをサイレントリフレッシュしてリトライする。

```kotlin
@Singleton
class TokenProvider(context: Context) : HttpRequestInitializer {
    fun setAccessToken(token: String)
    fun setAccountEmail(email: String)
    fun clearAccessToken()
    fun getAccessToken(): String?
    override fun initialize(request: HttpRequest)  // Bearerトークン付与 + 401時自動リフレッシュ
}
```

#### GoogleSheetsRepository

```kotlin
interface GoogleSheetsRepository {
    /**
     * 指定されたスプレッドシートの指定シートに経費を追記する。
     * 各Monthly_Sheetは1行目がヘッダー行（A列: 日付、B列: 金額、C列: カテゴリ）で、
     * データは2行目以降に追記される。
     * @return 追記された行番号を含むAppendResult
     */
    suspend fun appendExpense(
        spreadsheetId: String,
        sheetName: String,
        expense: ExpenseRecord
    ): Result<AppendResult>

    /**
     * 指定されたスプレッドシートの指定シートから指定行を削除する。
     * 取り消し機能で使用する。
     */
    suspend fun deleteRow(
        spreadsheetId: String,
        sheetId: Int,
        rowIndex: Int
    ): Result<Unit>

    /**
     * 新規スプレッドシートを作成し、まとめシート + 12ヶ月シートを初期化する。
     * 各月シートの1行目にはヘッダー行（A列: 日付、B列: 金額、C列: カテゴリ）を設定する。
     * @return 作成されたスプレッドシートのID
     */
    suspend fun createYearlySpreadsheet(
        name: String,
        folderId: String
    ): Result<String>

    /**
     * まとめシートにSUM関数を設定する。
     * 各月シートのB列（金額列）の合計を参照するSUM関数を設定する。
     */
    suspend fun setupSummarySheet(
        spreadsheetId: String
    ): Result<Unit>

    /**
     * まとめシートから今年の合計額を取得する。
     * @return 今年の合計額（円）。取得失敗時はnull
     */
    suspend fun fetchYearlyTotal(
        spreadsheetId: String
    ): Result<Int?>

    /**
     * 指定月シートから今月の合計額を取得する。
     * @return 今月の合計額（円）。取得失敗時はnull
     */
    suspend fun fetchMonthlyTotal(
        spreadsheetId: String,
        sheetName: String
    ): Result<Int?>
}
```

#### GoogleDriveRepository

```kotlin
interface GoogleDriveRepository {
    suspend fun findFolder(name: String): String?
    suspend fun createFolder(name: String): String
    suspend fun findSpreadsheet(folderId: String, name: String): String?
    /**
     * Sheets APIで作成されたスプレッドシートをSpendMgrフォルダに移動する。
     */
    suspend fun moveFile(fileId: String, newParentFolderId: String)
}
```

#### CategoryHistoryRepository

```kotlin
interface CategoryHistoryRepository {
    suspend fun searchByPrefix(prefix: String): List<String>
    suspend fun save(category: String)
}
```

#### PendingExpenseRepository

```kotlin
interface PendingExpenseRepository {
    suspend fun save(expense: ExpenseRecord): Long  // 自動生成されたIDを返す
    suspend fun getAll(): List<ExpenseRecord>
    suspend fun delete(id: Long)
}
```

#### SettingsRepository

```kotlin
interface SettingsRepository {
    suspend fun getFolderId(): String?
    suspend fun saveFolderId(id: String)
    suspend fun getSpreadsheetId(year: Int): String?
    suspend fun saveSpreadsheetId(year: Int, id: String)
    suspend fun isAuthenticated(): Boolean
    suspend fun saveAuthenticated(authenticated: Boolean)
    suspend fun getAllowanceAmount(): Int?
    suspend fun saveAllowanceAmount(amount: Int?)

    // OAuth認証（DataStore に永続化）
    suspend fun getAuthToken(): String?
    suspend fun saveAuthToken(token: String)
    suspend fun saveIsAuthenticated(isAuthenticated: Boolean)
    suspend fun saveAccountEmail(email: String)
    suspend fun getAccountEmail(): String?

    // 合計額キャッシュ（DataStore に永続化、アプリkill後も保持）
    suspend fun saveYearlyTotal(total: Int?)
    suspend fun getYearlyTotal(): Int?
    suspend fun saveMonthlyTotal(total: Int?)
    suspend fun getMonthlyTotal(): Int?
}
```

## Data Models

### ExpenseRecord

```kotlin
data class ExpenseRecord(
    val id: Long = 0,
    val amount: Int,        // 円単位の整数
    val date: LocalDate,
    val category: String
)
```

### AppendResult

```kotlin
data class AppendResult(
    val spreadsheetId: String,
    val sheetId: Int,       // シートのID（行削除APIで使用）
    val sheetName: String,
    val rowIndex: Int       // 追記された行のインデックス（始まり1）
)
```

### UndoTarget

```kotlin
data class UndoTarget(
    val spreadsheetId: String,
    val sheetId: Int,
    val sheetName: String,
    val rowIndex: Int,
    val expense: ExpenseRecord
)
```

### SummaryResult

```kotlin
data class SummaryResult(
    val yearlyTotal: Int?,  // 今年の合計額（円）。取得失敗時はnull
    val monthlyTotal: Int?  // 今月の合計額（円）。取得失敗時はnull
)
```

### ExpenseUiState

```kotlin
data class ExpenseUiState(
    val amountText: String = "",        // 表示用の金額文字列
    val date: LocalDate = LocalDate.now(),
    val category: String = "",
    val suggestions: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val message: String? = null,        // 成功/エラーメッセージ
    val error: ValidationError? = null,
    val spreadsheetUrl: String? = null, // 直近の年のスプレッドシートURL
    val undoTarget: UndoTarget? = null, // 取り消し対象（Snackbar表示中はnull以外）
    val showUndoSnackbar: Boolean = false,
    val undoExpenseLabel: String = "",  // Undo Snackbarに表示する記録内容（例: "4/22 ¥1,500 ランチ"）
    val yearlyTotal: Int? = null,       // 今年の合計額（円）。取得失敗/未取得時はnull
    val monthlyTotal: Int? = null,      // 今月の合計額（円）。取得失敗/未取得時はnull
    val isSummaryLoading: Boolean = false, // 合計額取得中フラグ
    val isRefreshing: Boolean = false,  // プルトゥリフレッシュフラグ
    val allowanceAmount: Int? = null,   // 現在のお小遣い額（円）。未設定時はnull
    val isRemainingMode: Boolean = false, // 残額表示モード（true: 残額表示、false: 通常表示）
    val showAllowanceDialog: Boolean = false // お小遣い設定ダイアログの表示状態
)
```

### Room Entity（カテゴリ履歴）

```kotlin
@Entity(tableName = "category_history")
data class CategoryHistoryEntity(
    @PrimaryKey val category: String,
    val lastUsed: Long = System.currentTimeMillis()
)
```

### Room Entity（一時保存経費）

```kotlin
@Entity(tableName = "pending_expenses")
data class PendingExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Int,
    val date: String,       // "yyyy-MM-dd" 形式（LocalDate.toString()）
    val category: String,
    val createdAt: Long = System.currentTimeMillis()
)
```

## Correctness Properties

*プロパティとは、システムのすべての有効な状態において成立すべき特性や振る舞いのことである。人間が期待する仕様と、機械で検証可能な正式な命題をつなぐ実装の指針。*

### Property 1: 金額入力の数値解釈

*For any* テキストフィールドに入力された文字列に対して、Amount_Inputが受け付ける値は数字のみで構成された文字列であり、結果は整数として解釈可能な数値文字列である。全文字列は金額（未入力）として扱われる。

**Validates: Requirements 2.2, 2.3**

### Property 2: 日付のM/dフォーマット

*For any* 有効なLocalDateに対して、日付フォーマット関数は「M/d」形式（月・日ともに先頭ゼロなし）の文字列を返し、その文字列をパースすると元の月・日と一致する。

**Validates: Requirements 3.1, 3.3**

### Property 3: カテゴリ前方一致検索の正確性

*For any* カテゴリ履歴リストと任意のプレフィックス文字列に対して、suggest()が返すすべてのカテゴリは当該プレフィックスで始まり、かつ履歴リスト内にプレフィックスに一致するカテゴリがすべて結果に含まれる。

**Validates: Requirements 4.1**

### Property 4: カテゴリ履歴のラウンドトリップ

*For any* 有効なカテゴリ文字列に対して、save()で保存した後にsearchByPrefix()でそのカテゴリの先頭文字列を検索すると、保存したカテゴリが結果に含まれる。

**Validates: Requirements 4.4**

### Property 5: 入力バリデーションの完全性

*For any* 金額文字列とカテゴリ文字列と認証状態（Boolean）の組み合わせに対して、validate()はエラーを返す：(a) 金額が空文字列または"¥"の場合はAMOUNT_EMPTYエラーを返す、(b) 金額が有効で数値でカテゴリが空文字列または認証が未認証の場合はCATEGORY_EMPTYエラーを返す、(c) すべての有効な場合はValidを返す。

**Validates: Requirements 6.1, 6.2, 7.2**

### Property 6: SpreadsheetResolverのシート名解決

*For any* 有効なLocalDateに対して、SpreadsheetResolver.resolve()が返すSpreadsheetTargetのsheetNameは「M月」形式（例: 「4月」）であり、月の値はExpenseRecordの日付の月と一致する。

**Validates: Requirements 5.1, 9.1**

### Property 7: フォルダ・スプレッドシートの冪等性

*For any* 同一のフォルダ名に対して、ensureFolder()を複数回呼び出しても、Google Drive上に作成されるフォルダは1つだけであり、毎回同一のフォルダIDが返る。同様に、同一の年に対してresolve()を複数回呼び出しても、スプレッドシートは1つだけ作成される。

**Validates: Requirements 8.3, 9.6**

### Property 8: 年別スプレッドシートの初期構成

*For any* 新規作成されたYearly_Spreadsheetに対して、シートの構成は先頭が「まとめ」シート、続いて「1月」〜「12月」の12シートの合計13シートであり、各月シートの1行目はヘッダー行（A列: 「日付」、B列: 「金額」、C列: 「カテゴリ」）であり、まとめシートにはSUM関数が設定されている。

**Validates: Requirements 9.3, 9.4, 9.5, 10.1, 10.2**

### Property 9: スプレッドシートURL生成の正確性

*For any* 有効なスプレッドシートIDに対して、getCurrentYearSpreadsheetUrl()が返すURLは「https://docs.google.com/spreadsheets/d/{spreadsheetId}」形式であり、スプレッドシートが存在しない場合はnullを返す。

**Validates: Requirements 11.2, 11.4**

### Property 10: Undo Snackbarの状態遷移

*For any* 直前記録成功後（スプレッドシートID、シートID、行番号、ExpenseRecordを含む）に対して、(a) 記録成功直後はshowUndoSnackbarがtrueかつundoTargetがnull以外かつundoExpenseLabelが「{M/d} ¥{金額（カンマ区切り）}（{カテゴリ}）」形式の文字列である、(b) onUndoClick()呼び出し後はonSnackbarDismiss()呼び出し後と同様にshowUndoSnackbarがfalseかつundoTargetがnullである、(c) showUndoSnackbarがfalseの場合はSnackbar APIを呼び出してはならない。

**Validates: Requirements 12.1, 12.3, 12.6, 12.7, 12.8**

### Property 11: 取り消し操作のラウンドトリップ

*For any* 有効なExpenseRecordに対して、appendExpense()で追記した直後にdeleteRow()で同じ行を削除すると、スプレッドシートの状態は追記前と同等になる（追記された行が存在しない）。

**Validates: Requirements 12.4, 12.9**

### Property 12: 合計額取得のフォールバック

*For any* 認証状態（Boolean）とスプレッドシート存在状態（Boolean）とネットワーク接続状態（Boolean）の組み合わせに対して、SummaryFetcher.fetchSummary()は以下を返す：(a) 認証未完了の場合はyearlyTotalとmonthlyTotalがともにnull、(b) 認証済みでスプレッドシートが存在しない場合はyearlyTotalとmonthlyTotalがともにnull、(c) ネットワーク接続なしの場合はyearlyTotalとmonthlyTotalがともにnull。

**Validates: Requirements 13.7, 13.8, 13.9**

### Property 13: 合計額表示の整合性

*For any* SummaryResultに対して、yearlyTotalがnullの場合はExpense_Summary_Areaに「—」が表示され、null以外の場合は円形フォーマットされた金額文字列が表示される。monthlyTotalについても同様である。

**Validates: Requirements 13.1, 13.7**

### Property 14: お小遣い・残額計算の正確性

*For any* 有効なAllowance_Amount（正の整数）と今年の合計額（非負の整数）と今月の合計額（非負の整数）に対して、残額表示における今月の残額は「Allowance_Amount − 今月の合計額」と一致し、今年の残額は「Allowance_Amount × 12 − 今年の合計額」と一致する。

**Validates: Requirements 14.4, 14.5**

### Property 15: 合計額表示トグルの動作規則

*For any* Allowance_Amount設定状態（設定済み/未設定）とSummary_Display_Mode（通常/残額）の組み合わせに対して、(a) Allowance_Amountが未設定の場合はonSummaryAreaClick()を呼び出してもisRemainingModeはfalseのままである、(b) Allowance_Amountが設定済みの場合はonSummaryAreaClick()を呼び出すたびにisRemainingModeがtrue/falseで切り替わる。

**Validates: Requirements 14.2, 14.6**

### Property 16: お小遣い設定のラウンドトリップ

*For any* 有効なお小遣い額（正の整数）に対して、saveAllowanceAmount()で保存した後にgetAllowanceAmount()で取得すると、保存した値と一致する。nullを保存した場合はnullが返る。

**Validates: Requirements 14.1**

### Property 17: 合計額キャッシュの加減算整合性

*For any* 初期合計額（整数）と記録・取り消しのシーケンスに対して、SummaryCacheのyearlyTotalとmonthlyTotalは以下を満たす：(a) update()で初期値を設定した後、adjust(amount)を呼び出すとyearlyTotalとmonthlyTotalがそれぞれamount増加する、(b) adjust(-amount)を呼び出すとyearlyTotalとmonthlyTotalがそれぞれamount減少する、(c) adjust(amount)の後にadjust(-amount)を呼び出すと元の値に戻る。

**Validates: Requirements 13.3, 13.4**

### Property 18: Undo Snackbar記録表示フォーマット

*For any* 有効なExpenseRecord（日付、金額、カテゴリ）に対して、Undo Snackbarに表示されるundoExpenseLabelは「{M/d} ¥{金額（カンマ区切り）}（{カテゴリ}）」形式の文字列であり、日付はExpenseRecordのdateのM/d形式、金額はカンマ区切り形式、カテゴリはExpenseRecordのcategoryと一致する。

**Validates: Requirements 12.3**

## Error Handling

### エラー分類と対応

| エラー種別 | 発生条件 | 対応 |
|-----------|---------|------|
| バリデーションエラー | 金額・カテゴリ入力が不正 | エラーメッセージを表示、記録を実行しない |
| 未認証エラー | OAuth認証未完了 | 「Googleアカウントを連携してください」メッセージ表示、認証フロー開始 |
| フォルダ作成エラー | Google Drive APIでのフォルダ作成失敗 | エラーメッセージ表示、リトライ可能 |
| スプレッドシート作成エラー | Google Sheets APIでのスプレッドシート作成失敗 | エラーメッセージ表示、経費をローカルに一時保存 |
| ネットワークエラー | Google Sheets APIへの接続失敗/断 | エラーメッセージ表示、経費をローカルに一時保存 |
| 取り消しエラー | 行削除API呼び出し失敗 | エラーメッセージを表示、Undo Snackbarを非表示にする |
| 合計額取得エラー | Summary_Sheet/Monthly_Sheetからの読み取り失敗 | 合計額に「—」プレースホルダーを表示、アプリの他の機能は正常に動作する |

### ローカルファースト保存と再送

記録ボタンタップ時の処理フロー：
1. まず`PendingExpenseRepository`に経費データをローカル保存（画面に反映）
2. Google Sheets APIでスプレッドシートに追記を試みる
3. 成功した場合：`PendingExpenseRepository`から該当レコードを削除
4. 失敗した場合：ユーザーにエラーメッセージを表示（データはローカルに保存済み）

起動時の再送処理：
1. アプリ起動時に`PendingExpenseRepository`に未送信データがあるか確認
2. 未送信データが存在する場合、バックグラウンドでスプレッドシートへの再送を試みる
3. 再送成功時に`PendingExpenseRepository`から該当レコードを削除

この方式により、記録ボタンタップ直後にアプリがバックグラウンドに移行したりプロセスがkillされた場合でも、データロスを防止できる。

### バリデーション優先順位

validate()は以下の順序でチェックを行い、最初に検出されたエラーを返す：
1. 全数字チェック（空文字列 or "¥"）
2. カテゴリチェック（空文字列 or 空白のみ）
3. 認証状態チェック（isAuthenticated == false）

## Testing Strategy

### テストフレームワーク

| テスト種別 | フレームワーク |
|-----------|--------------|
| ユニットテスト | JUnit 5 + MockK |
| プロパティベーステスト | Kotest (Property-Based Testing) |
| UIテスト | Compose UI Test |
| インテグレーションテスト | JUnit 5 + MockK |

### プロパティベーステスト

KotestのプロパティベーステストAPIを使用し、各プロパティを最低100イテレーションで実行する。

各テストには以下の形式でタグを付与する：
```
// Feature: spend-manager, Property {number}: {property_text}
```

対象プロパティ：
- Property 1: 金額入力の数値解釈（Arb.string()で金額・カテゴリ、Arb.boolean()で認証状態を生成し、数字のみの文字列が整数として解釈可能であることを検証）
- Property 2: 日付のM/dフォーマット（Arb.localDate()で日付を生成）
- Property 3: カテゴリ前方一致検索（Arb.list(Arb.string())でデータを生成）
- Property 4: カテゴリ履歴のラウンドトリップ（Arb.string()でカテゴリを生成、インメモリDBで検証）
- Property 5: 入力バリデーション（Arb.string()で金額・カテゴリ、Arb.boolean()で認証状態を生成し、validate()の戻り値を検証）
- Property 6: SpreadsheetResolverのシート名解決（Arb.localDate()で日付を生成、モックリポジトリでfetchSummary()の戻り値を検証）
- Property 7: フォルダ・スプレッドシートの冪等性（Arb.string()でフォルダ名を生成、モックDrive APIで検証）
- Property 8: 年別スプレッドシートの初期構成（Arb.int(2022..2099)で年を生成、モックSheets APIで検証）
- Property 9: スプレッドシートURL生成（Arb.string()でスプレッドシートIDを生成、URL形式を検証）
- Property 10: Undo Snackbarの状態遷移（Arb.int()で金額、Arb.string()でカテゴリ、Arb.localDate()で日付を生成し、undo/dismissの状態遷移を検証）
- Property 11: 取り消し操作のラウンドトリップ（Arb.int()で金額を生成し、モックSheets APIでappend→deleteの正確性を検証）
- Property 12: 合計額取得のフォールバック（Arb.int().orNull()でyearlyTotal/monthlyTotalを生成し、null時は「—」、null以外はフォーマット済み金額文字列が表示されることを検証）
- Property 13: 合計額表示の整合性（Arb.int().orNull()で合計額を生成し、残額計算の正確性を検証）
- Property 14: お小遣い・残額計算の正確性（Arb.positiveInt()でAllowance_Amountを生成し、残額計算の正確性を検証）
- Property 15: 合計額表示トグルの動作規則（Arb.int().orNull()でお小遣い額を生成、DataStore save-getの整合性を検証）
- Property 16: お小遣い設定のラウンドトリップ（Arb.int().orNull()でお小遣い額を生成、DataStore save-getの整合性を検証）
- Property 17: 合計額キャッシュの加減算整合性（Arb.int()で初期合計額とdeltaを生成し、SummaryCacheの加減算結果を検証）
- Property 18: Undo Snackbar記録表示フォーマット（Arb.localDate()で日付、Arb.int(1..10000000)で金額、Arb.string()でカテゴリを生成し、undoExpenseLabelのフォーマットを検証）

### ユニットテスト（例ベース）

- ExpenseEntryScreen: 起動時に全要素（Settings_Iconを含む）が表示されること（Req 1.1）
- AmountInputTextField: 起動時にAmount_Inputに数値キーボードが表示されること（Req 2.4）
- AmountInputTextField: IMEアクションNextでCategory_Inputにフォーカスが移動すること（Req 2.6）
- CategoryInput: フォーカス取得時に日本語テキストキーボードが表示されること（Req 2.5）
- DateInput: 起動時に本日がM/d形式で表示されること（Req 3.1）
- DatePicker: Date_InputタップでカレンダーUIが表示されること（Req 3.2）
- CategorySuggestionEngine: 候補選択時にリストが非表示になること（Req 4.3）
- ExpenseViewModel: 記録成功時に金額・カテゴリが空になり、日付が本日にリセットされること（Req 5.2, 5.3）
- ExpenseViewModel: 取り消し成功時にローカルキャッシュが減算更新されること（Req 13.4）
- ExpenseViewModel: プルトゥリフレッシュ時にスプレッドシートから合計額が再取得されること（Req 13.5）
- ExpenseViewModel: 記録失敗時にエラーメッセージ表示・ローカル保存されること（Req 5.4）
- ExpenseValidator: 未認証の場合に記録ができないこと（Req 7.2）
- SpreadsheetResolver: フォルダ未存在時に自動作成されること（Req 8.2）
- SpreadsheetResolver: フォルダ既存時に再利用されること（Req 8.3）
- SpreadsheetResolver: 年別スプレッドシート未存在時に自動作成されること（Req 9.2）
- SpreadsheetResolver: 年別スプレッドシート既存時に再利用されること（Req 9.4）
- GoogleSheetsRepository: まとめシートに対応するSUM関数が設定されること（Req 10.1）
- GoogleSheetsRepository: 月別シートの1行目にヘッダー行（日付、金額、カテゴリ）が記録されること（Req 9.4）
- OpenSpreadsheetButton: ボタン入力間に入力欄に重ならないこと（Req 11.3）
- ExpenseViewModel: Open_Spreadsheet_Buttonタップ時にSheetsアプリ優先（なければDriveアプリ、なければブラウザ）でスプレッドシートが開かれること（Req 11.2）
- 未認証状態: スプレッドシート未存在時にメッセージが表示されること（Req 11.4）
- ExpenseViewModel: お小遣い設定時にExpense_Summary_Areaタップでトグルが発生しないこと（Req 14.6）
- UndoSnackbar: 記録された直後に日付・金額・カテゴリが正しい形式で表示されること（Req 12.3）
- SummaryCache: adjust()が正しく合計額を加減算すること（Req 13.3, 13.4）
- SettingsRepository: お小遣い額の保存・取得が正しく動作すること（Req 14.1）
- SettingsIcon: ギアアイコンタップでAllowance_Dialogが表示されること（Req 15.2）
- AllowanceDialog: 最初から編集可能な状態で表示され、金額が¥フォーマットで表示されること（Req 15.3, 15.4）
- AllowanceDialog: 金額を入力して「決定」タップで保存・ダイアログが閉じること（Req 15.5）
- AllowanceDialog: 金額を空にして「決定」タップでnull保存（お小遣い無制限）されること（Req 15.6）

### インテグレーションテスト

- GoogleSheetsRepository: モックサーバーを使用してAPI呼び出しの正しさを検証（Req 5.1）
- GoogleDriveRepository: モックサーバーを使用してファイル操作の正しさを検証（Req 8.1, 8.2, 9.1, 9.2）
- OAuth認証フロー: 認証トークンの取得・保存を検証（Req 7.1）
