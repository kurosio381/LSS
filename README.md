# LSS
### Kurosio_Log_Search_System
ログの内容をMinecraftサーバー内から検索できるプラグイン。

## 概要
特定のユーザーの特定のプラグインの動作履歴をログファイルから検索して表示・ファイル出力ができるプラグイン。

## 導入 
１.Pluginsフォルダ内にKurosio_log_search_systemフォルダを作成  
２.上記新規フォルダ内に「logs」「lssresult」の２つのフォルダを新規作成  
３.logsフォルダに検索したい [.log/.gz/.tar]をコピペ  
４.サーバー内でコマンドを実行

## コマンド集
```ruby 
/lss p:<日付> [lm:ログ番号] u:<プレイヤー名> pl:<pl名> [d:latest=チャット表示行] [f:ファイル保存するか]  
``` 
例：```/lss p:2025-01-01 lm:2 u:kurosio381 pl:ShopChest d:latest=30 f:yes```  
例：```/lss p:2025-01-01 u:kurosio381 pl:ShopChest```  

#### 日付指定 p:<日付>

```ruby
p:yyyy-MM-dd
```
```ruby
p:latest
```
  
例：```p:2025-01-01```  
latestの場合、latast.logが参照されます。  

#### ログ番号 lm:<ログ番号> ※任意
```ruby
lm:2     
```
※ 2025-01-24-<ins>2</ins>  ←これ  
デフォルト：1   
2以降は必ず指定してください。  

#### プレイヤー名 u:<プレイヤー名>  
```ruby
u:kurosio381  
```

#### プラグイン名 pl:<PL名>
```ruby
pl:ShopChest  
```
PL名は```[00: 00: 00] [Server thread/INFO]: [ShopChest] Player kurosio381 bought item from Current 96400.0```  
の場合```[ShopChest]```に該当します。そのため、コマンド送信履歴などは検索できません。  

#### チャット表示行数 d:latest=<0~50>  ※任意
```ruby
d:latest=24 
```  
チャットに表示する取得行数を変更します。デフォ：50  
ファイル内の過去の時刻から表示します。最大50。0で表示なし。

#### ファイル保存要否 f:<要否> ※任意 
```ruby
f:yes
```
```ruby
f:not
```
ファイルを生成して抽出結果を保存するか決めます。デフォ：not  
ファイル出力時には51行以上でもすべて保存されます。

