package com.example;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@SpringBootApplication
@RestController
public class SalaryCalculator {

    private static final String APPLICATION_NAME = "My Salary Web App";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    public static void main(String[] args) {
        SpringApplication.run(SalaryCalculator.class, args);
    }

    // ★大進化！ ログインした人の情報（oauth2User）と、その人の鍵（authorizedClient）を自動で受け取ります
    @GetMapping("/")
    public ResponseEntity<String> home(
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
            @AuthenticationPrincipal OAuth2User oauth2User,
            @RequestParam(value = "wage", defaultValue = "1260") int hourlyWage) throws Exception {

        String userName = oauth2User.getAttribute("name");

        // ★修正ポイント1： html変数の作成は最初の一回だけ！
        // ★修正ポイント2： 古いデザインと新しいデザインのタグを綺麗に合体させました
        StringBuilder html = new StringBuilder();
        html.append("<html><head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("<title>給料計算ダッシュボード</title>");
        html.append("<style>body{font-family:sans-serif; padding:20px; background:#f4f7f6; color:#333;} .container{max-width: 650px; margin: 0 auto;} .card{background:#fff; padding:20px; border-radius:10px; box-shadow:0 4px 8px rgba(0,0,0,0.1); margin-bottom:20px;} input[type='number']{padding:8px; border:1px solid #ccc; border-radius:5px; width:100px; font-size:16px;} button{padding:8px 15px; background:#3498db; color:#fff; border:none; border-radius:5px; cursor:pointer; font-size:16px;}</style>");
        html.append("</head><body>");
        html.append("<div class='container'>"); // 全体を中央寄せにするコンテナ

        try {
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            
            Credential credential = new Credential(BearerToken.authorizationHeaderAccessMethod())
                    .setAccessToken(authorizedClient.getAccessToken().getTokenValue());

            Calendar service = new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                    .setApplicationName(APPLICATION_NAME).build();

            YearMonth currentMonth = YearMonth.now();
            ZonedDateTime startOfMonth = currentMonth.atDay(1).atStartOfDay(ZoneId.systemDefault());
            ZonedDateTime endOfMonth = currentMonth.atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.systemDefault());
            DateTime timeMin = new DateTime(startOfMonth.toInstant().toEpochMilli());
            DateTime timeMax = new DateTime(endOfMonth.toInstant().toEpochMilli());

            Events events = service.events().list("primary")
                    .setTimeMin(timeMin).setTimeMax(timeMax).setOrderBy("startTime").setSingleEvents(true).execute();

            List<Event> items = events.getItems();
            
            double totalShiftHours = 0.0;
            double totalBreakHours = 0.0;
            double totalWorkHours = 0.0;
            double totalOvertimeHours = 0.0;
            int totalSalary = 0;

            html.append("<h2 style='color: #2c3e50;'>📱 " + userName + "さんの給与ダッシュボード</h2>");

            // ① 画面から時給を変更できるフォーム
            html.append("<div class='card'>");
            html.append("<form method='get' action='/'>");
            html.append("<label>💰 時給: </label>");
            html.append("<input type='number' name='wage' value='" + hourlyWage + "'> 円 ");
            html.append("<button type='submit'>再計算</button>");
            html.append("</form>");
            html.append("</div>");

            // カレンダーの予定を1つずつループして計算
            if (items.isEmpty()) {
                html.append("<div class='card'><p>今月のシフトは見つかりませんでした。</p></div>");
            } else {
                java.util.Map<String, Double> dailyHours = new java.util.HashMap<>();

                for (Event event : items) {
                    String summary = event.getSummary();
                    double shiftHours = 0.0;
                    boolean hasTime = false;
                    String dateKey = "";

                    // ★カレンダーの日付を取得（時間指定 or 終日予定どちらでもOK！）
                    if (event.getStart().getDateTime() != null) {
                        dateKey = new java.text.SimpleDateFormat("yyyy-MM-dd")
                                .format(new java.util.Date(event.getStart().getDateTime().getValue()));
                    } else if (event.getStart().getDate() != null) {
                        dateKey = event.getStart().getDate().toString().substring(0, 10);
                    }

                    if (dateKey.isEmpty()) continue;

                    // ★最強AI機能：タイトルから「9-0」のような数字を読み取る！
                    if (summary != null) {
                        // "9-0"、"10-19"、"9〜0" のような文字を探す
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9]{1,2})\\s*[-〜~]\\s*([0-9]{1,2})").matcher(summary);
                        if (m.find()) {
                            int s = Integer.parseInt(m.group(1));
                            int e = Integer.parseInt(m.group(2));
                            if (e <= s) {
                                e += 24; // 終了時間が開始時間より小さい場合（0時など）は翌日の24時扱い
                            }
                            shiftHours = (double) (e - s);
                            hasTime = true;
                        }
                    }

                    // ★タイトルに時間が書いていない場合は、カレンダーの登録時間を使う
                    if (!hasTime) {
                        DateTime start = event.getStart().getDateTime();
                        DateTime end = event.getEnd().getDateTime();
                        if (start != null && end != null) {
                            long durationMs = end.getValue() - start.getValue();
                            shiftHours = durationMs / (1000.0 * 60.0 * 60.0);
                            hasTime = true;
                        }
                    }

                    // 時間が計算できたら、その日の合計に足し算！
                    if (hasTime && shiftHours > 0) {
                        dailyHours.put(dateKey, dailyHours.getOrDefault(dateKey, 0.0) + shiftHours);
                    }
                }

                // ② まとめた「1日ごとの合計時間」を使って、休憩と残業を計算する
                for (double dailyShift : dailyHours.values()) {
                    double breakTime = 0.0;
                    double actualWork = 0.0;
                    double overtime = 0.0;

                    if (dailyShift >= 8.0) {
                        breakTime = 1.0;
                    } else if (dailyShift > 6.0) {
                        breakTime = 0.75;
                    }

                    actualWork = dailyShift - breakTime;

                    if (actualWork > 8.0) {
                        overtime = actualWork - 8.0;
                    }

                    totalShiftHours += dailyShift;
                    totalBreakHours += breakTime;
                    totalWorkHours += actualWork;
                    totalOvertimeHours += overtime;

                    double normalWork = actualWork - overtime;
                    totalSalary += (int) ((normalWork * hourlyWage) + (overtime * hourlyWage * 1.25));
                }

                // ③ 労働時間の合計を表示するダッシュボード
                html.append("<div class='card' style='background:#e8f4f8;'>");
                html.append("<h3 style='margin-top:0;'>📊 今月の集計</h3>");
                html.append("<p>実働時間: <strong>" + String.format("%.2f", totalWorkHours) + " 時間</strong></p>");
                html.append("<p>休憩時間: " + String.format("%.2f", totalBreakHours) + " 時間</p>");
                html.append("<p>残業時間: <span style='color:red;'>" + String.format("%.2f", totalOvertimeHours) + " 時間</span></p>");
                html.append("<hr>");
                html.append("<h2 style='color:#e74c3c;'>💴 予想給与: " + String.format("%,d", totalSalary) + " 円</h2>");
                html.append("<p style='font-size:0.8em; color:#7f8c8d;'>※残業代（時給1.25倍）を含みます</p>");
                html.append("</div>");
            }

