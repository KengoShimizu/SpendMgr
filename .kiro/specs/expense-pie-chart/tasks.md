# Implementation Plan: expense-pie-chart

## Overview

既存の SpendMgr アプリ（Kotlin + Jetpack Compose + MVVM + Hilt）に円グラフ表示機能を追加する。
実装は以下の順序で進める：

1. データモデルと新規インターフェースの定義
2. データ層の拡張（GoogleSheetsRepository、GoogleDriveRepository、SettingsRepository）
3. ドメイン層の新規クラス実装（CategoryColorAssigner、PieChartCache、PieChartFetcher）
4. ViewModel の拡張（ExpenseViewModel、ExpenseUiState）
5. UI コンポーネントの実装（PieChart、ChartLegend、ChartPeriodSelector、PieChartArea）
6. ExpenseEntryScreen への統合

## Tasks

- [x] 1. データモデルと sealed class の定義
  - `domain/model/` に `ChartPeriod.kt` を作成し、`Monthly`・`Yearly`・`PastYear(year: Int)` の sealed class と `toKey()` メソッドを実装する
  - `domain/model/` に `PieChartData.kt` を作成し、`PieChartData`・`CategoryData` データクラスを定義する
  - `PieChartData.toJson()` と `PieChartData.fromJson()` を実装する（color フィールドは JSON に含めず、復元時に `CategoryColorAssigner.colorFor()` で再計算する）
  - _Requirements: 3.1, 3.2, 4.5_

- [ ] 2. データ層の拡張
  - [ ] 2.1 SettingsRepository にパイチャートキャッシュ用メソッドを追加する
    - `SettingsRepository` インターフェースに `savePieChartCache(periodKey, data)`・`getPieChartCache(periodKey)`・`clearAllPieChartCache()` を追加する
    - `SettingsRepositoryImpl` に DataStore を使った実装を追加する（キー: `"pie_chart_cache_$periodKey"`）
    - _Requirements: 4.5_

  - [ ] 2.2 GoogleSheetsRepository に `fetchCategoryAmounts` を追加する
    - `GoogleSheetsRepository` インターフェースに `fetchCategoryAmounts(spreadsheetId, sheetName): Result<List<Pair<String, Int>>>` を追加する
    - `GoogleSheetsRepositoryImpl` に実装を追加する（B列: 金額、C列: カテゴリ、2行目以降を取得）
    - _Requirements: 4.2, 4.3, 4.4_

  - [ ]* 2.3 GoogleSheetsRepository の `fetchCategoryAmounts` のユニットテストを書く
    - ヘッダー行（1行目）が除外されること
    - B列・C列のデータが正しく Pair<String, Int> に変換されること
    - _Requirements: 4.4_

  - [x] 2.4 GoogleDriveRepository に `listSpreadsheetNames` を追加する
    - `GoogleDriveRepository` インターフェースに `listSpreadsheetNames(folderId): Result<List<String>>` を追加する
    - `GoogleDriveRepositoryImpl` に実装を追加する（4桁数字のスプレッドシート名のみフィルタリング）
    - _Requirements: 2.2_

  - [ ]* 2.5 GoogleDriveRepository の `listSpreadsheetNames` のユニットテストを書く
    - 4桁数字以外のファイル名が除外されること
    - 4桁数字のスプレッドシート名のみが返されること
    - _Requirements: 2.2_

