package com.mozilla.secops.postprocessing;

import com.mozilla.secops.DocumentingTransform;
import com.mozilla.secops.IOOptions;
import com.mozilla.secops.OutputOptions;
import com.mozilla.secops.Watchlist;
import com.mozilla.secops.alert.Alert;
import com.mozilla.secops.alert.AlertFormatter;
import com.mozilla.secops.input.Input;
import com.mozilla.secops.metrics.CfgTickBuilder;
import com.mozilla.secops.metrics.CfgTickProcessor;
import com.mozilla.secops.parser.Event;
import com.mozilla.secops.parser.EventFilter;
import com.mozilla.secops.parser.EventFilterRule;
import com.mozilla.secops.parser.ParserCfg;
import com.mozilla.secops.parser.ParserDoFn;
import com.mozilla.secops.parser.Payload;
import com.mozilla.secops.state.StateException;
import com.mozilla.secops.window.GlobalTriggers;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.Flatten;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** {@link PostProcessing} implements analysis of alerts generated by other pipelines. */
public class PostProcessing implements Serializable {
  private static final long serialVersionUID = 1L;

  /**
   * Parse incoming events and filter to only include events of type {@link
   * com.mozilla.secops.parser.Alert}
   */
  public static class Parse extends PTransform<PCollection<String>, PCollection<Event>> {
    private static final long serialVersionUID = 1L;

    private Logger log;
    private final ParserCfg cfg;

    /**
     * Static initializer for {@link Parse} using specified pipeline options
     *
     * @param options Pipeline options
     */
    public Parse(PostProcessingOptions options) {
      log = LoggerFactory.getLogger(Parse.class);
      cfg = ParserCfg.fromInputOptions(options);
    }

    @Override
    public PCollection<Event> expand(PCollection<String> col) {
      EventFilter filter = new EventFilter().passConfigurationTicks();
      filter.addRule(new EventFilterRule().wantSubtype(Payload.PayloadType.ALERT));

      return col.apply(
          ParDo.of(new ParserDoFn().withConfiguration(cfg).withInlineEventFilter(filter)));
    }
  }

  /**
   * Check incoming alert events against a watchlist of various identifiers.
   *
   * <p>Uses {@link Watchlist} to retrieve the watchlist from datastore and check these entries
   * against alert metadata keys.
   */
  public static class WatchlistAnalyze extends DoFn<Event, Alert> implements DocumentingTransform {
    private static final long serialVersionUID = 1L;
    private Logger log;
    private Watchlist wl;
    private String warningEmail;
    private String criticalEmail;

    private static final String[] emailKeys = new String[] {"email", "username", "identity_key"};
    private static final String[] ipKeys = new String[] {"sourceaddress", "sourceaddress_previous"};

    public WatchlistAnalyze(PostProcessingOptions options) {
      warningEmail = options.getWarningSeverityEmail();
      criticalEmail = options.getCriticalSeverityEmail();
    }

    public String getTransformDoc() {
      return "Alert on matched watchlist entries in incoming alerts from other pipelines.";
    }

    @Setup
    public void setup() throws IOException {
      log = LoggerFactory.getLogger(WatchlistAnalyze.class);
      try {
        wl = new Watchlist();
      } catch (StateException exc) {
        throw new RuntimeException(exc.getMessage());
      }
    }

    @Teardown
    public void teardown() throws IOException {
      wl.done();
    }

    @ProcessElement
    public void processElement(ProcessContext c) {
      Event e = c.element();
      if (!e.getPayloadType().equals(Payload.PayloadType.ALERT)) {
        return;
      }

      com.mozilla.secops.parser.Alert ae = e.getPayload();
      Alert sourceAlert = ae.getAlert();

      List<Alert> eAlerts =
          checkAlertAgainstWatchlistEntries(sourceAlert, Watchlist.watchlistEmailKind, emailKeys);
      for (Alert a : eAlerts) {
        c.output(a);
      }

      List<Alert> ipAlerts =
          checkAlertAgainstWatchlistEntries(sourceAlert, Watchlist.watchlistIpKind, ipKeys);
      for (Alert a : ipAlerts) {
        c.output(a);
      }
    }

