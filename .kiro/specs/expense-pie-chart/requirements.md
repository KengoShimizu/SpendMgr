# Requirements Document

## Introduction

expense-pie-chart は、SpendMgr アプリの Expense_Entry_Screen 下部の空白スペースに円グラフを追加する機能である。ユーザーは選択した期間の経費をカテゴリ別に色分けした円グラフで視覚的に把握できる。表示期間は「今月」「今年」および Google Drive 上に存在する過去年のスプレッドシートから選択できる。データは既存の Google スプレッドシートから取得し、カテゴリごとに集計して表示する。

## Glossary

- **Pie_Chart**: カテゴリ別経費の割合を扇形で表示する円グラフコンポーネント
- **Pie_Chart_Area**: Expense_Entry_Screen 下部に配置される円グラフ表示エリア（グラフ本体 + 凡例 + 期間セレクター + 家計立替表示）
- **Chart_Period_Selector**: 表示期間を選択するUIコンポーネント。「xx年」プルダウンと「xx月/年間」プルダウンの2つで構成される。年と月が連動して期間を決定する
- **Chart_Period**: 円グラフに表示するデータの期間。`MONTHLY`（今月）、`YEARLY`（今年）、`PAST_YEAR(year: Int)`（過去年）の3種類
- **Available_Years**: Google Drive の SpendMgr フォルダ内に存在する Yearly_Spreadsheet の年のリスト。アプリ起動時に取得する
- **Category_Slice**: 円グラフ内の1カテゴリに対応する扇形セグメント
- **Category_Color**: 各カテゴリに割り当てられる固定の色。カテゴリ名をキーとして決定論的に割り当てられる
- **Chart_Legend**: 各 Category_Slice に対応するカテゴリ名・金額・割合を示す凡例リスト
- **Pie_Chart_Data**: 円グラフ描画に必要なデータ。カテゴリ名・金額・割合・色のリストおよび家計立替合計で構成される
- **Pie_Chart_Fetcher**: Google スプレッドシートから指定期間のカテゴリ別経費データおよびカード払い合計を取得するモジュール
- **Pie_Chart_Cache**: カテゴリ別経費データを DataStore に永続化するキャッシュ。Chart_Period をキーとして保持する。アプリkill後の再起動時も前回のデータを即座に表示し、バックグラウンドでスプレッドシートから最新データを取得して更新する
- **Monthly_Sheet**: 既存の月別シート（1月〜12月）。A列に日付（M/d形式）、B列に金額、C列にカテゴリ、D列にカード払いフラグ（TRUE/FALSE）、E列に割り勘人数が格納される
- **Yearly_Spreadsheet**: 既存の年別スプレッドシート
- **Credit_Card_Flag**: スプレッドシートD列に記録されるカード払いフラグ。TRUE/FALSE のチェックボックス形式。新規スプレッドシート作成時にD列ヘッダーとチェックボックス入力規則が自動設定される
- **家計立替**: クレジットカード払いとしてフラグが立った経費の合計額（割り勘前の金額）。選択中の期間に連動して Pie_Chart_Area 内に表示される

## Requirements

### Requirement 1: 円グラフエリアの表示

**User Story:** ユーザーとして、経費入力画面の下部に円グラフを表示してほしい。カテゴリ別の支出割合を一目で把握できるようにするため。

#### Acceptance Criteria

1. THE Expense_Entry_Screen SHALL Pie_Chart_Area を既存のボタン類（Record_Button、Open_Spreadsheet_Button）の下に表示する
2. THE Pie_Chart_Area SHALL Pie_Chart（円グラフ本体）、Chart_Legend（凡例）、Chart_Period_Toggle（期間切り替えトグル）を含む
3. WHILE OAuth 認証が完了しており、かつ対応する Yearly_Spreadsheet が存在する場合、THE Pie_Chart_Area SHALL データを取得して円グラフを表示する
4. IF OAuth 認証が未完了の場合、THEN THE Pie_Chart_Area SHALL 円グラフの代わりに「Googleアカウントを連携してください」というメッセージを表示する
5. IF 対応する Yearly_Spreadsheet が存在しない場合、THEN THE Pie_Chart_Area SHALL 円グラフの代わりに「データがありません」というメッセージを表示する

### Requirement 2: 期間セレクター

**User Story:** ユーザーとして、年と月を個別に選んで円グラフを確認したい。任意の年月の支出傾向を把握できるようにするため。

