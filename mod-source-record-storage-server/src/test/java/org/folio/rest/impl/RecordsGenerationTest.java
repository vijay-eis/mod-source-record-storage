package org.folio.rest.impl;

import io.restassured.RestAssured;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.http.HttpStatus;
import org.folio.rest.jaxrs.model.ParsedRecord;
import org.folio.rest.jaxrs.model.RawRecord;
import org.folio.rest.jaxrs.model.Record;
import org.folio.rest.jaxrs.model.Snapshot;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;

@RunWith(VertxUnitRunner.class)
public class RecordsGenerationTest extends AbstractRestVerticleTest {

  private static final String SOURCE_STORAGE_RECORDS_PATH = "/source-storage/records";
  private static final String SOURCE_STORAGE_SNAPSHOTS_PATH = "/source-storage/snapshots";
  private static final String SNAPSHOTS_TABLE_NAME = "snapshots";
  private static final String RECORDS_TABLE_NAME = "records";
  private static final String RAW_RECORDS_TABLE_NAME = "raw_records";
  private static final String ERROR_RECORDS_TABLE_NAME = "error_records";
  private static final String MARC_RECORDS_TABLE_NAME = "marc_records";

  private static String matchedId = UUID.randomUUID().toString();
  private static RawRecord rawRecord = new RawRecord()
    .withContent("01542ccm a2200361   450000100070000000500170000700800410002401000190006503500200008403500110010404" +
      "0002300115041001900138045000900157047007500166050001400241100004200255240001000297245016700307246002400474260" +
      "0036004983000025005345050314005596500016008736500023008896500049009126500042009619020023010039050021010269480" +
      "04201047948002701089948002701116948003701143\u001e393893\u001e20141107001016.0\u001e830419m19559999gw mua   hiz" +
      "   n    lat  \u001e  \u001fa   55001156/M \u001e  \u001fa(OCoLC)63611770\u001e  \u001fa393893\u001e  \u001fcUPB" +
      "\u001fdUPB\u001fdNIC\u001fdNIC\u001e0 \u001falatitager\u001fgger\u001e  \u001fav6v9\u001e  \u001facn\u001fact" +
      "\u001faco\u001fadf\u001fadv\u001faft\u001fafg\u001fams\u001fami\u001fanc\u001faop\u001faov\u001farq\u001fasn" +
      "\u001fasu\u001fasy\u001favr\u001fazz\u001e0 \u001faM3\u001fb.M896\u001e1 \u001faMozart, Wolfgang Amadeus,\u001f" +
      "d1756-1791.\u001e10\u001faWorks\u001e10\u001faNeue Ausgabe sämtlicher Werke,\u001fbin Verbindung mit den Mozartstädten," +
      " Augsburg, Salzburg und Wien.\u001fcHrsg. von der Internationalen Stiftung Mozarteum, Salzburg.\u001e33\u001f" +
      "aNeue Mozart-Ausgabe\u001e  \u001faKassel,\u001fbBärenreiter,\u001fcc1955-\u001e  \u001fav.\u001fbfacsims.\u001fc33 cm." +
      "\u001e0 \u001faSer. I. Geistliche Gesangswerke -- Ser. II. Opern -- Ser. III. Lieder, mehrstimmige Gesänge, Kanons" +
      " -- Ser. IV. Orchesterwerke -- Ser. V. Konzerte -- Ser. VI. Kirchensonaten -- Ser. VII. Ensemblemusik für grössere " +
      "Solobesetzungen -- Ser. VIII. Kammermusik -- Ser. IX. Klaviermusik -- Ser. X. Supplement.\u001e 0\u001faVocal music" +
      "\u001e 0\u001faInstrumental music\u001e 7\u001faInstrumental music\u001f2fast\u001f0(OCoLC)fst00974414\u001e 7\u001f" +
      "aVocal music\u001f2fast\u001f0(OCoLC)fst01168379\u001e  \u001fapfnd\u001fbAustin Music\u001e  \u001fa19980728120000.0" +
      "\u001e1 \u001fa20100622\u001fbs\u001fdlap11\u001felts\u001fxToAddCatStat\u001e0 \u001fa20110818\u001fbr\u001fdnp55" +
      "\u001felts\u001e2 \u001fa20130128\u001fbm\u001fdbmt1\u001felts\u001e2 \u001fa20141106\u001fbm\u001fdbatch\u001felts\u001fxaddfast\u001e\u001d");
  private static ParsedRecord marcRecord = new ParsedRecord()
    .withContent("{\"leader\":\"01542ccm a2200361   4500\",\"fields\":[{\"001\":\"393893\"},{\"005\":\"20141107001016.0\"}," +
      "{\"008\":\"830419m19559999gw mua   hiz   n    lat  \"},{\"010\":{\"subfields\":[{\"a\":\"   55001156/M \"}],\"ind1\":\"" +
      " \",\"ind2\":\" \"}},{\"035\":{\"subfields\":[{\"a\":\"(OCoLC)63611770\"}],\"ind1\":\" \",\"ind2\":\" \"}},{\"035\":{\"subfields\"" +
      ":[{\"a\":\"393893\"}],\"ind1\":\" \",\"ind2\":\" \"}},{\"040\":{\"subfields\":[{\"c\":\"UPB\"},{\"d\":\"UPB\"},{\"d\":\"NIC\"}," +
      "{\"d\":\"NIC\"}],\"ind1\":\" \",\"ind2\":\" \"}},{\"041\":{\"subfields\":[{\"a\":\"latitager\"},{\"g\":\"ger\"}],\"ind1\":\"0\"," +
      "\"ind2\":\" \"}},{\"045\":{\"subfields\":[{\"a\":\"v6v9\"}],\"ind1\":\" \",\"ind2\":\" \"}},{\"047\":{\"subfields\":[{\"a\":\"cn\"}," +
      "{\"a\":\"ct\"},{\"a\":\"co\"},{\"a\":\"df\"},{\"a\":\"dv\"},{\"a\":\"ft\"},{\"a\":\"fg\"},{\"a\":\"ms\"},{\"a\":\"mi\"},{\"a\":\"nc\"}," +
      "{\"a\":\"op\"},{\"a\":\"ov\"},{\"a\":\"rq\"},{\"a\":\"sn\"},{\"a\":\"su\"},{\"a\":\"sy\"},{\"a\":\"vr\"},{\"a\":\"zz\"}],\"ind1\":\" \",\"ind2\":\" \"}}," +
      "{\"050\":{\"subfields\":[{\"a\":\"M3\"},{\"b\":\".M896\"}],\"ind1\":\"0\",\"ind2\":\" \"}},{\"100\":{\"subfields\":[{\"a\":\"Mozart, Wolfgang Amadeus,\"}," +
      "{\"d\":\"1756-1791.\"}],\"ind1\":\"1\",\"ind2\":\" \"}},{\"240\":{\"subfields\":[{\"a\":\"Works\"}],\"ind1\":\"1\",\"ind2\":\"0\"}},{\"245\":{\"subfields\"" +
      ":[{\"a\":\"Neue Ausgabe sa\\u0308mtlicher Werke,\"},{\"b\":\"in Verbindung mit den Mozartsta\\u0308dten, Augsburg, Salzburg und Wien.\"},{\"c\":\"Hrsg. von der" +
      " Internationalen Stiftung Mozarteum, Salzburg.\"}],\"ind1\":\"1\",\"ind2\":\"0\"}},{\"246\":{\"subfields\":[{\"a\":\"Neue Mozart-Ausgabe\"}],\"ind1\":\"3\"," +
      "\"ind2\":\"3\"}},{\"260\":{\"subfields\":[{\"a\":\"Kassel,\"},{\"b\":\"Ba\\u0308renreiter,\"},{\"c\":\"c1955-\"}],\"ind1\":\" \",\"ind2\":\" \"}},{\"300\":" +
      "{\"subfields\":[{\"a\":\"v.\"},{\"b\":\"facsims.\"},{\"c\":\"33 cm.\"}],\"ind1\":\" \",\"ind2\":\" \"}},{\"505\":{\"subfields\":[{\"a\":\"Ser. I. Geistliche" +
      " Gesangswerke -- Ser. II. Opern -- Ser. III. Lieder, mehrstimmige Gesa\\u0308nge, Kanons -- Ser. IV. Orchesterwerke -- Ser. V. Konzerte -- Ser. VI. Kirchensonaten " +
      "-- Ser. VII. Ensemblemusik fu\\u0308r gro\\u0308ssere Solobesetzungen -- Ser. VIII. Kammermusik -- Ser. IX. Klaviermusik -- Ser. X. Supplement.\"}],\"ind1\":\"0\"," +
      "\"ind2\":\" \"}},{\"650\":{\"subfields\":[{\"a\":\"Vocal music\"}],\"ind1\":\" \",\"ind2\":\"0\"}},{\"650\":{\"subfields\":[{\"a\":\"Instrumental music\"}],\"ind1\":" +
      "\" \",\"ind2\":\"0\"}},{\"650\":{\"subfields\":[{\"a\":\"Instrumental music\"},{\"2\":\"fast\"},{\"0\":\"(OCoLC)fst00974414\"}],\"ind1\":\" \",\"ind2\":\"7\"}},{\"650\"" +
      ":{\"subfields\":[{\"a\":\"Vocal music\"},{\"2\":\"fast\"},{\"0\":\"(OCoLC)fst01168379\"}],\"ind1\":\" \",\"ind2\":\"7\"}},{\"902\":{\"subfields\":[{\"a\":\"pfnd\"},{\"b\"" +
      ":\"Austin Music\"}],\"ind1\":\" \",\"ind2\":\" \"}},{\"905\":{\"subfields\":[{\"a\":\"19980728120000.0\"}],\"ind1\":\" \",\"ind2\":\" \"}},{\"948\":{\"subfields\":[{\"a\"" +
      ":\"20100622\"},{\"b\":\"s\"},{\"d\":\"lap11\"},{\"e\":\"lts\"},{\"x\":\"ToAddCatStat\"}],\"ind1\":\"1\",\"ind2\":\" \"}},{\"948\":{\"subfields\":[{\"a\":\"20110818\"}," +
      "{\"b\":\"r\"},{\"d\":\"np55\"},{\"e\":\"lts\"}],\"ind1\":\"0\",\"ind2\":\" \"}},{\"948\":{\"subfields\":[{\"a\":\"20130128\"},{\"b\":\"m\"},{\"d\":\"bmt1\"},{\"e\":\"lts\"}]," +
      "\"ind1\":\"2\",\"ind2\":\" \"}},{\"948\":{\"subfields\":[{\"a\":\"20141106\"},{\"b\":\"m\"},{\"d\":\"batch\"},{\"e\":\"lts\"},{\"x\":\"addfast\"}],\"ind1\":\"2\",\"ind2\":\"" +
      " \"}}]}\n");
  private static Snapshot snapshot_1 = new Snapshot()
    .withJobExecutionId(UUID.randomUUID().toString())
    .withStatus(Snapshot.Status.NEW);
  private static Snapshot snapshot_2 = new Snapshot()
    .withJobExecutionId(UUID.randomUUID().toString())
    .withStatus(Snapshot.Status.NEW);
  private static Snapshot snapshot_3 = new Snapshot()
    .withJobExecutionId(UUID.randomUUID().toString())
    .withStatus(Snapshot.Status.NEW);
  private static Snapshot snapshot_4 = new Snapshot()
    .withJobExecutionId(UUID.randomUUID().toString())
    .withStatus(Snapshot.Status.NEW);

