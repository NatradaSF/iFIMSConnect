package sf.sfis.ifimsconnect.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import sf.sfis.ifimsconnect.MQWebSphereProducer;
import sf.sfis.ifimsconnect.model.FidsAfttab;
import sf.sfis.ifimsconnect.utility.TranformFidsAfttab;

/**
 * Regression tests for {@link ESBResponseService#getContentBody(String)} — the
 * pure string extraction of the &lt;pl_turn&gt; fragment. Dependencies are not
 * used by this method, so the service is built with nulls.
 */
class ESBResponseServiceTest {

	private TranformFidsAfttab tranformFidsAfttab;
    private ESBResponseService service;

	@BeforeEach
    void setUp() {
        // 1. Mock ตัวแปร TranformFidsAfttab
        tranformFidsAfttab = mock(TranformFidsAfttab.class);

        // 2. Mock พฤติกรรมของ parseFlightNumber ให้ส่งค่ากลับมาตามที่ส่งเข้า (หรือไม่เกิด null)
        when(tranformFidsAfttab.parseFlightNumber(anyString())).thenAnswer(invocation -> {
            Map<String, String> map = new HashMap<>();
            map.put("carrier", "PG");
            map.put("number", "2512");
            return map;
        });
        when(tranformFidsAfttab.toFlnoNonSuffix(any())).thenReturn("PG2512");

        // 3. ส่ง mock เข้าไปที่ Parameter ลำดับที่ 2 (tranformFidsAfttab)
        service = new ESBResponseService(
                null,                 // dateTimeFormatHelper
                tranformFidsAfttab,   // tranformFidsAfttab (ตำแหน่งที่ 2)
                null,                 // fidsAfttabService
                null,                 // fidsCcatabService
                null,                 // fidsGateHistoryService
                null,                 // fidsFinalcallHistoryService
                null,                 // redisController
                null                  // webSphereProducer
        );
    }

	@Test
	@DisplayName("getContentBody extracts the pl_turn fragment inclusive of tags")
	void extractsPlTurnFragment() {
		String xml = "<root>\n<pl_turn>payload</pl_turn>\n</root>";

		assertThat(service.getContentBody(xml)).isEqualTo("<pl_turn>payload</pl_turn>");
	}

	@Test
	@DisplayName("getContentBody strips blank lines inside the fragment")
	void stripsBlankLines() {
		String xml = "<pl_turn>\n   \n<a>1</a>\n</pl_turn>";

		assertThat(service.getContentBody(xml)).isEqualTo("<pl_turn>\n<a>1</a>\n</pl_turn>");
	}

	@Test
	@DisplayName("getContentBody returns null when no pl_turn element is present")
	void returnsNullWhenAbsent() {
		assertThat(service.getContentBody("<root><other/></root>")).isNull();
	}

	@Test
	@DisplayName("convertGatetoEsb (Insert Gate) - GTA1 has value and GTA2 set to space")
	void convertGatetoEsb_InsertGate() {
		// 1. Arrange: Gate ถูกเปลี่ยนจาก HOLD เป็น A3 (Gate Action = insert)
		FidsAfttab fidsAfttab = new FidsAfttab();
		fidsAfttab.setAction("UPDATE");
		fidsAfttab.setGateAction("insert");
		fidsAfttab.setAdid("A");
		fidsAfttab.setFlno("PG136");
		fidsAfttab.setGta1("A3");
		fidsAfttab.setGa1b("20260916083400");
		fidsAfttab.setGa1e("20260916084900");
		
		fidsAfttab.setFieldsNotNull(Arrays.asList("gta1", "ga1b", "ga1e"));

		// 2. Act
		String xmlResult = service.convertGatetoEsb("2026-09-16T07:25:59Z", fidsAfttab);

		// 3. Assert
		assertThat(xmlResult).isNotNull();
		assertThat(xmlResult).contains("<ACTIONTYPE>I</ACTIONTYPE>");
		assertThat(xmlResult).contains("<GATEARR>");
		assertThat(xmlResult).contains("<GTA1>A3</GTA1>");
		assertThat(xmlResult).contains("<GTA2> </GTA2>"); // เคส insert จะต้องถูก set เว้นวรรคใน GTA2
		assertThat(xmlResult).contains("<GA1B>20260916083400</GA1B>");
		assertThat(xmlResult).contains("<GA1E>20260916084900</GA1E>");
		assertThat(xmlResult).contains("<GA2B> </GA2B>").contains("<GA2E> </GA2E>")
		.contains("<GA1X> </GA1X>").contains("<GA1Y> </GA1Y>")
		.contains("<GA2X> </GA2X>").contains("<GA2Y> </GA2Y>");
	}