    private List<Alert> checkAlertAgainstWatchlistEntries(
        Alert sourceAlert, String whitelistEntryType, String[] keys) {
      List<Alert> outputAlerts = new ArrayList<Alert>();
      for (String key : keys) {
        String v = sourceAlert.getMetadataValue(key);
        if (v == null) {
          continue;
        }

        Watchlist.WatchlistEntry entry = wl.getWatchlistEntry(whitelistEntryType, v);
        if (entry == null) {
          continue;
        } else {
          Alert a = new Alert();
          a.setCategory("postprocessing");
          a.addMetadata("category", "watchlist");
          a.setSummary(
              String.format(
                  "matched watchlist object found in alert %s", sourceAlert.getAlertId()));
          a.setSeverity(entry.getSeverity());

          // Add escalation metadata
          if (entry.getSeverity() == Alert.AlertSeverity.WARNING) {
            a.addMetadata("notify_email_direct", warningEmail);
          }
          if (entry.getSeverity() == Alert.AlertSeverity.CRITICAL) {
            a.addMetadata("notify_email_direct", criticalEmail);
          }

          a.addMetadata("source_alert", sourceAlert.getAlertId().toString());
          a.addMetadata("matched_metadata_key", key);
          // This may seem redundant with the below `matched_object`, but trying to
          // future proof against adding regex matchers (or similar).
          a.addMetadata("matched_metadata_value", v);
          a.addMetadata("matched_type", entry.getType());
          a.addMetadata("matched_object", entry.getObject());
          a.addMetadata("whitelisted_entry_created_by", entry.getCreatedBy());
          outputAlerts.add(a);
        }
      }
      return outputAlerts;
    }
  }

  /** Runtime options for {@link PostProcessing} pipeline. */
  public interface PostProcessingOptions extends PipelineOptions, IOOptions {
    @Description("Enable watchlist analysis")
    @Default.Boolean(true)
    Boolean getEnableWatchlistAnalysis();

    void setEnableWatchlistAnalysis(Boolean value);

    @Description("Email address to send warning level alerts to")
    String getWarningSeverityEmail();

    void setWarningSeverityEmail(String value);

    @Description("Email address to send critical level alerts to")
    String getCriticalSeverityEmail();

    void setCriticalSeverityEmail(String value);
  }

  /**
   * Build a configuration tick for PostProcessing given pipeline options
   *
   * @param options Pipeline options
   * @return String
   */
  public static String buildConfigurationTick(PostProcessingOptions options) throws IOException {
    CfgTickBuilder b = new CfgTickBuilder().includePipelineOptions(options);

    if (options.getEnableWatchlistAnalysis()) {
      b.withTransformDoc(new WatchlistAnalyze(options));
    }

    return b.build();
  }

  /**
   * Process input collection
   *
   * <p>Process collection of input events, returning a collection of alerts as required.
   *
   * @param input Input collection
   * @param options Pipeline options
   * @return Output collection
   */
  public static PCollection<Alert> processInput(
      PCollection<String> input, PostProcessingOptions options) {
    PCollectionList<Alert> alertList = PCollectionList.empty(input.getPipeline());

    PCollection<Event> inputAlerts = input.apply("parse", new Parse(options));

    if (options.getEnableWatchlistAnalysis()) {
      alertList =
          alertList.and(
              inputAlerts
                  .apply("watchlist analyze", ParDo.of(new WatchlistAnalyze(options)))
                  .apply("watchlist analyze rewindow for output", new GlobalTriggers<Alert>(5)));
    }

    // If configuration ticks were enabled, enable the processor here too
    if (options.getGenerateConfigurationTicksInterval() > 0) {
      alertList =
          alertList.and(
              inputAlerts
                  .apply(
                      "cfgtick processor",
                      ParDo.of(new CfgTickProcessor("postprocessing-cfgtick", "category")))
                  .apply(new GlobalTriggers<Alert>(5)));
    }

    return alertList.apply("flatten output", Flatten.<Alert>pCollections());
  }

  private static void runPostProcessing(PostProcessingOptions options)
      throws IllegalArgumentException {
    Pipeline p = Pipeline.create(options);

    PCollection<String> input;
    try {
      input =
          p.apply("input", Input.compositeInputAdapter(options, buildConfigurationTick(options)));
    } catch (IOException exc) {
      throw new RuntimeException(exc.getMessage());
    }
    processInput(input, options)
        .apply("output format", ParDo.of(new AlertFormatter(options)))
        .apply("output convert", MapElements.via(new AlertFormatter.AlertToString()))
        .apply("output", OutputOptions.compositeOutput(options));

    p.run();
  }

  /**
   * Entry point for Beam pipeline.
   *
   * @param args Runtime arguments.
   */
  public static void main(String[] args) throws Exception {
    PipelineOptionsFactory.register(PostProcessingOptions.class);
    PostProcessingOptions options =
        PipelineOptionsFactory.fromArgs(args).withValidation().as(PostProcessingOptions.class);
    runPostProcessing(options);
  }
}
