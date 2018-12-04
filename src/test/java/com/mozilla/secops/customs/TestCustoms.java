package com.mozilla.secops.customs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.mozilla.secops.TestUtil;
import com.mozilla.secops.alert.Alert;
import com.mozilla.secops.parser.Event;
import com.mozilla.secops.parser.ParserDoFn;
import java.util.ArrayList;
import java.util.Collection;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Combine;
import org.apache.beam.sdk.transforms.Count;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.windowing.IntervalWindow;
import org.apache.beam.sdk.values.PCollection;
import org.joda.time.Instant;
import org.junit.Rule;
import org.junit.Test;

public class TestCustoms {
  @Rule public final transient TestPipeline p = TestPipeline.create();

  public TestCustoms() {}

  @Test
  public void noopPipelineTest() throws Exception {
    p.run().waitUntilFinish();
  }

  @Test
  public void parseTest() throws Exception {
    PCollection<String> input =
        TestUtil.getTestInput("/testdata/customs_rl_badlogin_simple1.txt", p);

    PCollection<Long> count =
        input
            .apply(ParDo.of(new ParserDoFn()))
            .apply(Combine.globally(Count.<Event>combineFn()).withoutDefaults());

    PAssert.that(count).containsInAnyOrder(447L);

    p.run().waitUntilFinish();
  }

  @Test
  public void customsMulti1Test() throws Exception {
    PCollection<String> input = TestUtil.getTestInput("/testdata/customs_multi1.txt", p);

    CustomsCfg cfg = CustomsCfg.loadFromResource("/customs/customsdefault.json");
    // Force use of event timestamp for testing purposes
    cfg.setTimestampOverride(true);

    PCollection<Alert> alerts =
        input.apply(ParDo.of(new ParserDoFn())).apply(new Customs.Detectors(cfg));

    ArrayList<IntervalWindow> windows = new ArrayList<IntervalWindow>();
    windows.add(new IntervalWindow(new Instant(3600000L), new Instant(4500000L)));
    windows.add(new IntervalWindow(new Instant(4500000L), new Instant(5400000L)));
    for (IntervalWindow w : windows) {
      PAssert.that(alerts)
          .inWindow(w)
          .satisfies(
              x -> {
                Alert[] a = ((Collection<Alert>) x).toArray(new Alert[0]);
                assertEquals(1, a.length);
                assertEquals("customs", a[0].getCategory());
                assertEquals(Alert.AlertSeverity.INFORMATIONAL, a[0].getSeverity());
                assertEquals(
                    "10.0.0.1+picard@uss.enterprise", a[0].getMetadataValue("customs_suspected"));
                assertEquals(
                    "rl_login_failure_sourceaddress_accountid",
                    a[0].getMetadataValue("customs_category"));
                assertEquals("3", a[0].getMetadataValue("customs_threshold"));
                assertEquals("3", a[0].getMetadataValue("customs_count"));
                assertEquals(
                    "picard@uss.enterprise", a[0].getMetadataValue("customs_actor_accountid"));

                Iterable<Event> samples =
                    Event.jsonToIterable(a[0].getMetadataValue("customs_sample"));
                assertNotNull(samples);
                Event[] elist = ((Collection<Event>) samples).toArray(new Event[0]);
                assertNotNull(elist);
                assertEquals(3, elist.length);
                return null;
              });
    }

    windows.clear();
    windows.add(new IntervalWindow(new Instant(5400000L), new Instant(6300000L)));
    windows.add(new IntervalWindow(new Instant(6300000L), new Instant(7200000L)));
    windows.add(new IntervalWindow(new Instant(7200000L), new Instant(8100000L)));
    windows.add(new IntervalWindow(new Instant(8100000L), new Instant(9000000L)));
    for (IntervalWindow w : windows) {
      PAssert.that(alerts)
          .inWindow(w)
          .satisfies(
              x -> {
                Alert[] a = ((Collection<Alert>) x).toArray(new Alert[0]);
                assertEquals(3, a.length);
                for (Alert ta : a) {
                  assertEquals("5", ta.getMetadataValue("customs_threshold"));
                  assertEquals("6", ta.getMetadataValue("customs_count"));
                  assertEquals("customs", ta.getCategory());
                  String cc = ta.getMetadataValue("customs_category");
                  if (cc.equals("rl_sms_recipient")) {
                    assertEquals("00000000000", ta.getMetadataValue("customs_suspected"));
                  } else if (cc.equals("rl_sms_sourceaddress")) {
                    assertEquals("10.0.0.2", ta.getMetadataValue("customs_suspected"));
                  } else if (cc.equals("rl_sms_accountid")) {
                    assertEquals("worf@uss.enterprise", ta.getMetadataValue("customs_suspected"));
                  } else {
                    fail("invalid customs category: " + cc);
                  }
                }
                return null;
              });
    }

    p.run().waitUntilFinish();
  }

  @Test
  public void rlLoginFailureSourceAddressTest() throws Exception {
    PCollection<String> input =
        TestUtil.getTestInput("/testdata/customs_rl_badlogin_simple1.txt", p);

    CustomsCfg cfg = CustomsCfg.loadFromResource("/customs/customsdefault.json");
    // Force use of event timestamp for testing purposes
    cfg.setTimestampOverride(true);

    PCollection<Alert> alerts =
        input.apply(ParDo.of(new ParserDoFn())).apply(new Customs.Detectors(cfg));

    ArrayList<IntervalWindow> windows = new ArrayList<IntervalWindow>();
    windows.add(new IntervalWindow(new Instant(1800000L), new Instant(2700000L)));
    windows.add(new IntervalWindow(new Instant(2700000L), new Instant(3600000L)));
    windows.add(new IntervalWindow(new Instant(11700000L), new Instant(12600000L)));
    windows.add(new IntervalWindow(new Instant(12600000L), new Instant(13500000L)));

    PCollection<Long> count =
        alerts.apply(Combine.globally(Count.<Alert>combineFn()).withoutDefaults());
    PAssert.that(count)
        .satisfies(
            x -> {
              int cnt = 0;
              for (Long l : x) {
                cnt += l;
              }
              assertEquals(4L, cnt);
              return null;
            });

    for (IntervalWindow w : windows) {
      PAssert.that(alerts)
          .inWindow(w)
          .satisfies(
              x -> {
                int cnt = 0;
                for (Alert a : x) {
                  assertEquals("customs", a.getCategory());
                  assertEquals(
                      "127.0.0.1+q@the-q-continuum", a.getMetadataValue("customs_suspected"));
                  cnt++;
                }
                assertEquals(1, cnt);
                return null;
              });
    }

    p.run().waitUntilFinish();
  }
}
