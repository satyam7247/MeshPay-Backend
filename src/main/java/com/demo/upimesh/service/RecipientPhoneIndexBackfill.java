package com.demo.upimesh.service;

import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.model.AppUserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class RecipientPhoneIndexBackfill implements ApplicationRunner {
    private final AppUserRepository users;
    public RecipientPhoneIndexBackfill(AppUserRepository users) { this.users = users; }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<AppUser> legacyUsers = users.findAllByPhoneNormalizedIsNull();
        if (legacyUsers.isEmpty()) return;
        legacyUsers.forEach(user -> user.setPhoneNormalized(MobileNumberNormalizer.normalizeStored(user.getPhone())));
        users.saveAll(legacyUsers);
    }
}
