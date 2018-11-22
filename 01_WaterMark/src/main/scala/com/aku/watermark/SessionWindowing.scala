package com.aku.watermark

import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import org.apache.flink.streaming.api.scala.{ DataStream, StreamExecutionEnvironment }
import org.apache.flink.streaming.api.watermark.Watermark
import org.apache.flink.streaming.api.windowing.assigners.EventTimeSessionWindows
import org.apache.flink.streaming.api.windowing.time.Time

/**
  * An example of grouped stream windowing in session windows with session timeout of 3 msec.
  * A source fetches elements with key, timestamp, and count.
  */
object SessionWindowing {

    def main(args: Array[String]): Unit = {

        val params = ParameterTool.fromArgs(args)
        val env = StreamExecutionEnvironment.getExecutionEnvironment

        env.getConfig.setGlobalJobParameters(params)
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
        env.setParallelism(1)

        val fileOutput = params.has("output")

        val input = List(
            ("a", 1L, 1),
            ("b", 1L, 1),
            ("b", 3L, 1),
            ("b", 5L, 1),
            ("c", 6L, 1),
            // We expect to detect the session "a" earlier than this point (the old
            // functionality can only detect here when the next starts)
            ("a", 10L, 1),
            // We expect to detect session "b" and "c" at this point as well
            ("c", 11L, 1))

        val source: DataStream[(String, Long, Int)] = env.addSource(
            new SourceFunction[(String, Long, Int)]() {

                override def run(ctx: SourceContext[(String, Long, Int)]): Unit = {
                    input.foreach(value => {
                        ctx.collectWithTimestamp(value, value._2)
                        ctx.emitWatermark(new Watermark(value._2 - 1))
                    })
                    ctx.emitWatermark(new Watermark(Long.MaxValue))
                }

                override def cancel(): Unit = {}

            })

        // We create sessions for each id with max timeout of 3 time units
        val aggregated: DataStream[(String, Long, Int)] = source
                .keyBy(0)
                .window(EventTimeSessionWindows.withGap(Time.milliseconds(3L)))
                .sum(2)

        if (fileOutput) {
            aggregated.writeAsText(params.get("output"))
        } else {
            print("Printing result to stdout. Use --output to specify output path.")
            aggregated.print()
        }

        env.execute()
    }

}