  @Override
  public void clearTables(TestContext context) {
    Async async = context.async();
    PostgresClient pgClient = PostgresClient.getInstance(vertx, TENANT_ID);
    pgClient.delete(RECORDS_TABLE_NAME, new Criterion(), event -> {
      pgClient.delete(RAW_RECORDS_TABLE_NAME, new Criterion(), event1 -> {
        pgClient.delete(ERROR_RECORDS_TABLE_NAME, new Criterion(), event2 -> {
          pgClient.delete(MARC_RECORDS_TABLE_NAME, new Criterion(), event3 -> {
            pgClient.delete(SNAPSHOTS_TABLE_NAME, new Criterion(), event4 -> {
              if (event4.failed()) {
                context.fail(event4.cause());
              }
              async.complete();
            });
          });
        });
      });
    });
  }

  @Test
  public void shouldCalculateRecordsGeneration(TestContext testContext) {
    List<Snapshot> snapshots = Arrays.asList(snapshot_1, snapshot_2, snapshot_3, snapshot_4);
    for (int i = 0; i < snapshots.size(); i++) {
      Async async = testContext.async();
      RestAssured.given()
        .spec(spec)
        .body(snapshots.get(i).withStatus(Snapshot.Status.PARSING_IN_PROGRESS))
        .when()
        .post(SOURCE_STORAGE_SNAPSHOTS_PATH)
        .then()
        .statusCode(HttpStatus.SC_CREATED);
      async.complete();

      async = testContext.async();
      Record record = new Record()
        .withSnapshotId(snapshots.get(i).getJobExecutionId())
        .withRecordType(Record.RecordType.MARC)
        .withRawRecord(rawRecord)
        .withParsedRecord(marcRecord)
        .withMatchedId(matchedId);

      Record created = RestAssured.given()
        .spec(spec)
        .body(record)
        .when()
        .post(SOURCE_STORAGE_RECORDS_PATH)
        .body().as(Record.class);

      snapshots.get(i).setStatus(Snapshot.Status.COMMITTED);
      RestAssured.given()
        .spec(spec)
        .body(snapshots.get(i))
        .when()
        .put(SOURCE_STORAGE_SNAPSHOTS_PATH + "/" + snapshots.get(i).getJobExecutionId())
        .then()
        .statusCode(HttpStatus.SC_OK);
      async.complete();

      async = testContext.async();
      RestAssured.given()
        .spec(spec)
        .when()
        .get(SOURCE_STORAGE_RECORDS_PATH + "/" + created.getId())
        .then()
        .statusCode(HttpStatus.SC_OK)
        .body("id", is(created.getId()))
        .body("rawRecord.content", is(rawRecord.getContent()))
        .body("matchedId", is(matchedId))
        .body("generation", is(i));
      async.complete();
    }
  }

