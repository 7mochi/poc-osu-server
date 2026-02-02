package pe.nanamochi.banchus.controllers;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/web")
public class SeasonalController {
  @Value("${banchus.seasonal-backgrounds.urls}")
  private List<String> seasonalBackgroundsUrls;

  @GetMapping(value = "/osu-getseasonal.php")
  public List<String> seasonalBackgrounds() {
    return seasonalBackgroundsUrls;
  }
}
