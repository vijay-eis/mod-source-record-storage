package org.folio.verticle.consumers;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import static org.folio.ActionProfile.Action.DELETE;
import static org.folio.ActionProfile.Action.MODIFY;
import static org.folio.consumers.DataImportKafkaHandler.PROFILE_SNAPSHOT_ID_KEY;
import static org.folio.consumers.ParsedRecordChunksKafkaHandler.JOB_EXECUTION_ID_HEADER;
import static org.folio.kafka.KafkaTopicNameHelper.getDefaultNameSpace;
import static org.folio.rest.jaxrs.model.DataImportEventTypes.DI_MARC_FOR_DELETE_RECEIVED;
import static org.folio.rest.jaxrs.model.DataImportEventTypes.DI_SRS_MARC_AUTHORITY_RECORD_DELETED;
import static org.folio.rest.jaxrs.model.DataImportEventTypes.DI_SRS_MARC_BIB_RECORD_CREATED;
import static org.folio.rest.jaxrs.model.DataImportEventTypes.DI_SRS_MARC_BIB_RECORD_MODIFIED_READY_FOR_POST_PROCESSING;
import static org.folio.rest.jaxrs.model.EntityType.MARC_BIBLIOGRAPHIC;
import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.JOB_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.MAPPING_PROFILE;
import static org.folio.rest.jaxrs.model.Record.RecordType.MARC_BIB;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.RegexPattern;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;
import io.github.jklingsporn.vertx.jooq.classic.reactivepg.ReactiveClassicGenericQueryExecutor;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import net.mguenther.kafka.junit.KeyValue;
import net.mguenther.kafka.junit.ObserveKeyValues;
import net.mguenther.kafka.junit.SendKeyValues;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.folio.ActionProfile;
import org.folio.JobProfile;
import org.folio.MappingProfile;
import org.folio.TestUtil;
import org.folio.dao.RecordDaoImpl;
import org.folio.dao.util.SnapshotDaoUtil;
import org.folio.dataimport.util.RestUtil;
import org.folio.kafka.KafkaTopicNameHelper;
import org.folio.processing.mapping.defaultmapper.processor.parameters.MappingParameters;
import org.folio.rest.jaxrs.model.Data;
import org.folio.rest.jaxrs.model.DataImportEventPayload;
import org.folio.rest.jaxrs.model.Event;
import org.folio.rest.jaxrs.model.ExternalIdsHolder;
import org.folio.rest.jaxrs.model.MappingDetail;
import org.folio.rest.jaxrs.model.MappingMetadataDto;
import org.folio.rest.jaxrs.model.MarcField;
import org.folio.rest.jaxrs.model.MarcMappingDetail;
import org.folio.rest.jaxrs.model.MarcSubfield;
import org.folio.rest.jaxrs.model.ParsedRecord;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.RawRecord;
import org.folio.rest.jaxrs.model.Record;
import org.folio.rest.jaxrs.model.Snapshot;
import org.folio.services.AbstractLBServiceTest;

@RunWith(VertxUnitRunner.class)
public class DataImportConsumersVerticleTest extends AbstractLBServiceTest {

  private static final String PARSED_CONTENT =
    "{\"leader\":\"01314nam  22003851a 4500\",\"fields\":[{\"001\":\"ybp7406411\"},{\"856\":{\"subfields\":[{\"u\":\"example.com\"}],\"ind1\":\" \",\"ind2\":\" \"}}]}";
  private static final String PROFILE_SNAPSHOT_URL = "/data-import-profiles/jobProfileSnapshots";
  private static final String MAPPING_METADATA_URL = "/mapping-metadata";
  private static final String RECORD_ID_HEADER = "recordId";
  private static final String CHUNK_ID_HEADER = "chunkId";

  private final String snapshotId = UUID.randomUUID().toString();
  private final String recordId = UUID.randomUUID().toString();

  private final MarcMappingDetail marcMappingDetail = new MarcMappingDetail()
    .withOrder(0)
    .withAction(MarcMappingDetail.Action.EDIT)
    .withField(new MarcField()
      .withField("856")
      .withIndicator1(null)
      .withIndicator2(null)
      .withSubfields(singletonList(new MarcSubfield()
        .withSubfield("u")
        .withSubaction(MarcSubfield.Subaction.INSERT)
        .withPosition(MarcSubfield.Position.BEFORE_STRING)
        .withData(new Data().withText("http://libproxy.smith.edu?url=")))));

  @Rule
  public WireMockRule mockServer = new WireMockRule(
    WireMockConfiguration.wireMockConfig()
      .dynamicPort()
      .notifier(new Slf4jNotifier(true)));

  private Record record;