            html.append("</div></body></html>"); // コンテナとbodyを閉じる
            
        } catch (Exception e) {
            html.append("<div class='card' style='background:#ffebee; color:#c62828;'>");
            html.append("<h3>❌ エラーが発生しました</h3>");
            html.append("<p>" + e.getMessage() + "</p>");
            html.append("</div></div></body></html>"); // エラー時もちゃんとタグを閉じる
            e.printStackTrace();
        }
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                .body(html.toString());
    }

    // =========================================================
    // ▼ 計算ロジック群（変更なし）
    // =========================================================
    public static double calculateRawHours(String shiftText) {
        try {
            String[] parts = shiftText.split("-");
            if (parts.length != 2) return 0.0; 
            double start = parseTime(parts[0].trim());
            double end = parseTime(parts[1].trim());
            if (end <= start) end += 24.0;
            return end - start;
        } catch (Exception e) { return 0.0; }
    }

    public static double calculateNightHours(String shiftText) {
        try {
            String[] parts = shiftText.split("-");
            if (parts.length != 2) return 0.0;
            double start = parseTime(parts[0].trim());
            double end = parseTime(parts[1].trim());
            if (end <= start) end += 24.0; 

            double night1Start = Math.max(start, 0.0);
            double night1End = Math.min(end, 5.0);
            double night1 = Math.max(0, night1End - night1Start);

            double night2Start = Math.max(start, 22.0);
            double night2End = Math.min(end, 29.0);
            double night2 = Math.max(0, night2End - night2Start);

            return night1 + night2;
        } catch (Exception e) { return 0.0; }
    }

    public static double calculateRestTime(double shiftTime){
        if(shiftTime >= 8.0) return 1.0;
        else if(shiftTime >= 6.0) return 0.75;
        else return 0.0;
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