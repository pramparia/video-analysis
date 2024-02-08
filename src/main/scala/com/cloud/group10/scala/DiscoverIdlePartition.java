package com.cloud.group10.scala;

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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Test for discover idle partition.
 * Run this class first then run the ProducerTestData.java
 */
public class DiscoverIdlePartition {
    public static void main(String[] args) throws Exception {
        // with idle check， 7 output
//        StreamExecutionEnvironment env = createEnv(true);
        // without idle check, 4 output
        StreamExecutionEnvironment env = createEnv(false);

        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers("35.226.135.93:9092")
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
                        /**
                         * {
                         *   "authorId": "author_Fitness_Yoga and Meditation_0",
                         *   "date": "2023-12-25",
                         *   "eventName": "video_exposure",
                         *   "time": 1700933813520,
                         *   "userAge": 41,
                         *   "userGender": "Male",
                         *   "userId": "user_British Columbia_3",
                         *   "userProvince": "Ontario",
                         *   "videoDuration": 117,
                         *   "videoId": "video_Fitness_Yoga and Meditation_0",
                         *   "videoSubtype": "Yoga and Meditation",
                         *   "videoType": "Fitness"
                         * }
                         */
                        System.out.println("receive msg:" + s);
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
                                point = point.addTag("video_type", entity.getVideoSubject());
                            }
                            if (StringUtils.isNotBlank(entity.getVideoSubSubject())) {
                                point = point.addTag("video_subtype", entity.getVideoSubSubject());
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

        pointSingleOutputStreamOperator.sinkTo(sinkToInfluxDB2());

        env.execute("kafkaSource");
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

    /**
     * 将数据写入influxDB2
     *
     * @return InfluxDBSink<LogFormat></>
     */
    private static InfluxDBSink<Point> sinkToInfluxDB2() {
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
                .setInfluxDBUrl("http://34.67.201.141:8086")
                .setInfluxDBBucket("cloudTech")
                .setInfluxDBUsername("tony")
                .setInfluxDBPassword("groupten")
                .setInfluxDBOrganization("LU")
                .setInfluxDBToken("Token fZgi0WeCn2W2X5WxQNKt0X_LjGb1WY-rjCeZVEceGTJzAEL_aWmw_HtlQWD097TikA8LMZ_Hwg1Ifx_bKWbg2w==")
                .setWriteBufferSize(100)
                .build();
    }
}