- [x] 3. CategoryColorAssigner の実装
  - `domain/` に `CategoryColorAssigner.kt` を作成し、`object CategoryColorAssigner` として実装する
  - `colorFor(category: String): Color` を実装する（カテゴリ名のハッシュ値を使って Material 3 カラーパレットから決定論的に色を選択する）
  - 事前定義パレットとして Material 3 の視覚的に区別可能な色を10〜16色程度定義する
  - _Requirements: 3.2, 3.3_

  - [ ]* 3.1 CategoryColorAssigner のプロパティテストを書く
    - **Property 1: カテゴリ色割り当ての決定論性**
    - **Validates: Requirements 3.2**
    - `Arb.string()` でカテゴリ名を生成し、同じ入力に対して常に同じ色が返ることを検証する

  - [ ]* 3.2 CategoryColorAssigner のパレット制約プロパティテストを書く
    - **Property 2: カテゴリ色のパレット制約**
    - **Validates: Requirements 3.3**
    - `Arb.string()` でカテゴリ名を生成し、返される色が事前定義パレットに含まれることを検証する

- [x] 4. PieChartCache の実装
  - `domain/` に `PieChartCache.kt` を作成する
  - `get(period)`・`put(period, data)`・`addExpense(period, category, amount)`・`removeExpense(period, category, amount)`・`clearAll()` を実装する
  - `addExpense()`: キャッシュ未存在時は何もしない。新カテゴリの場合は新規エントリとして追加する
  - `removeExpense()`: 減算後の金額が0以下になった場合はそのカテゴリを削除する
  - `PieChartData.toJson()` / `fromJson()` を使って DataStore に永続化する
  - _Requirements: 4.5, 5.1, 5.2, 5.3_

  - [ ]* 4.1 PieChartCache のキャッシュ永続化ラウンドトリッププロパティテストを書く
    - **Property 5: キャッシュの永続化ラウンドトリップ**
    - **Validates: Requirements 4.5**
    - `Arb.list()` で `PieChartData` を生成し、`put → get` のラウンドトリップを検証する（カテゴリ名・金額・割合が一致）

  - [ ]* 4.2 PieChartCache の経費記録・取り消しラウンドトリッププロパティテストを書く
    - **Property 6: 経費記録・取り消しのラウンドトリップ**
    - **Validates: Requirements 5.1, 5.2**
    - `Arb.int()` で金額、`Arb.string()` でカテゴリを生成し、`addExpense → removeExpense` 後に元の状態に戻ることを検証する

  - [ ]* 4.3 PieChartCache のユニットテストを書く
    - `addExpense()` でキャッシュ未存在時に何もしないこと
    - `addExpense()` で新カテゴリが追加されること
    - `removeExpense()` で金額が0以下になったカテゴリが削除されること
    - `clearAll()` で全期間のキャッシュがクリアされること
    - _Requirements: 5.1, 5.2, 5.3_

- [x] 5. チェックポイント - ここまでのテストがすべてパスすることを確認する
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. PieChartFetcher の実装
  - `domain/` に `PieChartFetcher.kt` を作成する
  - `fetchPieChartData(period: ChartPeriod): Result<PieChartData?>` を実装する
    - まず `PieChartCache.get()` でキャッシュを返し、バックグラウンドでスプレッドシートから最新データを取得して `PieChartCache.put()` で更新する
  - `fetchAvailableYears(): Result<List<Int>>` を実装する
    - `GoogleDriveRepository.listSpreadsheetNames()` で取得した名前を Int に変換し、現在の年を除外して降順で返す
  - `aggregateYearlyData(spreadsheetId)`: 1月〜12月の全シートから `fetchCategoryAmounts()` を呼び出してカテゴリ別に合計する
  - `aggregateMonthlyData(spreadsheetId, sheetName)`: 指定月シートから `fetchCategoryAmounts()` を呼び出してカテゴリ別に合計する
  - 集計結果から `PieChartData`（割合・色を含む）を生成する
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 2.2_

  - [ ]* 6.1 PieChartFetcher のデータ集計プロパティテストを書く
    - **Property 4: データ集計の正確性**
    - **Validates: Requirements 4.2, 4.3, 4.4**
    - `Arb.list()` で経費データを生成し、集計結果が手動計算と一致することを検証する（ヘッダー行除外を含む）

  - [ ]* 6.2 PieChartFetcher の過去年フィルタリングプロパティテストを書く
    - **Property 10: 過去年フィルタリングの正確性**
    - **Validates: Requirements 2.3**
    - `Arb.list(Arb.int(2020..2030))` で年リストを生成し、現在の年が除外されることを検証する

  - [ ]* 6.3 PieChartFetcher のユニットテストを書く
    - キャッシュが存在する場合に即座にキャッシュデータを返すこと（Req 4.1）
    - キャッシュが存在しない場合にスプレッドシートからデータを取得すること（Req 4.1）
    - 期間切り替え時にキャッシュを即座に表示すること（Req 4.6）
    - _Requirements: 4.1, 4.6_

