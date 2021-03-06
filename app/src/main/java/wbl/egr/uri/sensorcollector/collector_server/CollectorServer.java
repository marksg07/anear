package wbl.egr.uri.sensorcollector.collector_server;

import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fi.iki.elonen.NanoHTTPD;
import wbl.egr.uri.sensorcollector.fitbit.FBClient;
import wbl.egr.uri.sensorcollector.fitbit.events.FBHeartRateEvent;
import wbl.egr.uri.sensorcollector.fitbit.listeners.FBHeartRateEventListener;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CollectorServer extends NanoHTTPD {
  private static final Gson GSON = new Gson();
  private FBClient cli;

  public CollectorServer(FBClient c) {
    super(9673);
    Log.d("collector", "Collector server started!");
    cli = c;
  }

  @Override
  public void start() throws IOException {
    Log.d("collector", "Called .start!");
    super.start();
  }

  @Override
  public void stop() {
    Log.d("collector", "Called .stop!");
    super.stop();
  }

  @Override
  public Response serve(IHTTPSession session) {
    Log.d("collector", "Got request!");
    Map<String, List<String>> decodedQueryParameters =
            decodeParameters(session.getQueryParameterString());
    String uri = session.getUri();
    Log.i("server", uri);
    if (uri.equals("/heartrate")) {
      JsonParser parser = new JsonParser();
      JsonObject json;
      final HashMap<String, String> map = new HashMap<String, String>();
      String jsonString;
      try {
        session.parseBody(map);
        jsonString = map.get("postData");
      } catch (Exception e) {
        e.printStackTrace();
        return newFixedLengthResponse("bad");
      }
      try {
        json = (JsonObject) parser.parse(jsonString);
      } catch (Exception e) {
        Log.i("Server", "Caught request with no HR! :(");
        return newFixedLengthResponse("bad");
      }
      double hr = json.get("heartrate").getAsDouble();
      Log.i("Server", "Got heart rate: " + hr);
      FBHeartRateEventListener hrlist = cli.getSensorManager().getHeartRateEventListener();
      if (hrlist != null) {
        hrlist.onBandHeartRateChanged(new FBHeartRateEvent(hr));
      }
    }
    return newFixedLengthResponse("good");
  }

  private String toString(Map<String, ? extends Object> map) {
    if (map.size() == 0) {
      return "";
    }
    return unsortedList(map);
  }

  private String unsortedList(Map<String, ? extends Object> map) {
    StringBuilder sb = new StringBuilder();
    sb.append("<ul>");
    for (Map.Entry<String, ? extends Object> entry : map.entrySet()) {
      listItem(sb, entry);
    }
    sb.append("</ul>");
    return sb.toString();
  }

  private void listItem(StringBuilder sb, Map.Entry<String, ? extends Object> entry) {
    sb.append("<li><code><b>").append(entry.getKey()).append("</b> = ").append(entry.getValue()).append("</code></li>");
  }
}
