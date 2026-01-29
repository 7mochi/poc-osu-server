package pe.nanamochi.banchus.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilidad para parsear multipart/form-data SIN disparar el parser de Tomcat.
 * Lee el stream RAW de la request y extrae los parámetros manualmente.
 */
public class MultipartFormDataParser {

  private static final Logger logger = LoggerFactory.getLogger(MultipartFormDataParser.class);

  /**
   * Parsea parámetros Y datos binarios en una sola pasada.
   * Retorna los parámetros de texto en un mapa aparte de los datos binarios.
   */
  public static class ParsedFormData {
    public Map<String, String> params = new HashMap<>();
    public Map<String, byte[]> binaryData = new HashMap<>();
  }

  /**
   * Parsea multipart/form-data en una sola pasada, extrayendo parámetros texto y datos binarios.
   */
  public static ParsedFormData parseFormDataFull(InputStream inputStream, String contentType)
      throws IOException {
    ParsedFormData result = new ParsedFormData();

    if (!contentType.startsWith("multipart/form-data")) {
      return result;
    }

    // Extraer el boundary del content-type
    String boundary = extractBoundary(contentType);
    if (boundary == null) {
      logger.warn("No boundary found in Content-Type header");
      return result;
    }

    logger.info("Boundary encontrado: {}", boundary);

    byte[] buffer = new byte[8192];
    ByteArrayBuilder currentPart = new ByteArrayBuilder();
    int bytesRead;

    while ((bytesRead = inputStream.read(buffer)) != -1) {
      currentPart.append(buffer, 0, bytesRead);
    }

    byte[] body = currentPart.toByteArray();
    byte[] boundaryBytes = ("--" + boundary).getBytes(StandardCharsets.UTF_8);
    
    int pos = 0;
    
    while ((pos = indexOf(body, boundaryBytes, pos)) != -1) {
      pos += boundaryBytes.length;
      
      // Saltar CRLF después del boundary
      if (pos + 1 < body.length && body[pos] == '\r' && body[pos + 1] == '\n') {
        pos += 2;
      }
      
      // Buscar el siguiente boundary
      int nextBoundaryPos = indexOf(body, boundaryBytes, pos);
      if (nextBoundaryPos == -1) {
        break;
      }
      
      // Extraer esta parte
      byte[] partData = new byte[nextBoundaryPos - pos];
      System.arraycopy(body, pos, partData, 0, partData.length);
      
      String partStr = new String(partData, StandardCharsets.UTF_8);
      
      // Buscar el separador entre headers y contenido
      int headerEndIdx = partStr.indexOf("\r\n\r\n");
      if (headerEndIdx == -1) {
        pos = nextBoundaryPos;
        continue;
      }
      
      String headers = partStr.substring(0, headerEndIdx);
      String name = extractParameterName(headers);
      
      if (name == null) {
        pos = nextBoundaryPos;
        continue;
      }
      
      // Extraer el contenido (después de los headers)
      int contentStart = pos + headerEndIdx + 4;
      int contentEnd = nextBoundaryPos;
      
      // Limpiar trailing CRLF
      while (contentEnd > contentStart && 
             (body[contentEnd - 1] == '\r' || body[contentEnd - 1] == '\n')) {
        contentEnd--;
      }
      
      byte[] content = new byte[contentEnd - contentStart];
      System.arraycopy(body, contentStart, content, 0, content.length);
      
      // Checar si es un archivo (tiene filename) o parámetro de texto
      if (headers.contains("filename=")) {
        result.binaryData.put(name, content);
        logger.info("Dato binario parseado: {} = {} bytes", name, content.length);
      } else {
        String textContent = new String(content, StandardCharsets.UTF_8).trim();
        result.params.put(name, textContent);
        logger.info("Parámetro parseado: {} = {} bytes", name, textContent.length());
      }
      
      pos = nextBoundaryPos;
    }

    return result;
  }

  private static String extractBoundary(String contentType) {
    String[] parts = contentType.split(";");
    for (String part : parts) {
      part = part.trim();
      if (part.startsWith("boundary=")) {
        String boundary = part.substring("boundary=".length());
        // Remover comillas si las hay
        if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
          boundary = boundary.substring(1, boundary.length() - 1);
        }
        return boundary;
      }
    }
    return null;
  }

  private static String extractParameterName(String headers) {
    // Buscar Content-Disposition header
    for (String line : headers.split("\r\n")) {
      if (line.toLowerCase().startsWith("content-disposition:")) {
        // Extraer name="..."
        int nameIdx = line.indexOf("name=");
        if (nameIdx != -1) {
          String nameSection = line.substring(nameIdx + 5);
          int quoteEnd = nameSection.indexOf('"', 1);
          if (nameSection.startsWith("\"") && quoteEnd != -1) {
            return nameSection.substring(1, quoteEnd);
          }
        }
      }
    }
    return null;
  }

  /**
   * Clase auxiliar para construir un array de bytes dinámicamente
   */
  private static class ByteArrayBuilder {
    private byte[] data = new byte[1024];
    private int size = 0;

    void append(byte[] bytes, int offset, int len) {
      if (size + len > data.length) {
        byte[] newData = new byte[Math.max(data.length * 2, size + len)];
        System.arraycopy(data, 0, newData, 0, size);
        data = newData;
      }
      System.arraycopy(bytes, offset, data, size, len);
      size += len;
    }

    byte[] toByteArray() {
      byte[] result = new byte[size];
      System.arraycopy(data, 0, result, 0, size);
      return result;
    }
  }

  /**
   * Busca un array de bytes dentro de otro array de bytes.
   */
  private static int indexOf(byte[] array, byte[] pattern, int startPos) {
    for (int i = startPos; i <= array.length - pattern.length; i++) {
      boolean match = true;
      for (int j = 0; j < pattern.length; j++) {
        if (array[i + j] != pattern[j]) {
          match = false;
          break;
        }
      }
      if (match) {
        return i;
      }
    }
    return -1;
  }
}