- [x] 7. ExpenseUiState と ExpenseViewModel の拡張
  - [x] 7.1 ExpenseUiState に円グラフ関連フィールドを追加する
    - `pieChartData: PieChartData? = null`
    - `selectedPeriod: ChartPeriod = ChartPeriod.Monthly`
    - `availableYears: List<Int> = emptyList()`
    - `isPieChartLoading: Boolean = false`
    - _Requirements: 1.3, 2.4, 6.1_

  - [x] 7.2 ExpenseViewModel に PieChartFetcher・PieChartCache の依存関係を追加する
    - コンストラクタに `pieChartFetcher: PieChartFetcher` と `pieChartCache: PieChartCache` を追加する
    - Hilt の `AppModule` に `PieChartFetcher` と `PieChartCache` のバインディングを追加する
    - _Requirements: 1.3_

  - [x] 7.3 ExpenseViewModel に円グラフ関連メソッドを実装する
    - `onPeriodSelect(period: ChartPeriod)`: 期間選択時にキャッシュを即座に表示し、バックグラウンドで最新データを取得する
    - `fetchAvailableYears()`: アプリ起動時に `PieChartFetcher.fetchAvailableYears()` を呼び出して `availableYears` を更新する
    - `updatePieChartCacheOnRecord(expense: ExpenseRecord)`: 経費記録成功時に `PieChartCache.addExpense()` を今月・今年の両期間に対して呼び出す
    - `updatePieChartCacheOnUndo(expense: ExpenseRecord)`: 経費取り消し成功時に `PieChartCache.removeExpense()` を今月・今年の両期間に対して呼び出す
    - `init` ブロックで `fetchAvailableYears()` と初期期間（今月）のデータ取得を呼び出す
    - `onPullToRefresh()` 拡張: `PieChartCache.clearAll()` を呼び出してから現在選択中の期間のデータを再取得する
    - _Requirements: 2.4, 2.5, 4.1, 4.6, 5.1, 5.2, 5.3_

  - [ ]* 7.4 ExpenseViewModel の円グラフ関連ユニットテストを書く
    - 経費記録成功時に今月・今年のキャッシュが加算更新されること（Req 5.1）
    - 経費取り消し成功時に今月・今年のキャッシュが減算更新されること（Req 5.2）
    - プルトゥリフレッシュ時に全キャッシュがクリアされて再取得されること（Req 5.3）
    - 期間選択時にキャッシュが即座に表示されること（Req 4.6）
    - _Requirements: 5.1, 5.2, 5.3, 4.6_

