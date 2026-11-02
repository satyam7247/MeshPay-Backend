package com.demo.upimesh.controller;
import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.service.AccountDeletionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/account-deletion")
public class AccountDeletionController {
    private final AccountDeletionService deletion;
    public AccountDeletionController(AccountDeletionService deletion){this.deletion=deletion;}
    @PostMapping("/otp") public AccountDeletionService.OtpResponse sendOtp(@AuthenticationPrincipal AppUser user,@Valid @RequestBody OtpRequest body){return deletion.sendOtp(user,body.previousChallengeId());}
    @PostMapping("/verify") public AccountDeletionService.DeletionResponse verify(@AuthenticationPrincipal AppUser user,@Valid @RequestBody VerifyRequest body){return deletion.verifyAndRequest(user,body.challengeId(),body.code());}
    @GetMapping("/status") public AccountDeletionService.DeletionResponse status(@AuthenticationPrincipal AppUser user){return deletion.status(user);}
    public record OtpRequest(UUID previousChallengeId){}
    public record VerifyRequest(@NotNull UUID challengeId,@NotBlank @Pattern(regexp="^[0-9]{6}$") String code){}
}
