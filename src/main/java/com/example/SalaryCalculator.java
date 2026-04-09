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

        StringBuilder html = new StringBuilder();
        html.append("<html><head><meta charset='UTF-8'><title>給料計算システム</title></head>");
        html.append("<body style='font-family: sans-serif; padding: 30px; background-color: #f4f7f6;'>");
        html.append("<div style='max-width: 650px; margin: 0 auto; background: white; padding: 20px; border-radius: 10px; box-shadow: 0 4px 8px rgba(0,0,0,0.1);'>");
        
        // ★画面にログインした人の名前を表示します
        html.append("<h1 style='color: #2c3e50;'>📅 " + userName + "さんの給料計算</h1>");
        html.append("<ul style='line-height: 1.8; font-size: 14px;'>");

        try {
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            
            // ★ローカルの鍵ではなく、Googleから受け取った「ログイン中の人の一時的な鍵」を使います
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
            
            // ★合計時間の入れ物（ダッシュボード用）
            double totalShiftHours = 0.0; // シフトの総時間（拘束時間）
            double totalBreakHours = 0.0; // 総休憩時間
            double totalWorkHours = 0.0;  // 総実働時間
            double totalOvertimeHours = 0.0; // 総残業時間
            int totalSalary = 0; // 総給与

            StringBuilder html = new StringBuilder();
            html.append("<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'>");
            // ちょっとおしゃれにするCSS
            html.append("<style>body{font-family:sans-serif; padding:15px; background:#f4f7f6; color:#333;} .card{background:#fff; padding:15px; border-radius:10px; box-shadow:0 2px 5px rgba(0,0,0,0.1); margin-bottom:15px;} input[type='number']{padding:8px; border:1px solid #ccc; border-radius:5px; width:100px;} button{padding:8px 15px; background:#3498db; color:#fff; border:none; border-radius:5px; cursor:pointer;}</style>");
            html.append("</head><body>");

            html.append("<h2>📱 " + userName + "さんの給与ダッシュボード</h2>");

            // ★① 画面から時給を変更できるフォーム
            html.append("<div class='card'>");
            html.append("<form method='get' action='/'>");
            html.append("<label>💰 時給: </label>");
            html.append("<input type='number' name='wage' value='" + hourlyWage + "'> 円 ");
            html.append("<button type='submit'>再計算</button>");
            html.append("</form>");
            html.append("</div>");

            // カレンダーの予定を1つずつループして計算
            if (items.isEmpty()) {
                html.append("<p>今月のシフトは見つかりませんでした。</p>");
            } else {
                for (Event event : items) {
                    DateTime start = event.getStart().getDateTime();
                    DateTime end = event.getEnd().getDateTime();
                    
                    if (start != null && end != null) {
                        // ミリ秒を「時間（hour）」に変換
                        long durationMs = end.getValue() - start.getValue();
                        double shiftHours = durationMs / (1000.0 * 60.0 * 60.0);
                        
                        double breakTime = 0.0;
                        double actualWork = 0.0;
                        double overtime = 0.0;

                        // ★休憩時間の自動判定ロジック
                        if (shiftHours >= 8.0) {
                            breakTime = 1.0; // 8時間以上拘束なら1時間休憩
                        } else if (shiftHours > 6.0) {
                            breakTime = 0.75; // 6時間超えなら45分(0.75時間)休憩
                        }

                        actualWork = shiftHours - breakTime;

                        // ★残業時間の自動判定（1日8時間を超えた分）
                        if (actualWork > 8.0) {
                            overtime = actualWork - 8.0;
                        }

                        // 合計に足していく
                        totalShiftHours += shiftHours;
                        totalBreakHours += breakTime;
                        totalWorkHours += actualWork;
                        totalOvertimeHours += overtime;

                        // 今回の給料計算（※残業は時給1.25倍で計算するおまけ付き！）
                        double normalWork = actualWork - overtime;
                        totalSalary += (int) ((normalWork * hourlyWage) + (overtime * hourlyWage * 1.25));
                    }
                }

                // ★② 労働時間の合計を表示するダッシュボード
                html.append("<div class='card' style='background:#e8f4f8;'>");
                html.append("<h3 style='margin-top:0;'>📊 今月の集計</h3>");
                html.append("<p>🕒 実働時間: <strong>" + String.format("%.2f", totalWorkHours) + " 時間</strong></p>");
                html.append("<p>☕ 休憩時間: " + String.format("%.2f", totalBreakHours) + " 時間</p>");
                html.append("<p>🔥 残業時間: <span style='color:red;'>" + String.format("%.2f", totalOvertimeHours) + " 時間</span></p>");
                html.append("<hr>");
                html.append("<h2 style='color:#e74c3c;'>💴 予想給与: " + String.format("%,d", totalSalary) + " 円</h2>");
                html.append("<p style='font-size:0.8em; color:#7f8c8d;'>※残業代（時給1.25倍）を含みます</p>");
                html.append("</div>");
            }

            html.append("</body></html>");
            
        }catch (Exception e) {
            // もしGoogleカレンダーの取得などでエラーが起きたら、画面にエラー理由を出す
            html.append("<div class='card' style='background:#ffebee; color:#c62828;'>");
            html.append("<h3>❌ エラーが発生しました</h3>");
            html.append("<p>" + e.getMessage() + "</p>");
            html.append("</div></body></html>");
            e.printStackTrace(); // Renderのログにもエラー詳細を出す
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