package com.demo.upimesh.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExpoPushService {
    private static final Logger log=LoggerFactory.getLogger(ExpoPushService.class);
    private static final URI PUSH_URI=URI.create("https://exp.host/--/api/v2/push/send");
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json=new ObjectMapper();

    public void send(NotificationService.DeliveryDetails details) {
        if(details.pushTargets()==null||details.pushTargets().isEmpty()) return;
        List<Map<String,Object>> messages=new ArrayList<>();
        for(NotificationService.PushTarget target:details.pushTargets()) {
            Map<String,Object> message=new LinkedHashMap<>();
            message.put("to",target.token());
            message.put("title","Payment received");
            message.put("body",details.hideTransactionDetails() ? "You received a payment. Open MeshPay to view it."
                    : details.item().senderName()+" sent you INR "+details.item().amount().toPlainString()+".");
            message.put("sound",target.soundEnabled()?"default":null);
            message.put("badge",(int)Math.min(Integer.MAX_VALUE,details.unreadCount()));
            message.put("priority","high");
            message.put("channelId","payments");
            message.put("collapseId","payment-"+details.item().id());
            message.put("data",details.hideTransactionDetails()
                    ? Map.of("notificationId",details.item().id().toString())
                    : Map.of("notificationId",details.item().id().toString(),"transactionId",details.item().transactionId()));
            messages.add(message);
        }
        for(int start=0;start<messages.size();start+=100) sendBatch(messages.subList(start,Math.min(start+100,messages.size())));
    }

    private void sendBatch(List<Map<String,Object>> messages) {
        try {
            String payload=json.writeValueAsString(messages);
            HttpRequest request=HttpRequest.newBuilder(PUSH_URI).timeout(Duration.ofSeconds(10))
                    .header("Accept","application/json")
                    .header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload,StandardCharsets.UTF_8)).build();
            HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(response.statusCode()<200||response.statusCode()>=300) {
                log.warn("Expo push delivery request failed (HTTP {}).",response.statusCode());
                return;
            }
            JsonNode tickets=json.readTree(response.body()).path("data");
            if(tickets.isArray()) for(JsonNode ticket:tickets) {
                if("error".equals(ticket.path("status").asText())) {
                    log.warn("Expo push ticket rejected notification ({}).",ticket.path("details").path("error").asText("unknown"));
                }
            }
        } catch(Exception error) {
            if(error instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("Expo push notification request failed ({}).",error.getClass().getSimpleName());
        }
    }
}
