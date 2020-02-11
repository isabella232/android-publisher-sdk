package com.criteo.publisher.network;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import com.criteo.publisher.R;
import com.criteo.publisher.Util.StreamUtil;
import com.criteo.publisher.Util.TextUtils;
import com.criteo.publisher.model.CdbRequest;
import com.criteo.publisher.model.CdbResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

public class PubSdkApi {

  private static final int TIMEOUT_IN_MILLIS = 60 * 1000;
  private static final String TAG = PubSdkApi.class.getSimpleName();
  private static final String CRITEO_PUBLISHER_ID = "cpId";
  private static final String APP_ID = "appId";
  private static final String SDK_VERSION = "sdkVersion";
  private static final String GAID = "gaid";
  private static final String EVENT_TYPE = "eventType";
  private static final String LIMITED_AD_TRACKING = "limitedAdTracking";

  private final Context context;

  public PubSdkApi(Context context) {
    this.context = context;
  }

  @Nullable
  public JSONObject loadConfig(
      @NonNull String criteoPublisherId,
      @NonNull String appId,
      @NonNull String sdkVersion) {
    Map<String, String> parameters = new HashMap<>();
    parameters.put(CRITEO_PUBLISHER_ID, criteoPublisherId);
    parameters.put(APP_ID, appId);
    parameters.put(SDK_VERSION, sdkVersion);

    JSONObject configResult = null;
    try {
      URL url = new URL(
          context.getString(R.string.config_url) + "/v2.0/api/config" + "?" + getParamsString(
              parameters));
      configResult = executeGet(url, null);
    } catch (IOException | JSONException e) {
      Log.d(TAG, "Unable to process request to remote config TLA:" + e.getMessage());
      e.printStackTrace();
    }
    return configResult;
  }

  @Nullable
  public CdbResponse loadCdb(@NonNull CdbRequest cdbRequest, @NonNull String userAgent) {
    try {
      URL url = new URL(context.getString(R.string.cdb_url) + "/inapp/v2");
      JSONObject cdbRequestJson = cdbRequest.toJson();
      JSONObject result = executePost(url, cdbRequestJson, userAgent);
      return CdbResponse.fromJson(result);
    } catch (IOException | JSONException e) {
      Log.d(TAG, "Unable to process request to Cdb:" + e.getMessage());
      e.printStackTrace();
    }
    return null;
  }

  @Nullable
  public JSONObject postAppEvent(
      int senderId,
      @NonNull String appId,
      @Nullable String gaid,
      @NonNull String eventType,
      int limitedAdTracking,
      @NonNull String userAgent) {

    Map<String, String> parameters = new HashMap<>();
    parameters.put(APP_ID, appId);

    // If device doesnt support Playservices , gaid value stays as null
    if (gaid != null) {
      parameters.put(GAID, gaid);
    }

    parameters.put(EVENT_TYPE, eventType);
    parameters.put(LIMITED_AD_TRACKING, String.valueOf(limitedAdTracking));
    try {
      URL url = new URL(
          context.getString(R.string.event_url) + "/appevent/v1/" + senderId + "?"
              + getParamsString(
              parameters));
      return executeGet(url, userAgent);
    } catch (IOException | JSONException e) {
      Log.d(TAG, "Unable to process request to post app event:" + e.getMessage());
      e.printStackTrace();
      return null;
    }
  }

  @Nullable
  private static JSONObject executePost(
      @NonNull URL url,
      @NonNull JSONObject requestJson,
      @NonNull String userAgent)
      throws IOException, JSONException {
    HttpURLConnection urlConnection = prepareConnection(url, userAgent, "POST");
    writePayload(urlConnection, requestJson);
    return readResponseIfSuccess(urlConnection);
  }

  private static JSONObject executeGet(URL url, @Nullable String userAgent)
      throws IOException, JSONException {
    HttpURLConnection urlConnection = prepareConnection(url, userAgent, "GET");

    return readResponseIfSuccess(urlConnection);
  }

  @NonNull
  private static HttpURLConnection prepareConnection(@NonNull URL url,
      @NonNull String userAgent, String method) throws IOException {
    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
    urlConnection.setRequestMethod(method);
    urlConnection.setReadTimeout(TIMEOUT_IN_MILLIS);
    urlConnection.setConnectTimeout(TIMEOUT_IN_MILLIS);
    urlConnection.setRequestProperty("Content-Type", "text/plain");
    if (!TextUtils.isEmpty(userAgent)) {
      urlConnection.setRequestProperty("User-Agent", userAgent);
    }
    return urlConnection;
  }

  @Nullable
  private static JSONObject readResponseIfSuccess(@NonNull HttpURLConnection urlConnection)
      throws IOException, JSONException {
    int status = urlConnection.getResponseCode();
    if (status == HttpURLConnection.HTTP_OK || status == HttpURLConnection.HTTP_NO_CONTENT) {
      String response = StreamUtil.readStream(urlConnection.getInputStream());
      if (!TextUtils.isEmpty(response)) {
        return new JSONObject(response);
      }
      return new JSONObject();
    }
    return null;
  }

  private static void writePayload(
      @NonNull HttpURLConnection urlConnection,
      @NonNull JSONObject requestJson) throws IOException {
        byte[] payload = requestJson.toString().getBytes(Charset.forName("UTF-8"));

    urlConnection.setDoOutput(true);
    try (OutputStream outputStream = urlConnection.getOutputStream()) {
      outputStream.write(payload);
      outputStream.flush();
    }
  }

  private static String getParamsString(Map<String, String> params) {
    StringBuilder queryString = new StringBuilder();
    try {
      for (Map.Entry<String, String> entry : params.entrySet()) {
                queryString.append(URLEncoder.encode(entry.getKey(), Charset.forName("UTF-8").name()));
        queryString.append("=");
                queryString.append(URLEncoder.encode(entry.getValue(), Charset.forName("UTF-8").name()));
        queryString.append("&");
      }
    } catch (Exception e) {
      Log.e(TAG, e.getMessage());
    }

    // drop the last '&' if result is not empty
    return queryString.length() > 0
        ? queryString.substring(0, queryString.length() - 1)
        : queryString.toString();
  }

}
