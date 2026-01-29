package pe.nanamochi.banchus.controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class MenuController {

  @Value("${banchus.menu-icon.image-url}")
  private String menuIconImage;

  @Value("${banchus.menu-icon.redirect-url}")
  private String menuIconUrl;

  @GetMapping(value = "/menu-content.json", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> getMenuContent() {
    Map<String, Object> image = new HashMap<>();
    image.put("image", menuIconImage);
    image.put("url", menuIconUrl);
    image.put("IsCurrent", true);
    image.put("begins", null);
    image.put("expires", null);

    Map<String, Object> response = new HashMap<>();
    response.put("images", List.of(image));

    return response;
  }
}
