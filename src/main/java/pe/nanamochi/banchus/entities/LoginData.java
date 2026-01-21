package pe.nanamochi.banchus.entities;

import lombok.Data;

@Data
public class LoginData {
  private String username;
  private String passwordMd5;

  private String osuVersion;
  private String utcOffset;
  private String displayCity;
  private String clientHashes;
  private String pmPrivate;

  private String osuPathMd5;
  private String adaptersStr;
  private String adaptersMd5;
  private String uninstallMd5;
  private String diskSignatureMd5;

  public LoginData(String data) {
    String[] splitedData = data.split("\n", 3);
    this.username = splitedData[0];
    this.passwordMd5 = splitedData[1];

    String[] splitedClientInfo = splitedData[2].split("\\|", 5);
    this.osuVersion = splitedClientInfo[0];
    this.utcOffset = splitedClientInfo[1];
    this.displayCity = splitedClientInfo[2];
    this.pmPrivate = splitedClientInfo[4];

    String[] splitedClientHashes = splitedClientInfo[3].split(":", 5);
    this.osuPathMd5 = splitedClientHashes[0];
    this.adaptersStr = splitedClientHashes[1];
    this.adaptersMd5 = splitedClientHashes[2];
    this.uninstallMd5 = splitedClientHashes[3];
    this.diskSignatureMd5 = splitedClientHashes[4];
  }
}