  @Test
  public void shouldNotUpdateRecordsGenerationIfSnapshotsNotCommitted(TestContext testContext) {
    List<Snapshot> snapshots = Arrays.asList(snapshot_1, snapshot_2, snapshot_3, snapshot_4);
    for (Snapshot snapshot : snapshots) {
      Async async = testContext.async();
      RestAssured.given()
        .spec(spec)
        .body(snapshot.withStatus(Snapshot.Status.PARSING_IN_PROGRESS))
        .when()
        .post(SOURCE_STORAGE_SNAPSHOTS_PATH)
        .then()
        .statusCode(HttpStatus.SC_CREATED);
      async.complete();

      async = testContext.async();
      Record record = new Record()
        .withSnapshotId(snapshot.getJobExecutionId())
        .withRecordType(Record.RecordType.MARC)
        .withRawRecord(rawRecord)
        .withParsedRecord(marcRecord)
        .withMatchedId(matchedId);

      Record created = RestAssured.given()
        .spec(spec)
        .body(record)
        .when()
        .post(SOURCE_STORAGE_RECORDS_PATH)
        .body().as(Record.class);
      async.complete();

      async = testContext.async();
      RestAssured.given()
        .spec(spec)
        .when()
        .get(SOURCE_STORAGE_RECORDS_PATH + "/" + created.getId())
        .then()
        .statusCode(HttpStatus.SC_OK)
        .body("id", is(created.getId()))
        .body("rawRecord.content", is(rawRecord.getContent()))
        .body("matchedId", is(matchedId))
        .body("generation", is(0));
      async.complete();
    }
  }

