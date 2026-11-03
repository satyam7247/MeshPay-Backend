package com.demo.upimesh;

import com.demo.upimesh.dto.NotificationDtos;
import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.AccountRepository;
import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.model.AppUserRepository;
import com.demo.upimesh.model.PaymentInstruction;
import com.demo.upimesh.model.UserNotificationRepository;
import com.demo.upimesh.service.BrevoEmailService;
import com.demo.upimesh.service.ExpoPushService;
import com.demo.upimesh.service.NotificationService;
import com.demo.upimesh.service.SettlementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "upi.mesh.demo.seed-accounts=false")
class PaymentNotificationIntegrationTest {
    @Autowired private AppUserRepository users;
    @Autowired private AccountRepository accounts;
    @Autowired private UserNotificationRepository notifications;
    @Autowired private NotificationService notificationService;
    @Autowired private SettlementService settlement;
    @Autowired private TransactionTemplate transactions;
    @MockBean private BrevoEmailService email;
    @MockBean private ExpoPushService push;

    @Test
    void successfulSettlementCreatesOneOwnerScopedPersistentNotification() {
        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,10);
        String senderVpa="sender"+suffix+"@meshpay";
        String recipientVpa="recipient"+suffix+"@meshpay";
        String phone="9"+String.format("%09d",Math.abs((long)suffix.hashCode())%1_000_000_000L);
        AppUser sender=new AppUser("Satyam Sahu","sender"+suffix+"@example.test",phone,"test-hash",senderVpa);
        sender.updatePrivacySettings(new BigDecimal("500.00"),false,false);
        AppUser recipient=new AppUser("Mohit","recipient"+suffix+"@example.test","8"+phone.substring(1),"test-hash",recipientVpa);
        users.saveAndFlush(sender);
        users.saveAndFlush(recipient);
        String senderPinHash="f".repeat(64);
        accounts.saveAndFlush(new Account(senderVpa,"Satyam Sahu",new BigDecimal("200.00"),senderPinHash));
        accounts.saveAndFlush(new Account(recipientVpa,"Mohit",new BigDecimal("10.00")));

        String nonce=UUID.randomUUID().toString();
        var transaction=settlement.settle(new PaymentInstruction(senderVpa,recipientVpa,
                new BigDecimal("100.00"),senderPinHash,nonce,System.currentTimeMillis()),
                UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","") ,"test-bridge",1);
        assertEquals(com.demo.upimesh.model.Transaction.Status.SETTLED,transaction.getStatus());

        NotificationDtos.History recipientHistory=awaitHistory(recipient.getId());
        assertEquals(1,recipientHistory.items().size());
        NotificationDtos.Item item=recipientHistory.items().get(0);
        assertEquals("Satyam Sahu",item.senderName());
        assertEquals(0,new BigDecimal("100.00").compareTo(item.amount()));
        assertEquals(transaction.getId(),item.transactionId());
        assertEquals("SETTLED",item.paymentStatus());
        assertFalse(item.read());
        assertTrue(notificationService.history(sender.getId(),0,50).items().isEmpty(),"sender must not see recipient history");

        notificationService.markRead(recipient.getId(),item.id());
        assertEquals(0,notificationService.unreadCount(recipient.getId()).unreadCount());
        assertEquals(1,notificationService.history(recipient.getId(),0,50).items().size(),"reading keeps the notification in history");
        assertTrue(notificationService.history(recipient.getId(),0,50).items().get(0).read());

        transactions.executeWithoutResult(status -> notificationService.createForSettledTransaction(transaction.getId()));
        assertEquals(1,notificationService.history(recipient.getId(),0,50).items().size(),"replayed event must not duplicate the notification");
    }

    private NotificationDtos.History awaitHistory(UUID owner) {
        for(int i=0;i<40;i++) {
            NotificationDtos.History result=notificationService.history(owner,0,50);
            if(!result.items().isEmpty()) return result;
            try { Thread.sleep(25); } catch(InterruptedException e) { Thread.currentThread().interrupt(); fail("interrupted"); }
        }
        return notificationService.history(owner,0,50);
    }
}