	@Test
	@DisplayName("convertGatetoEsb (Update Gate) - GTA1 and GTA2 updated normally")
	void convertGatetoEsb_UpdateGate() {
		// 1. Arrange: ปรับปรุง Gate ที่มีอยู่เดิม (Gate Action = update)
		FidsAfttab fidsAfttab = new FidsAfttab();
		fidsAfttab.setAction("UPDATE");
		fidsAfttab.setGateAction("update");
		fidsAfttab.setAdid("A");
		fidsAfttab.setFlno("PG136");
		fidsAfttab.setGta1("A1");
		fidsAfttab.setGa1b("20260916100000");
		fidsAfttab.setGa1e("20260916100000");
		fidsAfttab.setGa1x("20260916100000");
		fidsAfttab.setGa1y("");
		fidsAfttab.setGta2("S101");
		fidsAfttab.setGa2b("20260916090000");
		fidsAfttab.setGa2e("20260916100000");
		fidsAfttab.setGa2x("");
		fidsAfttab.setGa2y("");
		
		fidsAfttab.setFieldsNotNull(Arrays.asList("ga1b", "ga1e", "gta1", "gta2", "gateAction"));

		// 2. Act
		String xmlResult = service.convertGatetoEsb("2026-09-16T07:25:59Z", fidsAfttab);

		// 3. Assert
		assertThat(xmlResult).isNotNull();
		assertThat(xmlResult).contains("<ACTIONTYPE>U</ACTIONTYPE>");
		assertThat(xmlResult).contains("<GTA1>A1</GTA1>");
		assertThat(xmlResult).contains("<GA1B>20260916100000</GA1B>");
		assertThat(xmlResult).contains("<GA1E>20260916100000</GA1E>");
		assertThat(xmlResult).contains("<GTA2>S101</GTA2>");
		assertThat(xmlResult).contains("<GA2B>20260916090000</GA2B>");
		assertThat(xmlResult).contains("<GA2E>20260916100000</GA2E>");
		assertThat(xmlResult).contains("<GA1X>20260916100000</GA1X>").contains("<GA1Y> </GA1Y>")
		.contains("<GA2X> </GA2X>").contains("<GA2Y> </GA2Y>");
	}

	@Test
	@DisplayName("convertGatetoEsb (Update Gate) - GTA1 updated normally")
	void convertGatetoEsb_UpdateGate1() {
		// 1. Arrange: ปรับปรุง Gate ที่มีอยู่เดิม (Gate Action = update)
		FidsAfttab fidsAfttab = new FidsAfttab();
		fidsAfttab.setAction("UPDATE");
		fidsAfttab.setGateAction("update");
		fidsAfttab.setAdid("D");
		fidsAfttab.setFlno("PG136");
		fidsAfttab.setGtd1("A3");
		
		fidsAfttab.setFieldsNotNull(Arrays.asList("gtd1"));

		// 2. Act
		String xmlResult = service.convertGatetoEsb("2026-09-16T07:25:59Z", fidsAfttab);

		// 3. Assert
		assertThat(xmlResult).isNotNull();
		assertThat(xmlResult).contains("<ACTIONTYPE>U</ACTIONTYPE>");
		assertThat(xmlResult).contains("<GTD1>A3</GTD1>");
		assertThat(xmlResult).contains("<GTD2> </GTD2>").contains("<GD1X> </GD1X>").contains("<GD1Y> </GD1Y>")
		.contains("<GD2X> </GD2X>").contains("<GD2Y> </GD2Y>");
	}