  @Test
  public void shouldNotUpdateRecordsGenerationIfSnapshotsCommittedAfter(TestContext testContext) {
    List<Snapshot> snapshots = Arrays.asList(snapshot_1, snapshot_2);
    for (int i = 0; i < snapshots.size(); i++) {
      Async async = testContext.async();
      RestAssured.given()
        .spec(spec)
        .body(snapshots.get(i).withStatus(Snapshot.Status.PARSING_IN_PROGRESS))
        .when()
        .post(SOURCE_STORAGE_SNAPSHOTS_PATH)
        .then()
        .statusCode(HttpStatus.SC_CREATED);
      async.complete();

      async = testContext.async();
      Record record = new Record()
        .withSnapshotId(snapshots.get(i).getJobExecutionId())
        .withRecordType(Record.RecordType.MARC)
        .withRawRecord(rawRecord)
        .withParsedRecord(marcRecord)
        .withMatchedId(matchedId);

      if (i > 0) {
        snapshots.get(i - 1).setStatus(Snapshot.Status.COMMITTED);
        RestAssured.given()
          .spec(spec)
          .body(snapshots.get(i - 1))
          .when()
          .put(SOURCE_STORAGE_SNAPSHOTS_PATH + "/" + snapshots.get(i - 1).getJobExecutionId())
          .then()
          .statusCode(HttpStatus.SC_OK);
      }
      async.complete();

      async = testContext.async();
      Record created = RestAssured.given()
        .spec(spec)
        .body(record)
        .when()
        .post(SOURCE_STORAGE_RECORDS_PATH)
        .body().as(Record.class);

      RestAssured.given()
        .spec(spec)
        .when()
        .get(SOURCE_STORAGE_RECORDS_PATH + "/" + created.getId())
        .then()
        .statusCode(HttpStatus.SC_OK)
        .body("id", is(created.getId()))
        .body("rawRecord.content", is(rawRecord.getContent()))
        .body("matchedId", is(matchedId))
        .body("generation", is(0));
      async.complete();
    }
  }

