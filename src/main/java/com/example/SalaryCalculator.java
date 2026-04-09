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
    @GetMapping(value = "/", produces = "text/html; charset=UTF-8")
    public ResponseEntity<String> home(
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
            @AuthenticationPrincipal OAuth2User oauth2User,
            @RequestParam(value = "wage", defaultValue = "1260") int hourlyWage) {

        // ログインした人の名前を取得！
        String userName = oauth2User.getAttribute("name");

        double totalStandardHours = 0.0;
        double totalOvertimeHours = 0.0;
        double totalNightHours = 0.0;
        double totalRestHours = 0.0;

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
            if (items.isEmpty()) {
                html.append("<li>今月の予定は見つかりませんでした。</li>");
            } else {
                for (Event event : items) {
                    String shiftTitle = event.getSummary();
                    if (shiftTitle != null) {
                        double rawHours = calculateRawHours(shiftTitle);
                        if (rawHours > 0) {
                            double restHours = calculateRestTime(rawHours);
                            double actualHours = rawHours - restHours;
                            double nightHours = calculateNightHours(shiftTitle);
                            double standard = (actualHours > 8.0) ? 8.0 : actualHours;
                            double overtime = (actualHours > 8.0) ? actualHours - 8.0 : 0.0;

                            totalStandardHours += standard;
                            totalOvertimeHours += overtime;
                            totalNightHours += nightHours;
                            totalRestHours += restHours;

                            DateTime start = event.getStart().getDateTime();
                            if (start == null) start = event.getStart().getDate();
                            String dateStr = start.toString().substring(0, 10);

                            html.append(String.format("<li><strong>%s</strong> [%s] -> 基本: %.1fh | 残業: %.1fh | 深夜: %.1fh (休憩: %.1fh)</li>", 
                                    dateStr, shiftTitle, standard, overtime, nightHours, restHours));
                        }
                    }
                }
            }

            int standardPay = (int) (totalStandardHours * hourlyWage);
            int overtimePay = (int) (totalOvertimeHours * hourlyWage * 1.25);
            int nightPay = (int) (totalNightHours * hourlyWage * 0.25);
            int totalSalary = standardPay + overtimePay + nightPay;

            html.append("</ul>");
            html.append("<hr style='border: none; border-top: 1px solid #ddd; margin: 20px 0;'>");
            html.append("<h2 style='color: #2980b9;'>💰 今月の見込み給料</h2>");
            html.append(String.format("<h3 style='color: #e74c3c; font-size: 24px; text-align: center; background: #fff3f3; padding: 15px; border-radius: 8px;'>✨ %,d 円</h3>", totalSalary));

        } catch (Exception e) {
            html.append("<p style='color:red;'>❌ エラーが発生しました: " + e.getMessage() + "</p>");
        }

        html.append("</div></body></html>");
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