package pe.nanamochi.poc_osu_server.utils;

import org.springframework.web.client.RestTemplate;
import pe.nanamochi.poc_osu_server.entities.Geolocation;

import java.net.InetAddress;

public class IPApi {
    private IPApi() {
    }

    public static Geolocation fetchFromIP(InetAddress ip) {
        return new RestTemplate().getForObject("http://ip-api.com/json/" + ip.getHostAddress(), Geolocation.class);
    }
}