	@Test
	@DisplayName("convertGatetoEsb (Delete Gate) - GTA1 and GTA2 set to space")
	void convertGatetoEsb_DeleteGate() {
		// 1. Arrange: ลบ Gate เดิมออกเป็น HOLD (Gate Action = delete)
		FidsAfttab fidsAfttab = new FidsAfttab();
		fidsAfttab.setAction("UPDATE");
		fidsAfttab.setGateAction("delete");
		fidsAfttab.setAdid("A");
		fidsAfttab.setFlno("PG136");
		
		fidsAfttab.setFieldsNotNull(Arrays.asList("gateAction"));

		// 2. Act
		String xmlResult = service.convertGatetoEsb("2026-09-16T07:25:59Z", fidsAfttab);

		// 3. Assert
		assertThat(xmlResult).isNotNull();
		assertThat(xmlResult).contains("<ACTIONTYPE>U</ACTIONTYPE>");
		assertThat(xmlResult).contains("<GTA1> </GTA1>"); // ลบ Gate -> ทั้ง GTA1 และ GTA2 ต้องเป็นเว้นวรรค
		assertThat(xmlResult).contains("<GTA2> </GTA2>");
		assertThat(xmlResult).doesNotContain("<GA1X>").doesNotContain("<GA1Y>")
		.doesNotContain("<GA2X>").doesNotContain("<GA2Y>");
	}

	@Test
	@DisplayName("sendGate should not call convertGatetoEsb nor send queue when gateAction is null")
	void sendGate_WhenGateActionIsNull_ShouldNotProcessOrSendQueue() {
		// 1. Arrange: สร้าง Spy เพื่อดักจับการเรียกใช้ Method ภายใน service เอง
		ESBResponseService serviceSpy = org.mockito.Mockito.spy(service);

		FidsAfttab fidsAfttab = new FidsAfttab();
		fidsAfttab.setAction("UPDATE");
		fidsAfttab.setGateAction(null); // จำลองสถานการณ์กรณี XSLT คืนค่าเป็น null (none action)
		fidsAfttab.setAdid("A");
		fidsAfttab.setFlno("PG136");
		fidsAfttab.setHopo("BKK");

		// 2. Act: เรียกใช้ sendGate
		serviceSpy.sendGate("2026-09-16T07:25:59Z", fidsAfttab);

		// 3. Assert: ยืนยันว่า convertGatetoEsb ไม่ถูกเรียกใช้
		verify(serviceSpy, never()).convertGatetoEsb(anyString(), any(FidsAfttab.class));
	}

	@Test
	void convertBelttoEsb_InsertBelt() {
		// 1. Arrange: เตรียมวัตถุ FidsAfttab และกำหนดค่า Belt
		FidsAfttab fidsAfttab = new FidsAfttab();
		fidsAfttab.setAction("UPDATE");             // ป้องกัน NPE\
		fidsAfttab.setBeltAction("insert");             // ป้องกัน NPE\
		fidsAfttab.setFlno("PG2512");
		fidsAfttab.setAdid("A");
		fidsAfttab.setBlt1("B01");                  // กำหนดค่า Belt 1
		fidsAfttab.setBlt2("B02");  
		fidsAfttab.setB1bs("20260821090000");        // กำหนดเวลาเริ่ม Plan
		fidsAfttab.setB1es("20260821090000");        // กำหนดเวลาสิ้นสุด Plan

		// ⚠️ สำคัญ: ต้องระบุชื่อฟิลด์ใน fieldsNotNull เพื่อให้ copyMatchingFields ทำงานได้
		fidsAfttab.setFieldsNotNull(Arrays.asList("blt1", "b1bs"));

		// 2. Act: เรียกใช้ Method
		String xmlResult = service.convertBelttoEsb("2026-08-21T09:00:00Z", fidsAfttab);

		// 3. Assert: เช็กผลลัพธ์
		// 3.1 ผลลัพธ์ต้องไม่เป็น null (แปลว่าผ่านเงื่อนไข hasAnyData และ marshal สำเร็จ)
		assertThat(xmlResult).isNotNull();

		// 3.2 ต้องมี Tag ข้อมูลของ BELT ปรากฏใน XML ผลลัพธ์
		assertThat(xmlResult).contains("<BLT1>B01</BLT1>");
		assertThat(xmlResult).contains("<BLT2>B02</BLT2>");
		assertThat(xmlResult).contains("<B1BS>20260821090000</B1BS>");
		assertThat(xmlResult).contains("<B1ES>20260821090000</B1ES>");
		assertThat(xmlResult).contains("<B1BA> </B1BA>").contains("<B1EA> </B1EA>")
		.contains("<B2BA> </B2BA>").contains("<B2EA> </B2EA>");

		// 3.3 Print ดู XML ออกมาที่ Console เพื่อตรวจสอบด้วยสายตา (Optional)
		System.out.println("Generated XML Result:\n" + xmlResult);
	}

