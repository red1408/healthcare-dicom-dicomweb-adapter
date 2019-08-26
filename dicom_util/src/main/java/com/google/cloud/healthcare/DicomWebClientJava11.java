package com.google.cloud.healthcare;


import com.github.danieln.multipart.MultipartInput;
import com.google.api.client.http.HttpStatusCodes;
import com.google.auth.oauth2.OAuth2Credentials;
import com.google.common.base.CharMatcher;
import com.google.common.net.HttpHeaders;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.json.JSONArray;

public class DicomWebClientJava11 implements IDicomWebClient {

  private final String serviceUrlPrefix;
  private final OAuth2Credentials credentials;

  public DicomWebClientJava11(
      OAuth2Credentials credentials,
      String serviceUrlPrefix) {
    this.credentials = credentials;
    this.serviceUrlPrefix = trim(serviceUrlPrefix);
  }

  public static BodyPublisher ofMimeMultipartData(Map<Object, Object> data,
      String boundary) throws IOException {
    var byteArrays = new ArrayList<byte[]>();
    byte[] separator = ("--" + boundary + "\r\nContent-Disposition: form-data; name=")
        .getBytes(StandardCharsets.UTF_8);
    for (Map.Entry<Object, Object> entry : data.entrySet()) {
      byteArrays.add(separator);

      if (entry.getValue() instanceof Path) {
        var path = (Path) entry.getValue();
        String mimeType = "application/dicom";//Files.probeContentType(path);
        byteArrays.add(("\"" + entry.getKey() + "\"; filename=\"" + path.getFileName()
            + "\"\r\nContent-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        byteArrays.add(Files.readAllBytes(path));
        byteArrays.add("\r\n".getBytes(StandardCharsets.UTF_8));
      } else {
        byteArrays.add(("\"" + entry.getKey() + "\"\r\n\r\n" + entry.getValue() + "\r\n")
            .getBytes(StandardCharsets.UTF_8));
      }
    }
    byteArrays.add(("--" + boundary + "--").getBytes(StandardCharsets.UTF_8));
    return BodyPublishers.ofByteArrays(byteArrays);
  }

  @Override
  public MultipartInput wadoRs(String path) throws DicomWebException {
    throw new DicomWebException("Not implemented");
  }

  @Override
  public JSONArray qidoRs(String path) throws DicomWebException {
    HttpClient client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
//        .followRedirects(Redirect.NORMAL)
        .build();

    HttpRequest request = HttpRequest.newBuilder()
        .GET()
        .uri(URI.create(serviceUrlPrefix + "/" + trim(path)))
        .header(HttpHeaders.ACCEPT_ENCODING, "gzip")
        .header(HttpHeaders.AUTHORIZATION,
            "Bearer " + credentials.getAccessToken().getTokenValue())
        .build();

    try {
      HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
      int statusCode = response.statusCode();
      if (statusCode != HttpStatusCodes.STATUS_CODE_OK) {
        throw new DicomWebException("stowRs: " + statusCode + ", " + response.body());
      }
      return new JSONArray(response.body());
    } catch (IOException | InterruptedException e) {
      throw new DicomWebException(e);
    }
  }

  @Override
  public void stowRs(String path, InputStream in) throws DicomWebException {
    try {
      HttpClient client = HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_2)
          .build();

      String boundary = UUID.randomUUID().toString();

      Map<Object, Object> data = new LinkedHashMap<>();
      data.put("file", Path.of("/home/tlr/Downloads/dcmexample/6"));

      BodyPublisher requestBody = ofMimeMultipartData(data, boundary);

      HttpRequest request = HttpRequest.newBuilder()
          .POST(requestBody)
          .uri(URI.create(serviceUrlPrefix + "/" + trim(path)))
          .header(HttpHeaders.ACCEPT_ENCODING, "gzip")
          .header(HttpHeaders.AUTHORIZATION,
              "Bearer " + credentials.getAccessToken().getTokenValue())
          .header(HttpHeaders.CONTENT_TYPE,
              "multipart/related; type=application/dicom; boundary="
                  + boundary)
          .build();

      HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

      int statusCode = response.statusCode();
      if (statusCode != HttpStatusCodes.STATUS_CODE_OK) {
        throw new DicomWebException("stowRs: " + statusCode + ", " + response.body());
      }
    } catch (IOException | InterruptedException e) {
      throw new DicomWebException(e);
    }
  }

  public void stowRs_nope(String path, InputStream in) throws DicomWebException {
    try {
      HttpClient client = HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_2)
//          .followRedirects(Redirect.NORMAL)
          .build();

//      PipedOutputStream pipedOutputStream = new PipedOutputStream();
//      PipedInputStream pipedInputStream = new PipedInputStream(pipedOutputStream);
//      BodyPublisher requestBody = BodyPublishers.ofInputStream(() -> pipedInputStream);

      BodyPublisher requestBody = BodyPublishers
          .ofFile(Path.of("/home/tlr/Downloads/dcmexample/6"));

      HttpRequest request = HttpRequest.newBuilder()
          .POST(BodyPublishers.ofString("the body"))
          .uri(URI.create(serviceUrlPrefix + "/" + trim(path)))
          .header(HttpHeaders.ACCEPT_ENCODING, "gzip")
          .header(HttpHeaders.AUTHORIZATION,
              "Bearer " + credentials.getAccessToken().getTokenValue())
//          .header(MIME.CONTENT_TYPE,
//              "multipart/related; type=application/dicom; boundary="
//                  + multipartBody.boundary())
          .build();

//      CompletableFuture<HttpResponse<String>> responseFuture = client
//          .sendAsync(request, BodyHandlers.ofString());
//      IOUtils.copy(in, pipedOutputStream);
//      HttpResponse<String> response = responseFuture.get();

      HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

      int statusCode = response.statusCode();
      if (statusCode != HttpStatusCodes.STATUS_CODE_OK) {
        throw new DicomWebException("stowRs: " + statusCode + ", " + response.body());
      }
    } catch (IOException | InterruptedException e) {
      throw new DicomWebException(e);
    }
  }

  private String trim(String value) {
    return CharMatcher.is('/').trimFrom(value);
  }
}
