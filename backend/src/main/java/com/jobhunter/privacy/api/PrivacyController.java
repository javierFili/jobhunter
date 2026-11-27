package com.jobhunter.privacy.api;

import com.jobhunter.privacy.application.PrivacyService;
import com.jobhunter.shared.security.CurrentUser;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/me")
public class PrivacyController {

    private final PrivacyService privacy;

    public PrivacyController(PrivacyService privacy) {
        this.privacy = privacy;
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        byte[] zip = privacy.export(CurrentUser.id());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("jobhunter-export-" + LocalDate.now() + ".zip").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(zip);
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@RequestBody Map<String, String> body) {
        privacy.deleteAccount(CurrentUser.id(), body.get("password"));
        privacy.auditDeleted();
        ResponseCookie clear = ResponseCookie.from("jh_refresh", "").httpOnly(true).sameSite("Strict").path("/api/v1/auth").maxAge(0).build();
        return ResponseEntity.status(HttpStatus.NO_CONTENT).header(HttpHeaders.SET_COOKIE, clear.toString()).build();
    }
}