	@Test
	@DisplayName("convertBelttoEsb (Update Belt) - BLT1 and BLT2 updated normally")
	void convertBelttoEsb_UpdateBelt() {
		// 1. Arrange: ปรับปรุง Gate ที่มีอยู่เดิม (Gate Action = update)
		FidsAfttab fidsAfttab = new FidsAfttab();
		fidsAfttab.setAction("UPDATE");
		fidsAfttab.setBeltAction("update");
		fidsAfttab.setAdid("A");
		fidsAfttab.setFlno("PG136");
		fidsAfttab.setBlt1("B01");                  // กำหนดค่า Belt 1
		fidsAfttab.setBlt2("B02");  
		fidsAfttab.setB1bs("20260821090000");        // กำหนดเวลาเริ่ม Plan
		fidsAfttab.setB1es("20260821090000");  
		fidsAfttab.setB2bs("20260916090000");
		fidsAfttab.setB2es("20260916100000");
		fidsAfttab.setB1ba("");
		fidsAfttab.setB1ea("");
		fidsAfttab.setB2ba("");
		fidsAfttab.setB2ea("");
		
		fidsAfttab.setFieldsNotNull(Arrays.asList("b1bs", "b1es", "blt1", "blt2"));

		// 2. Act
		String xmlResult = service.convertBelttoEsb("2026-09-16T07:25:59Z", fidsAfttab);

		// 3. Assert
		assertThat(xmlResult).isNotNull();
		assertThat(xmlResult).contains("<ACTIONTYPE>U</ACTIONTYPE>");
		assertThat(xmlResult).contains("<BLT1>B01</BLT1>");
		assertThat(xmlResult).contains("<B1BS>20260821090000</B1BS>");
		assertThat(xmlResult).contains("<B1ES>20260821090000</B1ES>");
		assertThat(xmlResult).contains("<BLT2>B02</BLT2>");
		assertThat(xmlResult).contains("<B1BA> </B1BA>").contains("<B1EA> </B1EA>")
		.contains("<B2BA> </B2BA>").contains("<B2EA> </B2EA>");
	}

	@Test
	@DisplayName("convertBelttoEsb (Update Belt) - BLT1 updated normally")
	void convertBelttoEsb_UpdateBelt1() {
		// 1. Arrange: ปรับปรุง Belt ที่มีอยู่เดิม (Belt Action = update)
		FidsAfttab fidsAfttab = new FidsAfttab();
		fidsAfttab.setAction("UPDATE");
		fidsAfttab.setBeltAction("update");
		fidsAfttab.setAdid("A");
		fidsAfttab.setFlno("PG136");
		fidsAfttab.setBlt1("B01");  
		fidsAfttab.setB1bs("20260821090000");
		fidsAfttab.setB1es("20260821090000");  
		fidsAfttab.setB1ba("20260916090000");
		fidsAfttab.setB1ea("20260916100000");
		
		fidsAfttab.setFieldsNotNull(Arrays.asList("blt1"));

		// 2. Act
		String xmlResult = service.convertBelttoEsb("2026-09-16T07:25:59Z", fidsAfttab);

		// 3. Assert
		assertThat(xmlResult).isNotNull();
		assertThat(xmlResult).contains("<ACTIONTYPE>U</ACTIONTYPE>");
		assertThat(xmlResult).contains("<BLT1>B01</BLT1>");
		assertThat(xmlResult).contains("<BLT2> </BLT2>");
		assertThat(xmlResult).contains("<B1BS>20260821090000</B1BS>")
		.contains("<B1ES>20260821090000</B1ES>")
		.contains("<B1BA>20260916090000</B1BA>").contains("<B1EA>20260916100000</B1EA>")
		.contains("<B2BS> </B2BS>").contains("<B2ES> </B2ES>")
		.contains("<B2BA> </B2BA>").contains("<B2EA> </B2EA>");
	}

