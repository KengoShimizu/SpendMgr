# Requirements Document

## Introduction

SpendMgrは、個人の日常経費を迅速に記録し、Googleスプレッドシートに自動追記するAndroidアプリである。家族の経費と個人の経費を分けて管理したいというニーズに応え、最小限の操作で支出を記録できることを目指す。

## Glossary

- **SpendMgr_App**: 個人経費記録用のAndroidアプリケーション
- **Expense_Entry_Screen**: アプリ起動時に表示される経費入力画面
- **Amount_Input**: 金額を入力するためのテキストフィールド（OS標準の数値キーボードを使用、keyboardType = KeyboardType.Number）
- **Category_Input**: カテゴリを入力するためのテキストフィールド（入力候補付き）
- **Date_Input**: 日付を入力するためのフィールド（本日の日付がデフォルト設定済み、カレンダーUIで変更可能）
- **Record_Button**: 入力内容をGoogleスプレッドシートに追記するボタン
- **Open_Spreadsheet_Button**: 直近の年のYearly_Spreadsheetをアプリで開くためのアイコンボタン。TopAppBarの右上（Settings_Iconの左）に配置される
- **Category_Suggestion_Engine**: 過去の入力履歴からカテゴリ候補を提示する機能
- **Google_Sheets_Connector**: Googleスプレッドシートとの連携を行うモジュール
- **Google_Drive_Manager**: Google Drive上のフォルダ・スプレッドシートの自動作成・管理を行うモジュール
- **SpendMgr_Folder**: Google Driveのトップレベルに作成される「SpendMgr」フォルダ
- **Yearly_Spreadsheet**: SpendMgr_Folderに年ごとに作成されるGoogleスプレッドシート（例: 2026, 2027）
- **Monthly_Sheet**: Yearly_Spreadsheetの月別シート（1月〜12月）
- **Monthly_Sheet_Header**: @Monthly_Sheetの1行目に設定されるヘッダー行。「日付」「金額」「カテゴリ」の3列で構成
- **Expense_Record**: 金額、日付、カテゴリの3項目で構成される1件の経費データ
- **Undo_Snackbar**: 経費記録成功後に画面下部に表示される「取り消し」ボタン、タップすると追記処理をスプレッドシートから削除される
- **Expense_Summary_Area**: Expense_Entry_Screenに表示される今年の合計経費と今月の合計経費を表示するエリア
- **Summary_Fetcher**: Summary_SheetおよびMonthly_Sheetから合計経費を取得するモジュール
- **Allowance_Settings**: 毎月のお小遣い額を設定・保存する機能。設定値はアプリ内で固定管理
- **Summary_Display_Modes**: Expense_Summary_Areaの表示モード。通常表示（合計額）と節約表示（お小遣い額超過）の2種類がある
- **Summary_Cache**: 合計値をローカルに保持するキャッシュ。DataStoreに永続化されるためアプリkill後も値が保持される。起動時はDataStoreから即座に値を復元し、バックグラウンドでスプレッドシートから最新値を取得する。記録追加・取り消し成功時はローカルで加算減算し、API呼び出しを最小化する
- **Monthly_Sheet_Header**: @Monthly_Sheetの1行目に設定されるヘッダー行。「日付」「金額」「カテゴリ」の3列で構成される
- **Settings_Icon**: Expense_Entry_Screenの右上に配置されるギアアイコン。タップするとAllowance_Dialogを表示する
- **Allowance_Dialog**: お小遣い額を設定するためのダイアログ。現在の設定値を表示し、確認・保存・削除が可能

## Requirements

### Requirement 1: 経費入力画面の表示

**User Story:** ユーザーとして、アプリを開いたらすぐに経費入力画面が表示されてほしい。素早く記録を開始できるようにするため。

#### Acceptance Criteria

1. WHEN SpendMgr_App が起動された時、THE Expense_Entry_Screen SHALL 金額入力欄、日付入力欄、カテゴリ入力欄、Record_Button、Settings_Icon、Open_Spreadsheet_Button を表示する
2. THE Expense_Entry_Screen SHALL 1行目に1列で操作可能な状態になる

### Requirement 2: 金額入力（数値キーボード）