- [x] 8. チェックポイント - ここまでのテストがすべてパスすることを確認する
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. UI コンポーネントの実装
  - [x] 9.1 ChartPeriodSelector を実装する
    - `ui/components/` に `ChartPeriodSelector.kt` を作成する
    - `LazyRow` を使って横スクロール可能なチップ列を実装する
    - 「今月」「今年」および `availableYears` の各年をチップとして表示する
    - 選択中のチップを `FilledTonalChip` または `FilterChip` で強調表示する
    - _Requirements: 2.1, 2.3, 2.4, 2.6, 2.7_

  - [ ]* 9.2 ChartPeriodSelector のユニットテストを書く
    - 起動時に「今月」が初期選択されること（Req 2.4）
    - 選択中の期間が視覚的に区別されること（Req 2.6）
    - `availableYears` が空の場合に「今月」「今年」のみ表示されること（Req 2.7）
    - _Requirements: 2.4, 2.6, 2.7_

  - [x] 9.3 PieChart（ドーナツグラフ）を実装する
    - `ui/components/` に `PieChart.kt` を作成する
    - Jetpack Compose の `Canvas` API を使ってドーナツグラフを描画する
    - 各カテゴリの掃引角度を `(amount / totalAmount) * 360f` で計算する
    - 中央に合計金額を「¥X,XXX,XXX」形式で表示する（`drawText` または `Text` コンポーザブル）
    - _Requirements: 7.1, 7.2, 7.3, 7.5_

  - [ ]* 9.4 PieChart のスライス面積比例性プロパティテストを書く
    - **Property 7: 円グラフスライス面積の比例性**
    - **Validates: Requirements 7.2**
    - `Arb.list()` で `CategoryData` を生成し、各スライスの掃引角度が比例することを検証する（誤差 ±0.1 度以内）

  - [ ]* 9.5 PieChart の合計金額フォーマットプロパティテストを書く
    - **Property 8: 合計金額フォーマットの正確性**
    - **Validates: Requirements 7.3**
    - `Arb.int(0..Int.MAX_VALUE)` で金額を生成し、フォーマット結果が正規表現 `¥\d{1,3}(,\d{3})*` にマッチすることを検証する

  - [x] 9.6 ChartLegend を実装する
    - `ui/components/` に `ChartLegend.kt` を作成する
    - カテゴリを金額の降順でソートして表示する
    - 各行に色付き四角形・カテゴリ名・金額（¥フォーマット）・割合（小数点第1位まで）を表示する
    - _Requirements: 3.4, 7.4_

  - [ ]* 9.7 ChartLegend の凡例表示完全性プロパティテストを書く
    - **Property 3: 凡例表示の完全性**
    - **Validates: Requirements 3.4**
    - `Arb.list()` で `CategoryData` を生成し、描画された凡例に全必須フィールド（色・名前・¥金額・%割合）が含まれることを検証する

  - [ ]* 9.8 ChartLegend の凡例ソート順プロパティテストを書く
    - **Property 9: 凡例のソート順**
    - **Validates: Requirements 7.4**
    - `Arb.list()` で `CategoryData` を生成し、凡例が金額降順でソートされることを検証する

  - [x] 9.9 PieChartArea を実装する
    - `ui/components/` に `PieChartArea.kt` を作成する
    - 認証未完了時: 「Googleアカウントを連携してください」メッセージを表示する（Req 1.4）
    - スプレッドシート未存在時: 「データがありません」メッセージを表示する（Req 1.5）
    - ローディング中: `CircularProgressIndicator` を表示する（Req 6.1）
    - API エラー時: 「データの取得に失敗しました」メッセージを表示する（Req 6.2）
    - データ0件時: 「この期間の経費データがありません」メッセージを表示する（Req 6.3）
    - 正常時: `PieChart`・`ChartLegend`・`ChartPeriodSelector` を縦に並べて表示する（Req 1.2）
    - _Requirements: 1.2, 1.3, 1.4, 1.5, 6.1, 6.2, 6.3_

  - [ ]* 9.10 PieChartArea のユニットテストを書く
    - 認証未完了時に「Googleアカウントを連携してください」メッセージが表示されること（Req 1.4）
    - スプレッドシート未存在時に「データがありません」メッセージが表示されること（Req 1.5）
    - データ取得中にローディングインジケーターが表示されること（Req 6.1）
    - API 呼び出し失敗時に「データの取得に失敗しました」メッセージが表示されること（Req 6.2）
    - データ0件時に「この期間の経費データがありません」メッセージが表示されること（Req 6.3）
    - `PieChart`・`ChartLegend`・`ChartPeriodSelector` が含まれること（Req 1.2）
    - _Requirements: 1.2, 1.4, 1.5, 6.1, 6.2, 6.3_

  - [ ]* 9.11 期間選択とデータ対応のプロパティテストを書く
    - **Property 11: 期間選択とデータ対応**
    - **Validates: Requirements 2.5**
    - 選択された `ChartPeriod` に対して表示される `PieChartData` が対応するスプレッドシートデータから集計されたものであることを検証する