	@Test
	@DisplayName("convertBelttoEsb (Update Belt) - BLT1 and BLT2 set to space")
	void convertBelttoEsb_DeleteBelt() {
		// 1. Arrange: ลบ Gate เดิมออกเป็น HOLD (Gate Action = delete)
		FidsAfttab fidsAfttab = new FidsAfttab();
		fidsAfttab.setAction("UPDATE");
		fidsAfttab.setBeltAction("delete");
		fidsAfttab.setAdid("A");
		fidsAfttab.setFlno("PG136");
		
		fidsAfttab.setFieldsNotNull(Arrays.asList("beltAction"));

		// 2. Act
		String xmlResult = service.convertBelttoEsb("2026-09-16T07:25:59Z", fidsAfttab);

		// 3. Assert
		assertThat(xmlResult).isNotNull();
		assertThat(xmlResult).contains("<ACTIONTYPE>U</ACTIONTYPE>");
		assertThat(xmlResult).contains("<BLT1> </BLT1>"); // ลบ Belt -> ทั้ง BLT1 และ BLT2 ต้องเป็นเว้นวรรค
		assertThat(xmlResult).contains("<BLT2> </BLT2>");
		assertThat(xmlResult).doesNotContain("<B1BA>").doesNotContain("<B1EA>")
		.doesNotContain("<B2BA>").doesNotContain("<B2EA>");
	}

	@Test
	@DisplayName("sendBelt should not call convertBelttoEsb nor send queue when gateAction is null")
	void sendBelt_WhenBeltActionIsNull_ShouldNotProcessOrSendQueue() {
		// 1. Arrange: สร้าง Spy เพื่อดักจับการเรียกใช้ Method ภายใน service เอง
		ESBResponseService serviceSpy = org.mockito.Mockito.spy(service);

		FidsAfttab fidsAfttab = new FidsAfttab();
		fidsAfttab.setAction("UPDATE");
		fidsAfttab.setBeltAction(null); // จำลองสถานการณ์กรณี XSLT คืนค่าเป็น null (none action)
		fidsAfttab.setAdid("A");
		fidsAfttab.setFlno("PG136");
		fidsAfttab.setHopo("BKK");

		// 2. Act: เรียกใช้ sendGate
		serviceSpy.sendBelt("2026-09-16T07:25:59Z", fidsAfttab);

		// 3. Assert: ยืนยันว่า convertGatetoEsb ไม่ถูกเรียกใช้
		verify(serviceSpy, never()).convertBelttoEsb(anyString(), any(FidsAfttab.class));
	}

	@Test
	@DisplayName("convertPositiontoEsb (Insert Position) - PSTA1 has value and PSTA2 set to space")
	void convertPositiontoEsb_InsertPosition() {
		// 1. Arrange: Position ถูกเพิ่มเข้ามาใหม่ (Position Action = insert)
		FidsAfttab fidsAfttab = new FidsAfttab();
		fidsAfttab.setAction("UPDATE");
		fidsAfttab.setPositionAction("insert");
		fidsAfttab.setAdid("A");
		fidsAfttab.setFlno("PG136");
		fidsAfttab.setPsta("101");
		fidsAfttab.setPaba("20260916083400");
		fidsAfttab.setPaea("20260916084900");
		
		fidsAfttab.setFieldsNotNull(Arrays.asList("psta1", "paba", "paea"));

		// 2. Act
		String xmlResult = service.convertAcpositiontoEsb("2026-09-16T07:25:59Z", fidsAfttab);

		// 3. Assert
		assertThat(xmlResult).isNotNull();
		assertThat(xmlResult).contains("<ACTIONTYPE>I</ACTIONTYPE>");
		assertThat(xmlResult).contains("<ACPOSITIONARR>");
		assertThat(xmlResult).contains("<PSTA>101</PSTA>");
		assertThat(xmlResult).contains("<PABA>20260916083400</PABA>");
		assertThat(xmlResult).contains("<PAEA>20260916084900</PAEA>");
		assertThat(xmlResult).contains("<PABS> </PABS>");
		assertThat(xmlResult).contains("<PAES> </PAES>");
	}