**User Story:** ユーザーとして、OS標準の数値キーボードで金額を素早く入力したい。慣れ親しんだキーボードで数字入力をスムーズに行えるようにするため。

#### Acceptance Criteria

1. THE Amount_Input SHALL OS標準の数値キーボード（keyboardType = KeyboardType.Number）を使用するテキストフィールドとして表示する
2. WHEN ユーザーがAmount_Inputに数字を入力した時、THE Amount_Input SHALL 入力された数字を金額として画面に反映し、先頭に「¥」を付与して3桁ごとにカンマ区切りで表示する（例: ¥10,000）
3. THE Amount_Input SHALL 入力方法は数値（円単位）として扱い、小数点に「¥」を付与して3桁ごとにカンマ区切りで表示する（例: ¥10,000）
4. WHEN SpendMgr_Appが起動された時、THE Amount_Input SHALL 自動的にフォーカスを取得し、OS標準の数値キーボードを表示する
5. THE Amount_Input SHALL IMEアクションをNext（imeAction = ImeAction.Next）に設定する
6. WHEN ユーザーがAmount_InputでIMEアクションキー（IMEアクション: Next）をタップした時、THE Amount_Input SHALL フォーカスをCategory_Inputに移動する

### Requirement 3: 日付入力のデフォルト設定

**User Story:** ユーザーとして、日付が本日の日付でデフォルト設定されていてほしい。ほとんどの場合は当日の支出を記録するため、入力の手間を省きたい。

#### Acceptance Criteria


1. WHEN SpendMgr_App が起動された時、THE Date_Input SHALL 本日の日付がデフォルト設定された状態で表示される
2. THE Date_Input SHALL カレンダーUIで日付を変更できる
3. THE Date_Input SHALL 日付を「M/d」形式（月・日ともに先頭ゼロなし）で表示する（例: 4/22）

### Requirement 4: カテゴリ入力補助

#### Acceptance Criteria

1. WHEN ユーザーがCategory_Inputに文字を入力した時、THE Category_Suggestion_Engine SHALL 入力文字列に前方一致するカテゴリ情報を過去の入力履歴から検索し、候補リストとして表示する
2. WHEN ユーザーが候補リストからカテゴリを選択した時、THE Category_Input SHALL 選択されたカテゴリをCategory_Inputに設定する
3. WHEN 一致する候補が存在しない時、THE Category_Suggestion_Engine SHALL 候補リストを非表示にする
4. THE Category_Suggestion_Engine SHALL カテゴリ入力履歴をデバイスのローカルストレージに保存する
5. THE Category_Input SHALL IMEアクションをDone（imeAction = ImeAction.Done）に設定し、サーボタイプを日本語テキスト入力（keyboardType = KeyboardType.Text）に設定する
6. WHEN Amount_InputからCategory_Inputにフォーカスが移動した時、THE Category_Input SHALL 日本語入力キーボード（keyboardType = KeyboardType.Text）を表示する

### Requirement 5: 経費記録のGoogleスプレッドシートへの追記

**User Story:** ユーザーとして、記録ボタンを押したら経費データがGoogleスプレッドシートの該当月シートに自動追記されてほしい。別途手動でスプレッドシートを開いて転記する手間をなくすため。

#### Acceptance Criteria

1. WHEN ユーザーがRecord_Buttonをタップした時、THE SpendMgr_App SHALL まずExpense_RecordをPendingExpenseRepositoryにローカル保存し、その後Google_Sheets_Connectorを通じてExpense_Recordの日付の年に対するYearly_Spreadsheetを検索し、日付の月に対するMonthly_Sheet（例: 4月なら「4月」シート）の最終行の次にExpense_Record（金額、日付、カテゴリ）を追記する
2. WHEN スプレッドシートへの追記が成功した時、THE SpendMgr_App SHALL 追記成功後にExpense_Record（金額、日付、カテゴリ）を追記する
3. WHEN 追記が成功した時、THE Date_Input SHALL 本日の日付に再設定する
4. IF スプレッドシートへの追記に失敗した時、THEN THE SpendMgr_App SHALL エラーメッセージを表示する（Expense_Recordはローカルに保持済み）
5. WHEN SpendMgr_Appが起動された時、THE SpendMgr_App SHALL PendingExpenseRepositoryに未追記のExpense_Recordを前回確認し、存在する場合はバックグラウンドでスプレッドシートへの再追記を試みる
6. WHEN 再追記が成功した時、THE SpendMgr_App SHALL PendingExpenseRepositoryから当該レコードを削除する

