package pe.nanamochi.banchus.startup;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.services.infra.StorageService;

@Component
@RequiredArgsConstructor
public class DataDirectoryInitializer {
  private static final Logger logger = LoggerFactory.getLogger(DataDirectoryInitializer.class);

  private final StorageService storageService;

  @PostConstruct
  public void init() {
    storageService.initStorage();
  }
}
