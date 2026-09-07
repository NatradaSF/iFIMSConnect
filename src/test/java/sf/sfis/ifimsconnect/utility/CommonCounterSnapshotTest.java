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
 * Characterization (snapshot) test for the common-counter branch of the
 * transform — i.e. a DATASET message that carries &lt;pl_desk&gt; counters and
 * no &lt;pl_turn&gt;, which routes through {@code buildCommonCounter()} ->
 * {@code getCounters(isCommon=true)}.
 *
 * <p>See {@link TranformFidsAfttabSnapshotTest} for the snapshot workflow
 * (golden files are created on first run, then compared on subsequent runs).
 */
class CommonCounterSnapshotTest {

	private static final Path SAMPLE = Paths.get("src", "test", "resources", "sample-pl-desk.xml");
	private static final Path SNAPSHOT_DIR = Paths.get("src", "test", "resources", "snapshots");

	private static String sampleXml;
	private static TranformFidsAfttab transformer;
	private static ObjectMapper mapper;

	@BeforeAll
	static void setUp() throws Exception {
		sampleXml = new String(Files.readAllBytes(SAMPLE), StandardCharsets.UTF_8);

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
	@DisplayName("DATASET common counter (adid=D) produces hopo + counter list, matches snapshot")
	void counterDeparture() throws Exception {
		FidsAfttab result = transformer.convertPlTurntoAfftab(sampleXml, "DATASET", "BKK", "D");

		assertThat(result).isNotNull();
		assertThat(result.getHopo()).isEqualTo("BKK");
		assertThat(result.getLstFidsCcatab()).isNotEmpty();

		// ----------------------------------------------------
		// XPath อ่านค่า pl_desk ได้จริง
		// ----------------------------------------------------
		var firstCounter = result.getLstFidsCcatab().get(0);
		assertThat(firstCounter.getFlno())
				.as("Flno must not be empty space due to wrong XPath")
				.isNotBlank();
		assertThat(firstCounter.getCtyp()).isNotNull();
		assertThat(firstCounter.getAct3()).isNotBlank();

		verifySnapshot("counter-departure", result);
	}

	@Test
	@DisplayName("common counter branch returns null for arrival (adid=A)")
	void counterArrivalIsNull() {
		FidsAfttab result = transformer.convertPlTurntoAfftab(sampleXml, "DATASET", "BKK", "A");

		assertThat(result).isNull();
	}

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
}