### Requirement 6: 入力バリデーション

**User Story:** ユーザーとして、不完全なデータが記録されることを防ぎたい。正確な経費管理を続けるため。

#### Acceptance Criteria

1. WHEN ユーザーがRecord_Buttonをタップした時に金額が空または入力の場合、THE SpendMgr_App SHALL「金額を入力してください」というエラーメッセージを表示し、記録を実行しない
2. WHEN ユーザーがRecord_Buttonをタップした時にカテゴリが空の場合、THE SpendMgr_App SHALL「カテゴリを入力してください」というエラーメッセージを表示し、記録を実行しない

### Requirement 7: Google連携OAuth認証

**User Story:** ユーザーとして、GoogleアカウントでログインするだけでスプレッドシートI連携を開始したい。手動でスプレッドシートIDを設定する手間をなくすため。

#### Acceptance Criteria

1. THE SpendMgr_App SHALL GoogleアカウントによるAuth認証を通じてGoogle DriveおよびGoogle Sheetsのアクセス権限を取得する
2. WHEN 認証が未完了の時にRecord_ButtonかOpen_Spreadsheet_Buttonがタップされた場合、THE SpendMgr_App SHALL「Googleアカウントを連携してください」というメッセージを表示し、認証フローを開始する
3. THE SpendMgr_App SHALL OAuth認証のスコープにGoogle Drive APIのファイル作成・管理権限とGoogle Sheets APIの読み書き権限を含める

### Requirement 8: Google Driveフォルダの自動作成

**User Story:** ユーザーとして、Google連携後にフォルダが自動的に作成されてほしい。手動でフォルダを準備する手間をなくすため。

#### Acceptance Criteria

1. WHEN OAuth認証が完了した時、THE Google_Drive_Manager SHALL Google Driveのトップレベル（マイドライブ直下）に「SpendMgr」という名前のSpendMgr_Folderが存在するか確認する
2. WHEN SpendMgr_Folderが存在しない場合、THE Google_Drive_Manager SHALL Google Driveのトップレベルに「SpendMgr」フォルダを新規作成する
3. WHEN SpendMgr_Folderが既に存在する場合、THE Google_Drive_Manager SHALL 既存のフォルダを利用し、重複作成しない

### Requirement 9: 年別スプレッドシートの自動作成

**User Story:** ユーザーとして、年ごとのスプレッドシートが自動で作成されてほしい。年が変わっても手動で新しいスプレッドシートを用意する必要がないようにするため。

#### Acceptance Criteria

1. WHEN 経費記録が実行される時、THE Google_Drive_Manager SHALL Expense_Recordの日付の年（例: 「2026」）がSpendMgr_Folderに存在するか確認する
2. WHEN 対応するYearly_Spreadsheetが存在しない場合、THE Google_Drive_Manager SHALL SpendMgr_Folderに新しいYearly_Spreadsheet（例: 「2026」）を作成する
3. 「6月」「7月」「8月」「9月」「10月」「11月」「12月」の12個のMonthly_Sheetを作成する
4. WHEN 初めてYearly_Spreadsheetが作成される時、THE Google_Drive_Manager SHALL @Monthly_Sheetの1行目にMonthly_Sheet_Headerとして「日付」（A列）「金額」（B列）「カテゴリ」（C列）のヘッダー行を設定し、経費データ入力が2行目以降に開始されるようにする
5. THE Monthly_Sheet SHALL 経費データを人月・日付（D/M形式）、円形金額（数値）、（列×カテゴリ（文字列）の形式で格納する
6. WHEN 対応するYearly_Spreadsheetが既に存在する場合、THE Google_Drive_Manager SHALL 既存のスプレッドシートを再利用し、重複作成しない

### Requirement 10: まとめシートの月別合計表示

**User Story:** ユーザーとして、年間の月別支出合計を一覧で確認したい。支出傾向を把握するため。

#### Acceptance Criteria

