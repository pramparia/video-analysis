package com.cloud.groupten.test;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import influxdb.common.DataPoint;

import java.time.Instant;

public class Test {

    public static void main(String[] args) {
        // {"name":"log-7","tags":{"logType": "2"},"fields":{"type": 1},"time":1700328589082}
        String json = "{\"name\":\"log-7\",\"tags\":{\"logType\": \"2\"},\"fields\":{\"type\": 1},\"time\":1700328589082}";
        JSONObject jsonObject = JSONObject.parseObject(json);
        String name = jsonObject.getString("name");
        Long time = jsonObject.getLong("time");
        JSONObject tags = jsonObject.getJSONObject("tags");
        String logType = tags.getString("logType");
        JSONObject fields = jsonObject.getJSONObject("fields");
        Integer type = fields.getInteger("type");
        Point point = new Point(name);
        point.addTag("logType", logType)
                .addField("type", type)
                .time(time, WritePrecision.MS);


//        DataPoint dataPoint = new DataPoint("log-7", System.currentTimeMillis());
//        dataPoint.addTag("logType", "2");
//        dataPoint.addField("type", 1);
        System.out.println(JSON.toJSON(point));
    }
}
