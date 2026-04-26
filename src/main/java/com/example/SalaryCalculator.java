package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

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

@Controller
public class SalaryCalculator {

    private static final String APPLICATION_NAME = "My Salary Web App";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    public static void main(String[] args) {
        SpringApplication.run(SalaryCalculator.class, args);
    }

    @GetMapping("/")

    public String home( 
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
            @AuthenticationPrincipal OAuth2User oauth2User,
            @RequestParam(value = "wage", defaultValue = "1260") int hourlyWage,
            Model model) throws Exception {

        String userName = oauth2User.getAttribute("name");
        
        // 画面に渡す基本データ
        model.addAttribute("userName", userName);
        model.addAttribute("hourlyWage", hourlyWage);

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

            if (items.isEmpty()) {
                model.addAttribute("hasShifts", false);
            } else {
                model.addAttribute("hasShifts", true);
                java.util.Map<String, Double> dailyHours = new java.util.HashMap<>();

                for (Event event : items) {
                    
                    String summary = event.getSummary();
                    double shiftHours = 0.0;
                    boolean hasTime = false;
                    String dateKey = getCalendar(event);

                    if (dateKey.isEmpty()) continue;

                    if (summary != null) {
                        shiftHours = calculateHours(summary);
                        if (shiftHours > 0) {
                            hasTime = true;
                        }
                    }

                    if (hasTime && shiftHours > 0) {
                        dailyHours.put(dateKey, dailyHours.getOrDefault(dateKey, 0.0) + shiftHours);
                    }
                }

                for (double dailyShift : dailyHours.values()) {
                    double breakTime = calculateBreakTime(dailyShift);
                    double actualWork = dailyShift - breakTime;
                    double overtime = 0.0;

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

                // 計算結果を画面に渡すためにModelにセット
                model.addAttribute("totalWorkHours", String.format("%.2f", totalWorkHours));
                model.addAttribute("totalBreakHours", String.format("%.2f", totalBreakHours));
                model.addAttribute("totalOvertimeHours", String.format("%.2f", totalOvertimeHours));
                model.addAttribute("totalSalary", totalSalary);
            }

        } catch (Exception e) {
            model.addAttribute("errorMessage", e.getMessage());
            e.printStackTrace();
        }
        
        // dashboard.html を表示
        return "dashboard"; 
    }


    public static String getCalendar(Event event) {
        if (event.getStart().getDateTime() != null) {
            return new java.text.SimpleDateFormat("yyyy-MM-dd")
                    .format(new java.util.Date(event.getStart().getDateTime().getValue()));
        } else if (event.getStart().getDate() != null) {
            return event.getStart().getDate().toString().substring(0, 10);
        } else{
            return "";
        }
    }

    public static double calculateHours(String summary) {
        // 文字が含まれていたら除外
        String regex = "^\\s*([0-9.]+)\\s*[-〜~]\\s*([0-9.]+)\\s*$";
        
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(summary);
        
        if (m.matches()) {
            try {
                double s = Double.parseDouble(m.group(1));
                double e = Double.parseDouble(m.group(2));
                
                if (e <= s) {
                    e += 24.0; 
                }
                
                return e - s;
                
            } catch (NumberFormatException ex) {
                return 0.0;
            }
        }
        return 0.0;
    }

    public static double calculateBreakTime(double shiftTime){
        if(shiftTime >= 8.0) {
            return 1.0;
        }else if(shiftTime >= 6.0){
            return 0.75;
        }else {
            return 0.0;
        }
    }
}