- [x] 10. ExpenseEntryScreen への統合
  - `ExpenseEntryScreen` の既存ボタン類（`RecordButton`・`OpenSpreadsheetButton`）の下に `PieChartArea` を追加する
  - `uiState.pieChartData`・`uiState.selectedPeriod`・`uiState.availableYears`・`uiState.isPieChartLoading` を `PieChartArea` に渡す
  - `onPeriodSelect` コールバックを `viewModel.onPeriodSelect()` に接続する
  - `isAuthenticated` と `hasSpreadsheet`（`uiState.spreadsheetUrl != null`）を `PieChartArea` に渡す
  - _Requirements: 1.1, 1.2, 1.3_

- [x] 12. クレジットカード払いフラグ機能の追加
  - [x] 12.1 ExpenseRecord に `isCreditCard: Boolean = true` フィールドを追加する
  - [x] 12.2 PendingExpenseEntity に `isCreditCard` カラムを追加し、DB マイグレーション（version 2）を実施する
  - [x] 12.3 GoogleSheetsRepositoryImpl の `appendExpense()` でD列に `TRUE`/`FALSE` を書き込む
  - [x] 12.4 GoogleSheetsRepositoryImpl の `createYearlySpreadsheet()` でD列ヘッダーとチェックボックス入力規則を設定する
  - [x] 12.5 GoogleSheetsRepository に `fetchCreditCardTotal()` を追加し実装する
  - [x] 12.6 SettingsRepository / SettingsRepositoryImpl にカード払い合計キャッシュメソッドを追加する
  - [x] 12.7 PieChartData に `creditCardTotal: Int?` フィールドを追加し、JSON シリアライズ/デシリアライズに対応する
  - [x] 12.8 PieChartFetcher で `fetchCreditCardTotalForPeriod()` を実装し、`PieChartData` に含める
  - [x] 12.9 PieChartCache の `addExpense()`・`removeExpense()` に `isCreditCard` パラメータを追加し `creditCardTotal` を更新する
  - [x] 12.10 ExpenseUiState に `isCreditCard: Boolean = true` を追加する
  - [x] 12.11 ExpenseViewModel に `onCreditCardChange()` を追加し、`onRecordClick()` で `isCreditCard` を `ExpenseRecord` に渡す
  - [x] 12.12 ExpenseEntryScreen にチェックボックス UI を追加する（カテゴリ入力の下、記録ボタンの上）
  - [x] 12.13 PieChartArea に家計立替表示を追加する（期間セレクタと同じ行の右側）
  - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7_

- [x] 11. 最終チェックポイント - すべてのテストがパスすることを確認する
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- `*` が付いたサブタスクはオプションであり、MVP を優先する場合はスキップ可能
- 各タスクは対応する要件番号を参照しており、トレーサビリティを確保している
- `CategoryData.color` は DataStore に保存せず、復元時に `CategoryColorAssigner.colorFor(name)` で再計算する
- `PieChartCache.addExpense()` はキャッシュ未存在時は何もしない（次回バックグラウンド取得時に同期される）
- 円グラフは Jetpack Compose Canvas API で自前実装（外部ライブラリ不使用）
- プロパティテストは Kotest の Property-Based Testing API を使用し、最低100イテレーションで実行する
- 各プロパティテストには `// Feature: expense-pie-chart, Property {number}: {property_text}` のタグを付与する
