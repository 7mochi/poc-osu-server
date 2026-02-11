package pe.nanamochi.banchus.config;

import java.nio.file.Path;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum StorageConfig {
  OSU(Path.of(".data/osu_beatmap_files"), ".osu"),
  REPLAY(Path.of(".data/replays_files"), ".osr"),
  SCREENSHOT(Path.of(".data/screenshots_files"), ".png"),
  AVATAR(Path.of(".data/avatars_files"), ".png");

  private final Path baseDir;
  private final String extension;

  public Path resolve(String name) {
    return baseDir.resolve(name + extension);
  }
}
