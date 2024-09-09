package io.vertx.core.http;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx.core.http.StreamPriorityBase}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.http.StreamPriorityBase} original class using Vert.x codegen.
 */
public class StreamPriorityBaseConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, StreamPriorityBase obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "dependency":
          if (member.getValue() instanceof Number) {
            obj.setDependency(((Number)member.getValue()).intValue());
          }
          break;
        case "exclusive":
          if (member.getValue() instanceof Boolean) {
            obj.setExclusive((Boolean)member.getValue());
          }
          break;
        case "incremental":
          break;
        case "weight":
          if (member.getValue() instanceof Number) {
            obj.setWeight(((Number)member.getValue()).shortValue());
          }
          break;
      }
    }
  }

   static void toJson(StreamPriorityBase obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(StreamPriorityBase obj, java.util.Map<String, Object> json) {
    json.put("dependency", obj.getDependency());
    json.put("exclusive", obj.isExclusive());
    json.put("incremental", obj.isIncremental());
    json.put("weight", obj.getWeight());
  }
}
