package sf.sfis.ifimsconnect.utility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import sf.sfis.ifimsconnect.model.FidsAfttab;
import sf.sfis.ifimsconnect.repository.FidsAirportRepository;

/**
 * Characterization (snapshot) tests for the end-to-end XML -> FidsAfttab
 * transform — this is the broad regression guard. It runs the real XSL
 * (src/main/resources/fids_afttab.xsl) plus every applyXxx() derivation against
 * a real sample message, serialises the result to JSON, and compares it against
 * a committed "golden" snapshot.
 *
 * <p>The transform is deterministic (UTC / Asia/Bangkok hardcoded, no use of the
 * current time), so the output is stable across runs and machines.
 *
 * <p>FIRST RUN: if a golden file under src/test/resources/snapshots/ is missing,
 * the test writes it and passes — review the generated JSON and commit it.
 * SUBSEQUENT RUNS: any difference fails the test. If the change is intentional,
 * delete the affected snapshot file and re-run to regenerate, then commit.
 *
 * <p>Run from the project root (Maven default working directory) so the relative
 * XSL path resolves.
 */
class TranformFidsAfttabSnapshotTest {

	private static final Path SAMPLE = Paths.get("src", "test", "resources", "sample-pl-turn.xml");
	private static final Path SAMPLE_D_COUNTER = Paths.get("src", "test", "resources", "delete-D-Counter-pl-turn.xml");
	private static final Path SAMPLE_C_COUNTER = Paths.get("src", "test", "resources", "delete-C-Counter-pl-turn.xml");
	private static final Path SNAPSHOT_DIR = Paths.get("src", "test", "resources", "snapshots");

	private static String sampleXml;
	private static String counterDedicatedXml;
	private static String counterCommonXml;
	private static TranformFidsAfttab transformer;
	private static ObjectMapper mapper;