  @Test
  public void shouldReturnNotFoundIfSnapshotDoesNotExist(TestContext testContext) {
    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .body(snapshot_1.withStatus(Snapshot.Status.PARSING_IN_PROGRESS))
      .when()
      .post(SOURCE_STORAGE_SNAPSHOTS_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);
    async.complete();

    async = testContext.async();
    Record record_1 = new Record()
      .withSnapshotId(snapshot_1.getJobExecutionId())
      .withRecordType(Record.RecordType.MARC)
      .withRawRecord(rawRecord)
      .withParsedRecord(marcRecord)
      .withMatchedId(matchedId);

    RestAssured.given()
      .spec(spec)
      .body(record_1)
      .when()
      .post(SOURCE_STORAGE_RECORDS_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);
    async.complete();

    async = testContext.async();
    Record record_2 = new Record()
      .withSnapshotId(snapshot_2.getJobExecutionId())
      .withRecordType(Record.RecordType.MARC)
      .withRawRecord(rawRecord)
      .withParsedRecord(marcRecord)
      .withMatchedId(matchedId);

    RestAssured.given()
      .spec(spec)
      .body(record_2)
      .when()
      .post(SOURCE_STORAGE_RECORDS_PATH)
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
    async.complete();
  }

  @Test
  public void shouldReturnBadRequestIfProcessingDateIsNull(TestContext testContext) {
    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .body(snapshot_1.withStatus(Snapshot.Status.NEW))
      .when()
      .post(SOURCE_STORAGE_SNAPSHOTS_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);
    async.complete();

    async = testContext.async();
    Record record_1 = new Record()
      .withSnapshotId(snapshot_1.getJobExecutionId())
      .withRecordType(Record.RecordType.MARC)
      .withRawRecord(rawRecord)
      .withParsedRecord(marcRecord)
      .withMatchedId(matchedId);

    RestAssured.given()
      .spec(spec)
      .body(record_1)
      .when()
      .post(SOURCE_STORAGE_RECORDS_PATH)
      .then()
      .statusCode(HttpStatus.SC_BAD_REQUEST);
    async.complete();
  }
}
