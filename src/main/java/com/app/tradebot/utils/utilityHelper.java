package com.app.tradebot.utils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

public class utilityHelper {

    public static Object jsonToJava(Object json) {
        if (json instanceof JSONObject) {
            JSONObject obj = (JSONObject) json;
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();

            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                map.put(key, jsonToJava(obj.get(key)));
            }
            return map;
        }
        else if (json instanceof JSONArray) {
            JSONArray arr = (JSONArray) json;
            List<Object> list = new ArrayList<>();

            for (int i = 0; i < arr.length(); i++) {
                list.add(jsonToJava(arr.get(i)));
            }
            return list;
        }
        else {
            return json;
        }
    }
}
