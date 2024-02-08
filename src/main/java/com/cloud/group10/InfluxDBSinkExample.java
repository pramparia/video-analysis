package com.cloud.group10;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cloud.group10.entity.Entity;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import influxdb.sink.InfluxDBSink;
import influxdb.sink.writer.InfluxDBSchemaSerializer;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.connector.sink.SinkWriter;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class InfluxDBSinkExample {
    private static final Logger LOG = LoggerFactory.getLogger(InfluxDBSinkExample.class);

    private static final int N = 10000;

    public static void main(String[] args) throws Exception {

        System.out.println(String.format("args:%s",JSON.toJSONString(args)));
        // Check arguments length value
        if (args.length == 0) {
            System.out.println("Enter args para");
            return;
        }

        String kafkaServer = args[0];
        String influxdbServer = args[1];

        StreamExecutionEnvironment env = createEnv(false);
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaServer)
                .setGroupId("test-group")
                .setTopics("test")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStreamSource<String> kafkaDataStream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "kafkaSource");

        SingleOutputStreamOperator<Point> pointSingleOutputStreamOperator = kafkaDataStream
                .map(new RichMapFunction<String, Point>() {
                    @Override
                    public Point map(String s) {
                        // {"name":"log-7","tags":{"logType": "2"},"fields":{"type": 1},"time":1700328589082}
                        System.out.println("receive msg:" + s);
//                        JSONObject jsonObject = new JSONObject();
//                        try {
//                            jsonObject = JSONObject.parseObject(s);
//                            String name = jsonObject.getString("eventName");
//                            Long time = jsonObject.getLong("time");
//                            String logType = jsonObject.getString("date");
//                            Integer type = 1;
//                            Point point = new Point(name);
//                            point.addTag("logType", logType)
//                                    .addField("type", type)
//                                    .time(time, WritePrecision.MS);
//                            return point;
//                        } catch (Exception e) {
//                            System.out.println("json is not valid:" + s);
//                            System.out.println(e);
//                            return null;
//                        }
                        try {
                            Entity entity = JSONObject.parseObject(s, Entity.class);
                            String eventName = entity.getEventName();
                            Point point = new Point(eventName);
                            if (entity.getUserAge() != null) {
                                point = point.addTag("user_age", entity.getUserAge().toString());
                            }
                            if (StringUtils.isNotBlank(entity.getUserGender())) {
                                point = point.addTag("user_gender", entity.getUserGender());
                            }
                            if (StringUtils.isNotBlank(entity.getUserProvince())) {
                                point = point.addTag("user_province", entity.getUserProvince());
                            }
                            if (StringUtils.isNotBlank(entity.getVideoSubject())) {
                                point = point.addTag("video_subject", entity.getVideoSubject());
                            }
                            if (StringUtils.isNotBlank(entity.getVideoSubSubject())) {
                                point = point.addTag("video_sub_subject", entity.getVideoSubSubject());
                            }
                            if (entity.getVideoDuration() != null) {
                                point = point.addTag("video_duration", entity.getVideoDuration().toString());
                            }
                            if (entity.getVideoPlayedDuration() != null) {
                                point = point.addField("videoPlayedDuration", entity.getVideoPlayedDuration().toString());
                            }
                            if (entity.getVideoPlayedTimes() != null) {
                                point = point.addField("videoPlayedTimes", entity.getVideoPlayedTimes().toString());
                            }
                            point = point
                                    .addField("cnt_", 1)
                                    .time(entity.getTime(), WritePrecision.MS);
                            return point;
                        } catch (Exception e) {
                            System.err.println(String.format("json is not valid:%s", s));
                            System.err.println(e);
                            return null;
                        }
                    }
                })
                .filter(d -> d != null);


        pointSingleOutputStreamOperator.sinkTo(sinkToInfluxDB2(influxdbServer));

        env.execute("InfluxDB Sink Example");
    }

    /**
     * 将数据写入influxDB2
     *
     * @return InfluxDBSink<LogFormat></>
     */
    private static InfluxDBSink<Point> sinkToInfluxDB2(String influxdbServer) {
        return InfluxDBSink.builder()
                .setInfluxDBSchemaSerializer(
                        new InfluxDBSchemaSerializer<Point>() {
                            private static final long serialVersionUID = 1L;

                            @Override
                            public Point serialize(Point point, SinkWriter.Context context) throws IOException {
                                return point;
                            }
                        }
                )
                .setInfluxDBUrl(String.format("http://%s",influxdbServer))
                .setInfluxDBBucket("cloudTech")
                .setInfluxDBUsername("tony")
                .setInfluxDBPassword("groupten")
                .setInfluxDBOrganization("LU")
                .setInfluxDBToken("fZgi0WeCn2W2X5WxQNKt0X_LjGb1WY-rjCeZVEceGTJzAEL_aWmw_HtlQWD097TikA8LMZ_Hwg1Ifx_bKWbg2w==")
                .setWriteBufferSize(16)
                .build();
    }

    private static StreamExecutionEnvironment createEnv(boolean idleCheck) {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
//        env.enableCheckpointing(1000, CheckpointingMode.AT_LEAST_ONCE);
        if (idleCheck) {
            Map<String, String> args = new HashMap<>();
            args.put("source.idle.timeout.ms", "5000");
            env.getConfig().setGlobalJobParameters(ParameterTool.fromMap(args));
        }
        env.setParallelism(1);
        return env;
    }
}