	@Test
	@DisplayName("convertAcpositiontoEsb (Update Position Arrival) - PSTA and time fields updated normally")
	void convertAcpositiontoEsb_UpdatePosition_Arrival() {
		// 1. Arrange: ปรับปรุง Position ขาเข้า (Position Action = update, ADID = A)
		FidsAfttab fidsAfttab = new FidsAfttab();
		fidsAfttab.setAction("UPDATE");
		fidsAfttab.setPositionAction("update");
		fidsAfttab.setAdid("A");
		fidsAfttab.setFlno("PG136");
		fidsAfttab.setPsta("101");
		fidsAfttab.setPaba("20260916100000");
		fidsAfttab.setPaea("20260916103000");
		fidsAfttab.setPabs("20260916095500");
		fidsAfttab.setPaes("20260916102500");
		
		fidsAfttab.setFieldsNotNull(Arrays.asList("psta", "paba", "paea", "positionAction"));

		// 2. Act
		String xmlResult = service.convertAcpositiontoEsb("2026-09-16T07:25:59Z", fidsAfttab);

		// 3. Assert
		assertThat(xmlResult).isNotNull();
		assertThat(xmlResult).contains("<ACTIONTYPE>U</ACTIONTYPE>");
		assertThat(xmlResult).contains("<ACPOSITIONARR>");
		assertThat(xmlResult).contains("<PSTA>101</PSTA>");
		assertThat(xmlResult).contains("<PABA>20260916100000</PABA>");
		assertThat(xmlResult).contains("<PAEA>20260916103000</PAEA>");
		assertThat(xmlResult).contains("<PABS>20260916095500</PABS>");
		assertThat(xmlResult).contains("<PAES>20260916102500</PAES>");
	}

	@Test
	@DisplayName("convertAcpositiontoEsb (Update Position Departure) - PSTD updated normally")
	void convertAcpositiontoEsb_UpdatePosition_Departure() {
		// 1. Arrange: ปรับปรุง Position ขาออก (Position Action = update, ADID = D)
		FidsAfttab fidsAfttab = new FidsAfttab();
		fidsAfttab.setAction("UPDATE");
		fidsAfttab.setPositionAction("update");
		fidsAfttab.setAdid("D");
		fidsAfttab.setFlno("PG136");
		fidsAfttab.setPstd("201");
		fidsAfttab.setPdba("20260916110000");
		fidsAfttab.setPdea("20260916113000");
		
		fidsAfttab.setFieldsNotNull(Arrays.asList("pstd", "pdba", "pdea"));

		// 2. Act
		String xmlResult = service.convertAcpositiontoEsb("2026-09-16T07:25:59Z", fidsAfttab);

		// 3. Assert
		assertThat(xmlResult).isNotNull();
		assertThat(xmlResult).contains("<ACTIONTYPE>U</ACTIONTYPE>");
		assertThat(xmlResult).contains("<ACPOSITIONDEP>");
		assertThat(xmlResult).contains("<PSTD>201</PSTD>");
		assertThat(xmlResult).contains("<PDBA>20260916110000</PDBA>");
		assertThat(xmlResult).contains("<PDEA>20260916113000</PDEA>");
		assertThat(xmlResult).contains("<PDBS> </PDBS>");
		assertThat(xmlResult).contains("<PDES> </PDES>");
	}

	@Test
	@DisplayName("convertAcpositiontoEsb (Delete Position) - PSTA set to space or HOLD")
	void convertAcpositiontoEsb_DeletePosition() {
		// 1. Arrange: ลบ Position เดิมออก (Position Action = delete)
		FidsAfttab fidsAfttab = new FidsAfttab();
		fidsAfttab.setAction("UPDATE");
		fidsAfttab.setPositionAction("delete");
		fidsAfttab.setAdid("A");
		fidsAfttab.setFlno("PG136");
		fidsAfttab.setPsta(" ");
		
		fidsAfttab.setFieldsNotNull(Arrays.asList("psta","positionAction"));

		// 2. Act
		String xmlResult = service.convertAcpositiontoEsb("2026-09-16T07:25:59Z", fidsAfttab);

		// 3. Assert
		assertThat(xmlResult).isNotNull();
		assertThat(xmlResult).contains("<ACTIONTYPE>U</ACTIONTYPE>");
		assertThat(xmlResult).contains("<PSTA> </PSTA>"); // ลบ Position -> Tag PSTA ต้องเป็นเว้นวรรค
		assertThat(xmlResult).doesNotContain("<PABA>").doesNotContain("<PAEA>")
		.doesNotContain("<PABS>").doesNotContain("<PAES>");
	}

