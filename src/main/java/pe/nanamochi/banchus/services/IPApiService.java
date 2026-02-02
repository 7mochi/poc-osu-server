package pe.nanamochi.banchus.services;

import java.net.InetAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import pe.nanamochi.banchus.entities.Geolocation;

@Service
@RequiredArgsConstructor
public class IPApiService {
  private final RestTemplate restTemplate;

  public Geolocation fetchFromIP(InetAddress ip) {
    return restTemplate.getForObject(
        "http://ip-api.com/json/" + ip.getHostAddress(), Geolocation.class);
  }
}
