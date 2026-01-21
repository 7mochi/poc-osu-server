package pe.nanamochi.banchus.utils;

import java.net.InetAddress;
import org.springframework.web.client.RestTemplate;
import pe.nanamochi.banchus.entities.Geolocation;

public class IPApi {
  private IPApi() {}

  public static Geolocation fetchFromIP(InetAddress ip) {
    return new RestTemplate()
        .getForObject("http://ip-api.com/json/" + ip.getHostAddress(), Geolocation.class);
  }
}