  @Before
  public void setUp(TestContext context) throws IOException {
    WireMock.stubFor(get(new UrlPathPattern(new RegexPattern(MAPPING_METADATA_URL + "/.*"), true))
      .willReturn(WireMock.ok().withBody(Json.encode(new MappingMetadataDto()
        .withMappingParams(Json.encode(new MappingParameters()))))));

    RawRecord rawRecord = new RawRecord().withId(recordId)
      .withContent(
        new ObjectMapper().readValue(TestUtil.readFileFromPath(RAW_MARC_RECORD_CONTENT_SAMPLE_PATH), String.class));
    ParsedRecord parsedRecord = new ParsedRecord().withId(recordId)
      .withContent(PARSED_CONTENT);

    Snapshot snapshot = new Snapshot()
      .withJobExecutionId(snapshotId)
      .withProcessingStartedDate(new Date())
      .withStatus(Snapshot.Status.COMMITTED);

    record = new Record()
      .withId(recordId)
      .withSnapshotId(snapshot.getJobExecutionId())
      .withGeneration(0)
      .withMatchedId(recordId)
      .withRecordType(MARC_BIB)
      .withRawRecord(rawRecord)
      .withParsedRecord(parsedRecord)
      .withExternalIdsHolder(new ExternalIdsHolder().withAuthorityId(UUID.randomUUID().toString()));

    ReactiveClassicGenericQueryExecutor queryExecutor = postgresClientFactory.getQueryExecutor(TENANT_ID);
    RecordDaoImpl recordDao = new RecordDaoImpl(postgresClientFactory);

    SnapshotDaoUtil.save(queryExecutor, snapshot)
      .compose(v -> recordDao.saveRecord(record, TENANT_ID))
      .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void shouldModifyRecordWhenPayloadContainsModifyMarcBibActionInCurrentNode() throws InterruptedException {
    ProfileSnapshotWrapper profileSnapshotWrapper = new ProfileSnapshotWrapper()
      .withId(UUID.randomUUID().toString())
      .withContentType(JOB_PROFILE)
      .withContent(JsonObject.mapFrom(new JobProfile()
        .withId(UUID.randomUUID().toString())
        .withDataType(JobProfile.DataType.MARC)).getMap())
      .withChildSnapshotWrappers(singletonList(
        new ProfileSnapshotWrapper()
          .withContentType(ACTION_PROFILE)
          .withContent(JsonObject.mapFrom(new ActionProfile()
            .withId(UUID.randomUUID().toString())
            .withAction(MODIFY)
            .withFolioRecord(ActionProfile.FolioRecord.MARC_BIBLIOGRAPHIC)).getMap())
          .withChildSnapshotWrappers(singletonList(
            new ProfileSnapshotWrapper()
              .withContentType(MAPPING_PROFILE)
              .withContent(JsonObject.mapFrom(new MappingProfile()
                .withId(UUID.randomUUID().toString())
                .withIncomingRecordType(MARC_BIBLIOGRAPHIC)
                .withExistingRecordType(MARC_BIBLIOGRAPHIC)
                .withMappingDetails(new MappingDetail()
                  .withMarcMappingOption(MappingDetail.MarcMappingOption.MODIFY)
                  .withMarcMappingDetails(List.of(marcMappingDetail)))).getMap())))));

    WireMock.stubFor(get(new UrlPathPattern(new RegexPattern(PROFILE_SNAPSHOT_URL + "/.*"), true))
      .willReturn(WireMock.ok().withBody(Json.encode(profileSnapshotWrapper))));

    String expectedParsedContent =
      "{\"leader\":\"00107nam  22000491a 4500\",\"fields\":[{\"001\":\"ybp7406411\"},{\"856\":{\"subfields\":[{\"u\":\"http://libproxy.smith.edu?url=example.com\"}],\"ind1\":\" \",\"ind2\":\" \"}}]}";

    DataImportEventPayload eventPayload = new DataImportEventPayload()
      .withEventType(DI_SRS_MARC_BIB_RECORD_CREATED.value())
      .withJobExecutionId(snapshotId)
      .withCurrentNode(profileSnapshotWrapper.getChildSnapshotWrappers().get(0))
      .withOkapiUrl(mockServer.baseUrl())
      .withTenant(TENANT_ID)
      .withToken(TOKEN)
      .withContext(new HashMap<>() {{
        put(MARC_BIBLIOGRAPHIC.value(), Json.encode(record));
        put(PROFILE_SNAPSHOT_ID_KEY, profileSnapshotWrapper.getId());
      }});

    String topic = getTopicName(DI_SRS_MARC_BIB_RECORD_CREATED.value());
    KeyValue<String, String> kafkaRecord = buildKafkaRecord(eventPayload);
    kafkaRecord.addHeader(RECORD_ID_HEADER, record.getId(), UTF_8);
    kafkaRecord.addHeader(CHUNK_ID_HEADER, UUID.randomUUID().toString(), UTF_8);

    SendKeyValues<String, String> request = SendKeyValues.to(topic, singletonList(kafkaRecord)).useDefaults();

    // when
    cluster.send(request);

    // then
    var value = DI_SRS_MARC_BIB_RECORD_MODIFIED_READY_FOR_POST_PROCESSING.value();
    String observeTopic = getTopicName(value);
    List<KeyValue<String, String>> observedRecords = cluster.observe(ObserveKeyValues.on(observeTopic, 1)
      .observeFor(50, TimeUnit.SECONDS)
      .build());

    Event obtainedEvent = Json.decodeValue(observedRecords.get(0).getValue(), Event.class);
    DataImportEventPayload dataImportEventPayload =
      Json.decodeValue(obtainedEvent.getEventPayload(), DataImportEventPayload.class);
    assertEquals(DI_SRS_MARC_BIB_RECORD_MODIFIED_READY_FOR_POST_PROCESSING.value(),
      dataImportEventPayload.getEventType());

    Record actualRecord =
      Json.decodeValue(dataImportEventPayload.getContext().get(MARC_BIBLIOGRAPHIC.value()), Record.class);
    assertEquals(expectedParsedContent, actualRecord.getParsedRecord().getContent().toString());
    assertEquals(Record.State.ACTUAL, actualRecord.getState());
    assertEquals(dataImportEventPayload.getJobExecutionId(), actualRecord.getSnapshotId());
    assertNotNull(observedRecords.get(0).getHeaders().lastHeader(RECORD_ID_HEADER));
  }

  @Test
  public void shouldDeleteMarcAuthorityRecord() throws InterruptedException {
    ProfileSnapshotWrapper profileSnapshotWrapper = new ProfileSnapshotWrapper()
      .withId(UUID.randomUUID().toString())
      .withContentType(JOB_PROFILE)
      .withContent(JsonObject.mapFrom(new JobProfile()
        .withId(UUID.randomUUID().toString())
        .withDataType(JobProfile.DataType.MARC)).getMap())
      .withChildSnapshotWrappers(List.of(
        new ProfileSnapshotWrapper()
          .withId(UUID.randomUUID().toString())
          .withContentType(ACTION_PROFILE)
          .withOrder(0)
          .withContent(JsonObject.mapFrom(new ActionProfile()
            .withId(UUID.randomUUID().toString())
            .withAction(DELETE)
            .withFolioRecord(ActionProfile.FolioRecord.MARC_AUTHORITY)).getMap())
      ));

    WireMock.stubFor(get(new UrlPathPattern(new RegexPattern(PROFILE_SNAPSHOT_URL + "/.*"), true))
      .willReturn(WireMock.ok().withBody(Json.encode(profileSnapshotWrapper))));

    HashMap<String, String> payloadContext = new HashMap<>();
    payloadContext.put("MATCHED_MARC_AUTHORITY", Json.encode(record));
    payloadContext.put(PROFILE_SNAPSHOT_ID_KEY, profileSnapshotWrapper.getId());
    var eventPayload = new DataImportEventPayload()
      .withContext(payloadContext)
      .withOkapiUrl(mockServer.baseUrl())
      .withTenant(TENANT_ID)
      .withToken(TOKEN)
      .withJobExecutionId(snapshotId)
      .withCurrentNode(profileSnapshotWrapper.getChildSnapshotWrappers().get(0));

    String topic = getTopicName(DI_MARC_FOR_DELETE_RECEIVED.value());
    KeyValue<String, String> kafkaRecord = buildKafkaRecord(eventPayload);
    kafkaRecord.addHeader(RECORD_ID_HEADER, record.getId(), UTF_8);
    kafkaRecord.addHeader(CHUNK_ID_HEADER, UUID.randomUUID().toString(), UTF_8);

    var request = SendKeyValues.to(topic, singletonList(kafkaRecord)).useDefaults();

    // when
    cluster.send(request);

    String observeTopic = getTopicName(DI_SRS_MARC_AUTHORITY_RECORD_DELETED.name());
    List<KeyValue<String, String>> observedRecords = cluster.observe(ObserveKeyValues.on(observeTopic, 1)
      .observeFor(30, TimeUnit.SECONDS)
      .build());
    Event obtainedEvent = Json.decodeValue(observedRecords.get(0).getValue(), Event.class);
    var resultPayload = Json.decodeValue(obtainedEvent.getEventPayload(), DataImportEventPayload.class);
    assertEquals(DI_SRS_MARC_AUTHORITY_RECORD_DELETED.value(), resultPayload.getEventType());
    assertEquals(record.getExternalIdsHolder().getAuthorityId(), resultPayload.getContext().get("AUTHORITY_RECORD_ID"));
    assertEquals(ACTION_PROFILE, resultPayload.getCurrentNode().getContentType());
  }

  private String getTopicName(String value) {
    return KafkaTopicNameHelper.formatTopicName(kafkaConfig.getEnvId(), getDefaultNameSpace(), TENANT_ID, value);
  }

  private KeyValue<String, String> buildKafkaRecord(DataImportEventPayload eventPayload) {
    Event event = new Event().withEventPayload(Json.encode(eventPayload));
    KeyValue<String, String> record = new KeyValue<>("1", Json.encode(event));
    record.addHeader(RestUtil.OKAPI_URL_HEADER, mockServer.baseUrl(), StandardCharsets.UTF_8);
    record.addHeader(RestUtil.OKAPI_TENANT_HEADER, TENANT_ID, StandardCharsets.UTF_8);
    record.addHeader(RestUtil.OKAPI_TOKEN_HEADER, TOKEN, StandardCharsets.UTF_8);
    record.addHeader(JOB_EXECUTION_ID_HEADER, snapshotId, StandardCharsets.UTF_8);
    return record;
  }

}