1. WHEN Yearly_Spreadsheetが新規作成された時、THE Google_Drive_Manager SHALL Summary_Sheetに「月」列と「合計」列のヘッダーを設定し、1月〜12月の各行に対応するMonthly_Sheetの8列（金額列）の合計を参照するSUM関数を設定する
2. THE Summary_Sheet SHALL @Monthly_Sheetにデータが追記されるたびに合計が自動更新されるようにスプレッドシート関数で実現する

### Requirement 11: スプレッドシートへのワンタップ遷移

**User Story:** ユーザーとして、経費入力画面からワンタップでGoogleスプレッドシートを開きたい。記録した経費データをすぐに確認できるようにするため。

#### Acceptance Criteria

1. THE Expense_Entry_Screen SHALL Open_Spreadsheet_ButtonをTopAppBarの右上（Settings_Iconの左）にアイコンボタンとして表示する
2. WHEN ユーザーがOpen_Spreadsheet_Buttonをタップした時、THE SpendMgr_App SHALL 現在の年に対するYearly_SpreadsheetをSheetsアプリ（未インストールの場合はDriveアプリ、それもない場合はブラウザ）で開く
3. WHEN OAuth認証が未完了の時にOpen_Spreadsheet_Buttonがタップされた場合、THE SpendMgr_App SHALL「Googleアカウントを連携してください」というメッセージを表示し、認証フローを開始する
4. IF 現在の年に対するYearly_Spreadsheetが存在しない場合、THEN THE SpendMgr_App SHALL「スプレッドシートがまだ作成されていません。経費を記録すると自動作成されます」というメッセージを表示する

### Requirement 12: 記録直後の取り消し（Undo Snackbar）

**User Story:** ユーザーとして、経費を記録した直後に取り消しができるようにしたい。誤入力した場合にスプレッドシートを開いて手動で削除する手間をなくすため。

#### Acceptance Criteria

1. WHEN 経費記録がスプレッドシートへの追記に成功した時、THE Undo_Snackbar SHALL 画面下部にスライドインで表示され、Record_Button、Open_Spreadsheet_Button、Amount_Inputのいずれにも重ならない位置に配置される
2. THE Undo_Snackbar SHALL Material 3のSnackbarコンポーネントを使用して実装する
3. THE Undo_Snackbar SHALL 記録された経費の日付・金額・カテゴリを「（M/D）¥（金額（カンマ区切り））【カテゴリ】の形式で表示する（例:「4/22 ¥1,500 ランチ」）
4. WHEN ユーザーがUndo_Buttonをタップした時、THE Google_Sheets_Connector SHALL 直前に追記したExpense_Recordをスプレッドシートの取り込み行から削除し、「取り消し」ラベルのUndo_Buttonを含む
5. WHEN 取り消しが成功した時、THE SpendMgr_App SHALL 取り消し操作を受け付けない
6. THE Undo_Snackbar SHALL 表示開始から5秒後に自動消滅し、SpendMgr_Appは取り消し操作を受け付けない
7. WHEN Undo_Snackbarが表示された状態で、THE SpendMgr_App SHALL 取り消し操作を受け付けない
8. THE SpendMgr_App SHALL 取り消し対象のExpense_Recordの情報（スプレッドシートID、行番号）をUndo_Snackbarが表示されている間のみ一時的に保持する
9. IF 取り消し処理中にGoogle_Sheets_Connectorがスプレッドシートの接続に失敗した場合、THEN THE SpendMgr_App SHALL エラーメッセージを表示する

### Requirement 13: 今年・今月の合計経費表示

**User Story:** ユーザーとして、経費入力画面で年と今月の合計経費を確認したい。目の支出状況を把握しながら記録できるようにするため。

#### Acceptance Criteria

