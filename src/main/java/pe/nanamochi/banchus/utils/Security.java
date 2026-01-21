package pe.nanamochi.banchus.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Security {
  private void DataUtils() {}

  public static String getMd5(final String plaintext) throws NoSuchAlgorithmException {
    MessageDigest m = MessageDigest.getInstance("MD5");
    m.update(plaintext.getBytes(), 0, plaintext.length());
    StringBuilder sb = new StringBuilder();
    for (byte b : m.digest()) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
