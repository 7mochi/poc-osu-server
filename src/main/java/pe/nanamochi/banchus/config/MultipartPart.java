package pe.nanamochi.banchus.config;

/**
 * Represents a single part in a multipart form data request.
 * Can contain either text or binary data.
 */
public class MultipartPart {
  private final String fieldName;
  private final byte[] bytes;
  private final boolean isText;

  public MultipartPart(String fieldName, byte[] bytes, boolean isText) {
    this.fieldName = fieldName;
    this.bytes = bytes;
    this.isText = isText;
  }

  public String getFieldName() {
    return fieldName;
  }

  public byte[] getBytes() {
    return bytes;
  }

  public String getText() {
    // For text fields, decode as UTF-8
    return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
  }

  public boolean isText() {
    return isText;
  }

  public int getLength() {
    return bytes != null ? bytes.length : 0;
  }
}
