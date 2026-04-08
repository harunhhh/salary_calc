
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

public class SalaryCalculator {
    // ▼ Google APIを動かすための設定
    private static final String APPLICATION_NAME = "My Salary App";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens"; // ログイン情報を保存する隠しフォルダ
    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR_READONLY);
    private static final String CREDENTIALS_FILE_PATH = "credentials.json"; // さっきゲットした許可証

    // ▼ Googleへのログイン認証を行う魔法のメソッド
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws Exception {
        InputStreamReader clientSecretsReader = new InputStreamReader(new FileInputStream(CREDENTIALS_FILE_PATH));
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, clientSecretsReader);

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    public static void main(String[] args) {
        int hourlyWage = 1260; 
        double totalHours = 0.0;     
        double totalRestHours = 0.0; 

        System.out.println("=== 📅 Googleカレンダー自動同期 給料計算システム ===");
        System.out.println("Googleに接続しています...");

        try {
            // Googleと通信する準備
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            Calendar service = new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                    .setApplicationName(APPLICATION_NAME)
                    .build();

            // 「今月」の最初から最後までの時間をセット
            YearMonth currentMonth = YearMonth.now();
            ZonedDateTime startOfMonth = currentMonth.atDay(1).atStartOfDay(ZoneId.systemDefault());
            ZonedDateTime endOfMonth = currentMonth.atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.systemDefault());
            DateTime timeMin = new DateTime(startOfMonth.toInstant().toEpochMilli());
            DateTime timeMax = new DateTime(endOfMonth.toInstant().toEpochMilli());

            // 自分のカレンダー（primary）から、今月の予定を引っ張ってくる！
            Events events = service.events().list("primary")
                    .setTimeMin(timeMin)
                    .setTimeMax(timeMax)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute();

            List<Event> items = events.getItems();
            if (items.isEmpty()) {
                System.out.println("今月の予定は見つかりませんでした。");
            } else {
                System.out.println("✅ カレンダーデータの取得に成功しました！計算を開始します。\n");
                
                // カレンダーの予定を1つずつ取り出して計算
                for (Event event : items) {
                    String shiftTitle = event.getSummary(); // 予定のタイトル（"14-0"など）
                    
                    if (shiftTitle != null) {
                        double rawHours = calculateRawHours(shiftTitle);

                        // 計算できた（0時間より大きい）予定だけを給料として処理する
                        if (rawHours > 0) {
                            double restHours = calculateRestTime(rawHours);

                            double actualHours = rawHours - restHours;

                            totalHours += actualHours;
                            totalRestHours += restHours;

                            // 予定の日付を取得して画面に出す
                            DateTime start = event.getStart().getDateTime();
                            if (start == null) start = event.getStart().getDate();
                            String dateStr = start.toString().substring(0, 10); // "2026-04-08" の部分だけ切り取る

                            System.out.println(dateStr + " [" + shiftTitle + "]" + "\t" + "労働: " + actualHours + "h (休憩: " + restHours + "h)"+" 日給："+hourlyWage*(actualHours - restHours));
                        }
                    }
                }
            }

            // 最終結果の表示
            int totalSalary = (int) (totalHours * hourlyWage);
            System.out.println("====================================================");
            System.out.println("💰 今月の合計労働時間: " + totalHours + " 時間");
            System.out.println("☕ 今月の合計休憩時間: " + totalRestHours + " 時間");
            System.out.println("💰 今月の見込み給料: " + totalSalary + " 円");

        } catch (FileNotFoundException e) {
            System.out.println("❌ エラー: credentials.json がフォルダに見つかりません！");
        } catch (Exception e) {
            System.out.println("❌ エラーが発生しました: " + e.getMessage());
        }
    }

    // =========================================================
    // ▼ ここから下は、あなた自身が作り上げた最強の計算ロジックです！
    // =========================================================
    public static double calculateRawHours(String shiftText) {
        try {
            String[] parts = shiftText.split("-");
            if (parts.length != 2) return 0.0; 

            double start = parseTime(parts[0].trim()); // 念のため余計な空白を消す(trim)
            double end = parseTime(parts[1].trim());

            if (end <= start) {
                end += 24.0;
            }

            return end - start;
            
        } catch (Exception e) {
            return 0.0; 
        }
    }

    public static double calculateRestTime(double shiftTime){
        if(shiftTime >= 8.0){
            return 1.0;
        } else if(shiftTime >= 6.0){
            return 0.75;
        } else {
            return 0.0;
        }
    }

    public static double parseTime(String timeText) {
        if (timeText.contains(":")) {
            String[] timeParts = timeText.split(":");
            double hour = Double.parseDouble(timeParts[0]);
            double min = Double.parseDouble(timeParts[1]);
            return hour + (min / 60.0);
        } else {
            return Double.parseDouble(timeText);
        }
    }

    
}