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
            @RequestParam(value = "wage", defaultValue = "1260") int hourlyWage) { // ★ここを追加！

        // ログインした人の名前を取得
        String userName = oauth2User.getAttribute("name");

        // hourlyWage は上の @RequestParam で受け取った値が自動的に入ります
        double totalStandardHours = 0.0;
        // ... (以下、計算ロジックはそのまま) ...

        // 表示部分を少しリッチに！
        html.append("<h1 style='color: #2c3e50;'>📅 " + userName + "さんの給料計算</h1>");
        html.append("<p style='background: #eef2f3; padding: 10px; border-radius: 5px;'>💰 現在の設定時給: <strong>" + hourlyWage + "円</strong></p>");
        
        // ... (中略) ...

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