#### Acceptance Criteria

1. THE Chart_Period_Selector SHALL「xx年」プルダウンと「xx月/年間」プルダウンの2つを横に並べて表示する
2. THE Chart_Period_Selector の「xx年」プルダウン SHALL Available_Years に現在の年を含めた全年を選択肢として表示する
3. THE Chart_Period_Selector の「xx月」プルダウン SHALL 1月〜12月と「年間」を選択肢として表示する
4. WHEN SpendMgr_App が起動された時、THE Chart_Period_Selector SHALL 現在の年・現在の月をデフォルト選択状態として表示する
5. WHEN ユーザーが年プルダウンで年を選択した時、THE Pie_Chart_Area SHALL 選択された年・現在選択中の月に対応するデータで円グラフを更新する
6. WHEN ユーザーが月プルダウンで月または「年間」を選択した時、THE Pie_Chart_Area SHALL 現在選択中の年・選択された月（または年間）に対応するデータで円グラフを更新する
7. IF Available_Years の取得に失敗した場合、THEN THE Chart_Period_Selector SHALL 現在の年のみを年プルダウンに表示する

### Requirement 3: カテゴリ別色分け

**User Story:** ユーザーとして、カテゴリごとに異なる色で円グラフを表示してほしい。どのカテゴリがどの扇形に対応するかを直感的に識別できるようにするため。

#### Acceptance Criteria

1. THE Pie_Chart SHALL 各 Category_Slice を Category_Color で色分けして表示する
2. THE Category_Color SHALL カテゴリ名から決定論的に割り当てられ、同じカテゴリには常に同じ色が使用される
3. THE Category_Color SHALL Material 3 のカラーパレットから視覚的に区別可能な色を使用する
4. THE Chart_Legend SHALL 各カテゴリの Category_Color、カテゴリ名、金額（¥フォーマット）、割合（%表示、小数点第1位まで）を表示する

### Requirement 4: データ取得と集計

**User Story:** ユーザーとして、スプレッドシートのデータが円グラフに正確に反映されてほしい。実際の支出状況を正確に把握するため。

#### Acceptance Criteria

1. WHEN Pie_Chart_Area が表示される時、THE Pie_Chart_Fetcher SHALL まず Pie_Chart_Cache から前回のデータを即座に表示し、バックグラウンドでスプレッドシートから最新データを取得して更新する
2. WHEN Chart_Period が `YEARLY` または `PAST_YEAR(year)` の場合、THE Pie_Chart_Fetcher SHALL 対象年の全 Monthly_Sheet（1月〜12月）からカテゴリ（C列）と金額（B列）のデータを取得し、カテゴリ別に合計する
3. WHEN Chart_Period が `MONTHLY` の場合、THE Pie_Chart_Fetcher SHALL 今月の Monthly_Sheet からカテゴリ（C列）と金額（B列）のデータを取得し、カテゴリ別に合計する
4. THE Pie_Chart_Fetcher SHALL ヘッダー行（1行目）を除いた2行目以降のデータのみを集計対象とする
5. THE Pie_Chart_Fetcher SHALL 取得したデータを Pie_Chart_Cache に Chart_Period をキーとして DataStore に永続化する
6. WHEN Chart_Period が切り替えられた時、THE Pie_Chart_Area SHALL Pie_Chart_Cache に該当 Chart_Period のデータが存在する場合はキャッシュを即座に表示し、バックグラウンドで最新データを取得して更新する

### Requirement 5: データ更新

**User Story:** ユーザーとして、経費を記録した後に円グラフが最新の状態に更新されてほしい。記録直後の支出状況を確認できるようにするため。

#### Acceptance Criteria

1. WHEN 経費記録がスプレッドシートへの追記に成功した時、THE Pie_Chart_Cache SHALL 今月・今年の Chart_Period に対応するキャッシュのカテゴリ金額を加算更新し、DataStore に永続化して円グラフを再描画する
2. WHEN 経費記録の取り消しが成功した時、THE Pie_Chart_Cache SHALL 今月・今年の Chart_Period に対応するキャッシュのカテゴリ金額を減算更新し、DataStore に永続化して円グラフを再描画する
3. WHEN ユーザーがプルトゥリフレッシュ操作を行った時、THE Pie_Chart_Fetcher SHALL 全 Chart_Period の Pie_Chart_Cache をクリアし、現在選択中の Chart_Period のデータをスプレッドシートから再取得して円グラフを更新する

