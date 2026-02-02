package pe.nanamochi.banchus.controllers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.nanamochi.banchus.entities.*;
import pe.nanamochi.banchus.packets.server.*;
import pe.nanamochi.banchus.services.*;

@RestController
@RequestMapping("/")
@RequiredArgsConstructor
public class LoginController {
  private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

  private final LoginService loginService;
  private final BanchoService banchoService;

  @PostMapping(value = "/", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  public ResponseEntity<Resource> banchoHandler(
      @RequestHeader HttpHeaders headers, @RequestBody byte[] data) throws IOException {
    ResponseEntity<Resource> response;

    if (!headers.containsHeader("osu-token")) {
      logger.debug("Handling login request");
      response = handleLogin(headers, new String(data, StandardCharsets.UTF_8));
    } else {
      logger.debug("Handling bancho request");
      response = handleBanchoRequest(headers, data);
    }

    return response;
  }

  private ResponseEntity<Resource> handleLogin(HttpHeaders headers, String data)
      throws IOException {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    LoginData loginData = new LoginData(data);
    HttpHeaders responseHeaders = new HttpHeaders();

    String choToken = loginService.handleLogin(loginData, headers, stream);

    responseHeaders.add("cho-token", choToken);

    return ResponseEntity.ok()
        .headers(responseHeaders)
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(new ByteArrayResource(stream.toByteArray()));
  }

  private ResponseEntity<Resource> handleBanchoRequest(HttpHeaders headers, byte[] data)
      throws IOException {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    HttpHeaders responseHeaders = new HttpHeaders();

    byte[] responseData = banchoService.handleBanchoRequest(headers, data, stream);

    String choToken = headers.getFirst("cho-token");
    if (choToken != null) {
      responseHeaders.add("cho-token", choToken);
    }

    return ResponseEntity.ok()
        .headers(responseHeaders)
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(responseData.length > 0 ? new ByteArrayResource(responseData) : null);
  }
}