1. THE Expense_Entry_Screen SHALL Expense_Summary_Areaを経費入力画面に表示し、今年の合計経費と今月の合計経費を表示する
2. WHEN SpendMgr_Appが起動された時、THE Summary_Fetcher SHALL Summary_Sheetから今年の合計経費を取得してMonthly_Sheetから今月の合計経費を取得し、Expense_Summary_Areaに反映する
3. WHEN 経費記録の取り込みが成功した時、THE SpendMgr_App SHALL Summary_Cacheのローカルキャッシュを加算更新する。記録した経費が今年かつ今月の場合は yearlyTotal と monthlyTotal の両方を加算する。今年だが今月以外の場合は yearlyTotal のみ加算する。今年以外の場合は更新しない。スプレッドシートからの再取得は行わない
4. WHEN 経費記録の取り消しが成功した時、THE SpendMgr_App SHALL Summary_Cacheのローカルキャッシュを減算更新する。取り消した経費が今年かつ今月の場合は yearlyTotal と monthlyTotal の両方を減算する。今年だが今月以外の場合は yearlyTotal のみ減算する。今年以外の場合は更新しない。スプレッドシートからの再取得は行わない
5. WHEN ユーザーがExpense_Entry_Screenでプルトゥリフレッシュ操作を行った時、THE Summary_Fetcher SHALL スプレッドシートから合計値を再取得してSummary_Cacheを更新し、Expense_Summary_Areaに反映する
6. THE SpendMgr_App SHALL 合計取得のAPI呼び出しをアプリ起動時とプルトゥリフレッシュ時のみに限定する
7. IF Summary_FetcherがスプレッドシートへのAPIに失敗した場合、THEN THE Expense_Summary_Area SHALL 前回取得済みのキャッシュ値を表示し続ける。キャッシュが存在しない場合のみ「—」をプレースホルダーとして表示する
8. IF 直近の年に対応するYearly_Spreadsheetが存在しない場合、THEN THE Expense_Summary_Area SHALL 合計額の代わりに「—」をプレースホルダーとして表示する
9. IF OAuth認証が未完了の場合、THEN THE Expense_Summary_Area SHALL 合計額の代わりに「—」をプレースホルダーとして表示する

### Requirement 14: お小遣い設定と節約モード表示

**User Story:** ユーザーとして、毎月のお小遣い額を設定し、設定入力画面で視覚的に節約状況を把握しながら記録できるようにするため。

#### Acceptance Criteria

1. THE SpendMgr_App SHALL Settings_Iconのタップで表示されるAllowance_Dialogを通じてAllowance_Amountを設定できる機能を提供し、設定値をデバイスのローカルストレージに永続化する
2. WHEN Allowance_Amountが設定されている場合、THE Expense_Summary_Area SHALL タップ可能な状態になり、タップするたびにSummary_Display_Modeが通常表示と節約表示の間で切り替わる
3. WHILE Summary_Display_Modeが通常表示の場合、THE Expense_Summary_Area SHALL「今年の合計」「今月: ¥（今年の合計額）」の形式で表示する
4. WHILE Summary_Display_Modeが節約表示の場合、THE Expense_Summary_Area SHALL「今月: ¥（今年の合計額）」「今年: ¥（年間お小遣い計）」の形式で表示する
5. THE Expense_Summary_Area SHALL 節約表示における今月のお小遣い残高をAllowance_Amount × 12で算出する
6. WHEN Allowance_Amountが設定されていない場合、THE Expense_Summary_Area SHALL タップしても何も起こらず、通常表示のみ行う

### Requirement 15: お小遣い設定ダイアログUI

**User Story:** ユーザーとして、トップ画面からお小遣い額を簡単に設定・変更・削除したい。設定画面に遷移せずに素早く操作できるようにするため。

#### Acceptance Criteria

1. THE Expense_Entry_Screen SHALL 右上にSettings_Icon（ギアアイコン）を配置する
2. WHEN ユーザーがSettings_Iconをタップした時、THE SpendMgr_App SHALL Allowance_Dialogを表示する
3. WHEN Allowance_Dialogが表示された時、THE Allowance_Dialog SHALL 現在のAllowance_Amountの設定値を¥フォーマット（例: ¥10,000）で表示し、最初から編集可能な状態にする（未設定の場合は空欄）
4. WHEN ユーザーが金額を入力した時、THE Allowance_Dialog SHALL 入力中もリアルタイムで¥フォーマットで表示する
5. WHEN ユーザーが金額を入力して「決定」ボタンをタップした時、THE Allowance_Dialog SHALL 入力された金額をAllowance_Amountとして保存し、ダイアログを閉じる
6. WHEN ユーザーが金額を空にして「決定」ボタンをタップした時、THE Allowance_Dialog SHALL Allowance_Amountをnullとして保存し（お小遣い設定を解除）、ダイアログを閉じる