### Requirement 6: ローディングとエラー表示

**User Story:** ユーザーとして、データ取得中や取得失敗時に適切なフィードバックを受け取りたい。アプリの状態を把握できるようにするため。

#### Acceptance Criteria

1. WHILE Pie_Chart_Fetcher がデータを取得中の場合、THE Pie_Chart_Area SHALL 円グラフの代わりにローディングインジケーターを表示する
2. IF Pie_Chart_Fetcher がスプレッドシートへの API 呼び出しに失敗した場合、THEN THE Pie_Chart_Area SHALL 円グラフの代わりに「データの取得に失敗しました」というメッセージを表示する
3. IF 選択中の Chart_Period に対応するデータが0件の場合、THEN THE Pie_Chart_Area SHALL 円グラフの代わりに「この期間の経費データがありません」というメッセージを表示する

### Requirement 8: クレジットカード払いフラグの記録

**User Story:** ユーザーとして、経費がクレジットカード払いかどうかを記録したい。家計への立替分を把握できるようにするため。

#### Acceptance Criteria

1. THE Expense_Entry_Screen SHALL カテゴリ入力の下にクレジットカード払いチェックボックスを表示する
2. THE クレジットカード払いチェックボックス SHALL デフォルトでチェック済み状態とする
3. WHEN 経費が記録される時、THE GoogleSheetsRepository SHALL スプレッドシートのD列にカード払いフラグを `TRUE`（チェックあり）または `FALSE`（チェックなし）として記録する
4. WHEN 新規スプレッドシートが作成される時、THE GoogleSheetsRepository SHALL 各月シートのD1セルに「カード払い」ヘッダーを設定し、D2:D1000 にチェックボックス入力規則を設定する
5. WHEN 経費記録が取り消される時、THE クレジットカード払いフラグ SHALL 取り消し対象の行とともに削除される

### Requirement 9: 家計立替の表示

**User Story:** ユーザーとして、選択中の期間のカード払い合計（家計立替）を確認したい。家計に請求すべき金額を把握できるようにするため。

#### Acceptance Criteria

1. THE Pie_Chart_Area SHALL 期間セレクタと同じ行の右側に「家計立替」ラベルと金額を表示する
2. THE 家計立替金額 SHALL 選択中の期間に連動して更新される
3. IF 選択中の期間のカード払いデータが存在しない場合、THEN THE 家計立替表示 SHALL 非表示とする
4. THE 家計立替金額 SHALL D列が `TRUE` または `○` の行の金額（B列）を合計した値とする（割り勘人数で割らない）
5. THE 家計立替金額 SHALL Pie_Chart_Data の `creditCardTotal` フィールドとして保持され、DataStore にキャッシュされる
6. WHEN 経費記録が成功した時、THE Pie_Chart_Cache SHALL カード払いフラグに応じて `creditCardTotal` を加算更新する
7. WHEN 経費記録が取り消された時、THE Pie_Chart_Cache SHALL カード払いフラグに応じて `creditCardTotal` を減算更新する

**User Story:** ユーザーとして、見やすく情報量の多い円グラフを確認したい。支出の全体像と内訳を同時に把握できるようにするため。

#### Acceptance Criteria

1. THE Pie_Chart SHALL カテゴリ数が1件以上の場合に扇形セグメントを描画する
2. THE Pie_Chart SHALL 各 Category_Slice の面積をカテゴリの合計金額の全体に対する割合に比例させる
3. THE Pie_Chart SHALL 中央に合計金額を「¥(金額)」形式（3桁カンマ区切り）で表示する（ドーナツグラフ形式）
4. THE Chart_Legend SHALL カテゴリを金額の降順で表示する
5. THE Pie_Chart SHALL Jetpack Compose の Canvas API を使用して描画する
6. WHEN ユーザーが Category_Slice をタップした時、THE Pie_Chart SHALL タップされたスライスを拡大・外側にオフセットして強調表示し、他のスライスを半透明にする。タップ位置の近くにカテゴリ名・金額・割合を示すフローティングラベルを表示する。タップ判定は内周より内側（穴）を除いたエリアで有効とする
7. WHEN ユーザーが内周より内側（穴）をタップした時、THE Pie_Chart SHALL 通常表示に戻る
