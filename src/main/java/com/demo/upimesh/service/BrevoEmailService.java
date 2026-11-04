package com.demo.upimesh.service;

import java.util.Map;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BrevoEmailService {
    private static final Logger log=LoggerFactory.getLogger(BrevoEmailService.class);
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json=new ObjectMapper();
    @Value("${app.email.brevo.api-key:}") private String apiKey;
    @Value("${app.email.brevo.sender-email:}") private String senderEmail;
    @Value("${app.email.brevo.sender-name:}") private String senderName;

    public boolean isConfigured() {
        return apiKey!=null&&!apiKey.isBlank()&&senderEmail!=null&&!senderEmail.isBlank()
                && senderName!=null&&!senderName.isBlank();
    }

    public void sendPasswordOtp(String email, String otp) {
        if (!isConfigured()) {
            throw new EmailDeliveryUnavailableException();
        }
        String html="<p>Your Offline UPI Mesh verification code is <strong>"+otp+"</strong>.</p>"
                +"<p>This code expires in 5 minutes and can be used once. If you did not request a password change, "
                +"secure your account and ignore this email.</p>";
        Map<String,Object> message=Map.of(
                "sender",Map.of("email",senderEmail,"name",senderName),
                "to",java.util.List.of(Map.of("email",email)),
                "subject","Password Reset Verification",
                "htmlContent",html);
        try {
            String payload=json.writeValueAsString(message);
            HttpRequest request=HttpRequest.newBuilder(URI.create("https://api.brevo.com/v3/smtp/email"))
                    .timeout(Duration.ofSeconds(10))
                    .header("api-key",apiKey)
                    .header("Accept","application/json")
                    .header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload,StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Void> response=http.send(request,HttpResponse.BodyHandlers.discarding());
            if(response.statusCode()<200||response.statusCode()>=300) {
                log.warn("Brevo rejected password email request (HTTP {}).",response.statusCode());
                throw new EmailDeliveryUnavailableException();
            }
            log.info("Brevo accepted password email request (HTTP {}).",response.statusCode());
        } catch (IOException error) {
            log.warn("Brevo password email transport failed ({}).",error.getClass().getSimpleName());
            throw new EmailDeliveryUnavailableException();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            log.warn("Brevo password email request was interrupted.");
            throw new EmailDeliveryUnavailableException();
        }
    }

    public void sendAppLockResetOtp(String email, String otp) {
        if (!isConfigured()) throw new EmailDeliveryUnavailableException();
        String html="<p>Your MeshPay screen lock reset code is <strong>"+otp+"</strong>.</p>"
                +"<p>This code expires in 5 minutes and can be used once. If you did not request this, ignore this email.</p>";
        Map<String,Object> message=Map.of(
                "sender",Map.of("email",senderEmail,"name",senderName),
                "to",java.util.List.of(Map.of("email",email)),
                "subject","MeshPay screen lock reset verification",
                "htmlContent",html);
        try {
            String payload=json.writeValueAsString(message);
            HttpRequest request=HttpRequest.newBuilder(URI.create("https://api.brevo.com/v3/smtp/email"))
                    .timeout(Duration.ofSeconds(10)).header("api-key",apiKey)
                    .header("Accept","application/json").header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload,StandardCharsets.UTF_8)).build();
            HttpResponse<Void> response=http.send(request,HttpResponse.BodyHandlers.discarding());
            if(response.statusCode()<200||response.statusCode()>=300) {
                log.warn("Brevo rejected screen lock reset email request (HTTP {}).",response.statusCode());
                throw new EmailDeliveryUnavailableException();
            }
            log.info("Brevo accepted screen lock reset email request (HTTP {}).",response.statusCode());
        } catch (IOException error) {
            log.warn("Brevo screen lock reset transport failed ({}).",error.getClass().getSimpleName());
            throw new EmailDeliveryUnavailableException();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            log.warn("Brevo screen lock reset email request was interrupted.");
            throw new EmailDeliveryUnavailableException();
        }
    }

    public void sendAppLockChanged(String email, boolean reset) {
        if (!isConfigured()) throw new EmailDeliveryUnavailableException();
        Map<String,Object> message=Map.of("sender",Map.of("email",senderEmail,"name",senderName),
                "to",java.util.List.of(Map.of("email",email)),
                "subject", reset ? "MeshPay App Lock reset" : "MeshPay App Lock enabled",
                "htmlContent", reset
                        ? "<p>Your MeshPay App Lock password was reset.</p><p>If you did not do this, contact support.</p>"
                        : "<p>App Lock was enabled for your MeshPay account.</p><p>If you did not do this, contact support.</p>");
        sendMessage(message,"app lock notification");
    }

    public void sendBankLinkOtp(String email, String otp) {
        if (!isConfigured()) throw new EmailDeliveryUnavailableException();
        String html = "<p>Your Link bank account verification code is <strong>"+otp+"</strong>.</p>"
                + "<p>This code expires in 65 seconds. If you did not request this, ignore this email.</p>";
        Map<String,Object> message=Map.of(
                "sender",Map.of("email",senderEmail,"name",senderName),
                "to",java.util.List.of(Map.of("email",email)),
                "subject","Link bank account verification code",
                "htmlContent",html);
        try {
            String payload=json.writeValueAsString(message);
            HttpRequest request=HttpRequest.newBuilder(URI.create("https://api.brevo.com/v3/smtp/email"))
                    .timeout(Duration.ofSeconds(10)).header("api-key",apiKey)
                    .header("Accept","application/json").header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload,StandardCharsets.UTF_8)).build();
            HttpResponse<Void> response=http.send(request,HttpResponse.BodyHandlers.discarding());
            if(response.statusCode()<200||response.statusCode()>=300) {
                log.warn("Brevo rejected bank link email request (HTTP {}).",response.statusCode());
                throw new EmailDeliveryUnavailableException();
            }
            log.info("Brevo accepted bank link email request (HTTP {}).",response.statusCode());
        } catch (IOException error) {
            log.warn("Brevo bank link email transport failed ({}).",error.getClass().getSimpleName());
            throw new EmailDeliveryUnavailableException();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            log.warn("Brevo bank link email request was interrupted.");
            throw new EmailDeliveryUnavailableException();
        }
    }

    public void sendAccountDeletionOtp(String email, String otp) {
        if (!isConfigured()) throw new EmailDeliveryUnavailableException();
        String html = "<p>Your account deletion verification code is <strong>"+otp+"</strong>.</p>"
                + "<p>This code expires in 60 seconds. If you did not request this, ignore this email.</p>";
        Map<String,Object> message=Map.of(
                "sender",Map.of("email",senderEmail,"name",senderName),
                "to",java.util.List.of(Map.of("email",email)),
                "subject","Account deletion verification code",
                "htmlContent",html);
        try {
            String payload=json.writeValueAsString(message);
            HttpRequest request=HttpRequest.newBuilder(URI.create("https://api.brevo.com/v3/smtp/email"))
                    .timeout(Duration.ofSeconds(10)).header("api-key",apiKey)
                    .header("Accept","application/json").header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload,StandardCharsets.UTF_8)).build();
            HttpResponse<Void> response=http.send(request,HttpResponse.BodyHandlers.discarding());
            if(response.statusCode()<200||response.statusCode()>=300) {
                log.warn("Brevo rejected account deletion email request (HTTP {}).",response.statusCode());
                throw new EmailDeliveryUnavailableException();
            }
            log.info("Brevo accepted account deletion email request (HTTP {}).",response.statusCode());
        } catch (IOException error) {
            log.warn("Brevo account deletion email transport failed ({}).",error.getClass().getSimpleName());
            throw new EmailDeliveryUnavailableException();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            log.warn("Brevo account deletion email request was interrupted.");
            throw new EmailDeliveryUnavailableException();
        }
    }


    public void sendPaymentReceived(String email, String payerName, java.math.BigDecimal amount,
                                    Long transactionId, String dateTime, String status) {
        if (!isConfigured()) throw new EmailDeliveryUnavailableException();
        Map<String,Object> message=paymentReceivedMessage(email,payerName,amount,transactionId,dateTime,status);
        try {
            String payload=json.writeValueAsString(message);
            HttpRequest request=HttpRequest.newBuilder(URI.create("https://api.brevo.com/v3/smtp/email"))
                    .timeout(Duration.ofSeconds(10)).header("api-key",apiKey)
                    .header("Accept","application/json").header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload,StandardCharsets.UTF_8)).build();
            HttpResponse<Void> response=http.send(request,HttpResponse.BodyHandlers.discarding());
            if(response.statusCode()<200||response.statusCode()>=300) {
                log.warn("Brevo rejected payment notification email (HTTP {}).",response.statusCode());
                throw new EmailDeliveryUnavailableException();
            }
            log.info("Brevo accepted payment notification email (HTTP {}).",response.statusCode());
        } catch(IOException error) {
            log.warn("Brevo payment notification email transport failed ({}).",error.getClass().getSimpleName());
            throw new EmailDeliveryUnavailableException();
        } catch(InterruptedException error) {
            Thread.currentThread().interrupt();
            log.warn("Brevo payment notification email request was interrupted.");
            throw new EmailDeliveryUnavailableException();
        }
    }


    public void sendPaymentReceivedGeneric(String email, String dateTime) {
        if (!isConfigured()) throw new EmailDeliveryUnavailableException();
        Map<String,Object> message=Map.of("sender",Map.of("email",senderEmail,"name",senderName),
                "to",java.util.List.of(Map.of("email",email)),"subject","Payment received",
                "htmlContent","<p>You received a payment. Open MeshPay to view the details.</p><p>Date and time: "+escapeHtml(dateTime)+"</p>");
        sendMessage(message,"payment notification");
    }

    private void sendMessage(Map<String,Object> message, String label) {
        try {
            String payload=json.writeValueAsString(message);
            HttpRequest request=HttpRequest.newBuilder(URI.create("https://api.brevo.com/v3/smtp/email"))
                    .timeout(Duration.ofSeconds(10)).header("api-key",apiKey)
                    .header("Accept","application/json").header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload,StandardCharsets.UTF_8)).build();
            HttpResponse<Void> response=http.send(request,HttpResponse.BodyHandlers.discarding());
            if(response.statusCode()<200||response.statusCode()>=300) throw new EmailDeliveryUnavailableException();
        } catch(IOException error) { throw new EmailDeliveryUnavailableException(); }
        catch(InterruptedException error) { Thread.currentThread().interrupt(); throw new EmailDeliveryUnavailableException(); }
    }

    Map<String,Object> paymentReceivedMessage(String email,String payerName,java.math.BigDecimal amount,
                                              Long transactionId,String dateTime,String status) {
        String safeName=escapeHtml(limitCodePoints(payerName,80));
        String html="<p><strong>"+safeName+"</strong> sent you <strong>"+String.valueOf((char)0x20B9)+" "+amount.toPlainString()+"</strong></p>"
                +"<p>Transaction ID: "+transactionId+"<br>Date and time: "+escapeHtml(dateTime)
                +"<br>Payment status: "+escapeHtml(status)+"</p>";
        return Map.of("sender",Map.of("email",senderEmail,"name",this.senderName),
                "to",java.util.List.of(Map.of("email",email)),"subject","Payment received","htmlContent",html);
    }

    private static String limitCodePoints(String value,int limit) {
        if(value==null)return "";
        int count=value.codePointCount(0,value.length());
        return count<=limit?value:value.substring(0,value.offsetByCodePoints(0,limit));
    }

    private static String escapeHtml(String value) {
        if(value==null) return "";
        return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                .replace(String.valueOf((char)34),"&quot;").replace("'","&#39;");
    }

    public static class EmailDeliveryUnavailableException extends RuntimeException {
        public EmailDeliveryUnavailableException(){super("Password email delivery is not available.");}
    }
}