	@BeforeAll
	static void setUp() throws Exception {
		sampleXml = new String(Files.readAllBytes(SAMPLE), StandardCharsets.UTF_8);
		counterDedicatedXml = new String(Files.readAllBytes(SAMPLE_D_COUNTER), StandardCharsets.UTF_8);
		counterCommonXml = new String(Files.readAllBytes(SAMPLE_C_COUNTER), StandardCharsets.UTF_8);

		// Airport lookup is mocked to "not found" so org4/des4 resolve to "" deterministically.
		FidsAirportRepository airportRepo = mock(FidsAirportRepository.class);
		when(airportRepo.findById(anyString())).thenReturn(Optional.empty());
		transformer = new TranformFidsAfttab(new DateTimeFormatHelper(), airportRepo);

		mapper = new ObjectMapper();
		mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker()
				.withFieldVisibility(Visibility.ANY)
				.withGetterVisibility(Visibility.NONE)
				.withIsGetterVisibility(Visibility.NONE)
				.withSetterVisibility(Visibility.NONE));
		mapper.setSerializationInclusion(Include.NON_NULL);
		mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
	}

	@Test
	@DisplayName("DATASET arrival transform matches snapshot")
	void datasetArrival() throws Exception {
		FidsAfttab result = transformer.convertPlTurntoAfftab(sampleXml, "DATASET", "BKK", "A");

		assertThat(result).isNotNull();
		assertThat(result.getHopo()).isEqualTo("BKK");
		assertThat(result.getAdid()).isEqualTo("A");
		assertThat(result.getRtyp()).isEqualTo("J"); // sample has both arrival and departure

		verifySnapshot("dataset-arrival", result);
	}

	@Test
	@DisplayName("DATASET departure transform matches snapshot")
	void datasetDeparture() throws Exception {
		FidsAfttab result = transformer.convertPlTurntoAfftab(sampleXml, "DATASET", "BKK", "D");

		assertThat(result).isNotNull();
		assertThat(result.getHopo()).isEqualTo("BKK");
		assertThat(result.getAdid()).isEqualTo("D");
		assertThat(result.getRtyp()).isEqualTo("J");

		verifySnapshot("dataset-departure", result);
	}

	@Test
	@DisplayName("UPDATE arrival transform matches snapshot (action-filtered + fieldsNotNull)")
	void updateArrival() throws Exception {
		FidsAfttab result = transformer.convertPlTurntoAfftab(sampleXml, "UPDATE", "BKK", "A");

		verifySnapshot("update-arrival", result);
	}

	@Test
	@DisplayName("UPDATE departure transform matches snapshot (action-filtered + fieldsNotNull)")
	void updateDeparture() throws Exception {
		FidsAfttab result = transformer.convertPlTurntoAfftab(sampleXml, "UPDATE", "BKK", "D");

		verifySnapshot("update-departure", result);
	}

	/**
	 * Compares the serialised transform output against a committed golden file,
	 * creating it on first run.
	 */
	private void verifySnapshot(String name, FidsAfttab result) throws Exception {
		String actual = (result == null) ? "null" : mapper.writeValueAsString(result);

		Files.createDirectories(SNAPSHOT_DIR);
		Path golden = SNAPSHOT_DIR.resolve(name + ".json");

		if (!Files.exists(golden)) {
			Files.write(golden, actual.getBytes(StandardCharsets.UTF_8));
			System.out.println("[snapshot] created " + golden.toAbsolutePath()
					+ " — review and commit it, then re-run.");
			return;
		}

		String expected = new String(Files.readAllBytes(golden), StandardCharsets.UTF_8);
		assertThat(actual)
				.as("Snapshot '%s' changed: transform output differs from the committed golden file (%s). "
						+ "If this change is intentional, delete that file and re-run to regenerate.", name, golden)
				.isEqualTo(expected);
	}

	@Test
	@DisplayName("Test FTYP calculation for UPDATE arrival")
	void testFtypUpdateArrival() throws Exception {
		FidsAfttab result = transformer.convertPlTurntoAfftab(sampleXml, "UPDATE", "BKK", "A");

		assertThat(result).isNotNull();
		
		// 🔍 1. เช็กว่า XML Parse เข้าไปใน REMP และ REM1 ถูกหรือไม่
		assertThat(result.getRemp()).isEqualTo("AIR");
		assertThat(result.getRem1()).isEqualTo("");
		
		// 🔍 2. เช็กว่า FTYP คำนวณออกมาได้ Z ตาม Business Logic หรือไม่
		assertThat(result.getFtyp()).isEqualTo("O");

		if (result.getFieldsNotNull() != null) {
			assertThat(result.getFieldsNotNull()).doesNotContain("remp");
		}
	}

	@Test
	@DisplayName("DELETE counter transform extracts action='delete' and reads ctyp from @old attribute")
	void deleteDedicatedCounterTransform() throws Exception {
		FidsAfttab result = transformer.convertPlTurntoAfftab(counterDedicatedXml, "UPDATE", "BKK", "D");

		// Assert ตรวจสอบผลลัพธ์
		assertThat(result).isNotNull();
		assertThat(result.getLstFidsCcatab()).hasSize(1);

		sf.sfis.ifimsconnect.model.FidsCcatab ccatab = result.getLstFidsCcatab().get(0);

		// เช็กว่า action ถูกอ่านมาเป็น "delete"
		assertThat(ccatab.getAction()).isEqualToIgnoringCase("delete");
    
		// เช็กว่าอ่าน counter "H20" ได้ถูกต้อง
		assertThat(ccatab.getFlnu()).isEqualTo("1005039543");
		assertThat(ccatab.getCkic()).isEqualTo("H20");
		assertThat(ccatab.getUrno()).isEqualTo("1007787242");

		// เช็กว่าอ่าน @old="R" แล้วถูก Map ให้เป็น ctyp = "D"
		assertThat(ccatab.getCtyp()).isEqualTo("D");
	}

	@Test
	@DisplayName("COMMON counter transform extracts common counters and maps ctyp='C'")
	void deleteCommonCounterTransform() throws Exception {
		FidsAfttab result = transformer.convertPlTurntoAfftab(counterCommonXml, "UPDATE", "BKK", "D");

		// Assert - ตรวจสอบผลลัพธ์
		assertThat(result).isNotNull();
		// A01,A02,A03,A04,A05,B01,B02,B03,B04,B05,H01,H02,H03,H04,H05
		assertThat(result.getLstFidsCcatab()).hasSize(15);

		sf.sfis.ifimsconnect.model.FidsCcatab ccatab1 = result.getLstFidsCcatab().get(0);
		sf.sfis.ifimsconnect.model.FidsCcatab ccatab2 = result.getLstFidsCcatab().get(1);

		// เช็กว่ารายการแรกอ่านค่า CKIC ถูกต้อง
		assertThat(ccatab1.getCkic()).isEqualTo("A01");
		// เช็กว่า Common Counter ถูกกำหนด ctyp = "C"
		assertThat(ccatab1.getCtyp()).isEqualTo("C");
		assertThat(ccatab2.getUrno()).isEqualTo("1007788263");
		// เช็ก action
		assertThat(ccatab1.getAction()).isEqualToIgnoringCase("update");

		// เช็กรายการที่สอง
		assertThat(ccatab2.getCkic()).isEqualTo("A02");
		assertThat(ccatab2.getCtyp()).isEqualTo("C");
		assertThat(ccatab2.getUrno()).isEqualTo("1007788263");
	}
}