	/* @Test
	@DisplayName("convertTowingtoEsb fills INFOBJ_GENERIC, empty CONCAT/TOWINGS")
	void buildsTowing() {
		FidsAfttab f = new FidsAfttab();
		f.setAction("update");
		f.setHopo("BKK");
		f.setAdid("A");
		f.setUrno(new java.math.BigDecimal("2009910692"));
		f.setFlno("QF 023");
		f.setStoa("20260511094000");
		f.setCsgn("QFA23");
		f.setRtyp("S");

		String xml = service.convertTowingtoEsb("20260511170019", f);

		assertThat(xml).isNotNull();
		assertThat(xml).contains("<MESSAGETYPE>UFISTOWUD</MESSAGETYPE>")
				.contains("<MESSAGEORIGIN>AOS</MESSAGEORIGIN>")
				.contains("<FLNO>QF 023</FLNO>")
				.contains("<RTYP>S</RTYP>");
		// payload อยู่ใต้ CONCAT/TOWINGS — field ว่าง
		assertThat(xml).contains("<CONCAT>").contains("<TOWINGS>")
				.contains("<TOID></TOID>").contains("<TWTP></TWTP>").contains("<SCHE></SCHE>").contains("<SCHS></SCHS>");
	}

	@Test
	@DisplayName("convertVdgstoEsb (ADID=D) uses ACTIONTYPE=U + empty VDGSDEP")
	void buildsVdgsDeparture() {
		FidsAfttab f = new FidsAfttab();
		f.setAction("update");
		f.setHopo("BKK");
		f.setAdid("D");
		f.setUrno(new java.math.BigDecimal("2009913942"));
		f.setFlno("BR 6004");
		f.setStod("20260511174500");
		f.setCsgn("EVA6004");
		f.setRtyp("J");

		String xml = service.convertVdgstoEsb("20260511170002", f);

		assertThat(xml).isNotNull();
		assertThat(xml).contains("<MESSAGETYPE>UFISVDGUD</MESSAGETYPE>")
				.contains("<MESSAGEORIGIN>AOS</MESSAGEORIGIN>")
				.contains("<ACTIONTYPE>U</ACTIONTYPE>")   // action=update → U (ปกติเหมือนคิวอื่น)
				.contains("<ADID>D</ADID>")
				.contains("<FLNO>BR 6004</FLNO>");
		// ADID=D → VDGSDEP + field ว่าง
		assertThat(xml).contains("<INFOBJ_VDGS>").contains("<VDGSDEP>")
				.doesNotContain("<VDGSARR>");
		assertThat(xml).contains("<PSTD></PSTD>").contains("<ACT5></ACT5>")
				.contains("<FTYP></FTYP>").contains("<TIFD></TIFD>");
	} */

	@Test
	@DisplayName("convertSitatoEsb builds UFISSITA with minimal header + BULKDATA/SITA content")
	void buildsSita() {
		String xml = service.convertSitatoEsb("20260621012747", "BKK", "1351466.snd", "=PRIORITY\nQU\n=TEXT\nDOMESTIC");

		assertThat(xml).isNotNull();
		assertThat(xml).contains("<MESSAGETYPE>UFISSITA</MESSAGETYPE>")
				.contains("<MESSAGEORIGIN>AOS</MESSAGEORIGIN>")
				.contains("<ACTIONTYPE>I</ACTIONTYPE>")
				.contains("<HOPO>BKK</HOPO>");
		// SITA ไม่ผูกเที่ยวบิน → ไม่มี field flight ใน generic
		assertThat(xml).doesNotContain("<ADID>").doesNotContain("<FLNO>").doesNotContain("<URNO>");
		// payload อยู่ใต้ BULKDATA/SITA พร้อม FILE_NAME + CONTENT จริง
		assertThat(xml).contains("<BULKDATA>").contains("<SITA>")
				.contains("<FILE_NAME>1351466.snd</FILE_NAME>")
				.contains("DOMESTIC");
	}

	@Test
	@DisplayName("sendFileReady forwards XML verbatim to UFIS_TRIGGER_OUT (passthrough, no build)")
	void fileReadyPassthrough() {
		MQWebSphereProducer producer = mock(MQWebSphereProducer.class);
		ESBResponseService svc = new ESBResponseService(null, null, null, null, null, null, null, producer);
		ReflectionTestUtils.setField(svc, "webSphereEnabled", true);

		String xml = "<MSG><BATCHFILE_OUT><INFOBJ_FILE_OUT>"
				+ "<FILENAME>/FILES/HDYATC_DLYFLT_20260525100000.txt</FILENAME>"
				+ "</INFOBJ_FILE_OUT></BATCHFILE_OUT></MSG>";
		svc.sendFileReady("BKK", xml);

		// ลงคิว UFIS_TRIGGER_OUT_BKK (BKK → machine1) แบบ verbatim ไม่แตะ payload
		verify(producer).sendToMachine1("UFIS_TRIGGER_OUT_BKK", "BKK", xml);
	}
}
