package sf.sfis.ifimsconnect.utility;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.s9api.Xslt30Transformer;
import net.sf.saxon.s9api.XsltExecutable;
import sf.sfis.ifimsconnect.model.FidsAfttab;
import sf.sfis.ifimsconnect.model.FidsAirport;
import sf.sfis.ifimsconnect.model.FidsCcatab;
import sf.sfis.ifimsconnect.repository.FidsAirportRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class TranformFidsAfttab {

	// Loaded from the classpath (packaged inside the jar/war) — NOT a filesystem
	// path,
	// so it resolves the same in dev and on the deployed server.
	private static final String XSL_RESOURCE = "/fids_afttab.xsl";
	private static final Pattern ISO_8601_Z = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
	private static final DateTimeFormatter YMD_HMS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	// Cached, thread-safe Saxon/Jackson objects. Compiling the XSL and building the
	// XmlMapper are expensive and the stylesheet never changes, so do it once
	// instead
	// of per message. (The per-call Serializer and load30() transformer are not
	// thread-safe and are still created on each invocation.)
	private static final Processor SAXON_PROCESSOR = new Processor(false);
	private static volatile XsltExecutable xsltExecutable;
	private static final XmlMapper XML_MAPPER = buildXmlMapper();

	private final DateTimeFormatHelper dateTimeFormatHelper;
	private final FidsAirportRepository fidsAirportRepository;

	// ─── ENTRY POINT ───────────────────────────────────────────────────────
	public FidsAfttab convertPlTurntoAfftab(String xmlString, String actionType, String hopo, String adid) {
		return convertPlTurntoAfftab(xmlString, actionType, hopo, adid, null);
	}

	public FidsAfttab convertPlTurntoAfftab(String xmlString, String actionType, String hopo, String adid,
			String originator) {
		try {
			Document doc = parseDocument(xmlString);
			XPath xpath = XPathFactory.newInstance().newXPath();

			String plTurn = (String) xpath.evaluate("//pl_turn", doc, XPathConstants.STRING);
			boolean hasArrival = !((String) xpath.evaluate("//pa_idseq", doc, XPathConstants.STRING)).isEmpty();
			boolean hasDeparture = !((String) xpath.evaluate("//pd_idseq", doc, XPathConstants.STRING)).isEmpty();

			if (plTurn.isEmpty()) {
				return buildCommonCounter(xmlString, actionType, hopo, adid, originator);
			}
			return buildFlight(xmlString, doc, xpath, actionType, hopo, adid, hasArrival, hasDeparture, originator);
		} catch (Exception e) {
			log.error("convertPlTurntoAfftab error: ", e);
			return null;
		}
	}

	// ─── COMMON COUNTER (XML without pl_turn) ──────────────────────────────
	private FidsAfttab buildCommonCounter(String xmlString, String actionType, String hopo, String adid, String originator) {
		if (!"D".equalsIgnoreCase(adid))
			return null;

		try {
			// 1. ส่ง XML เข้า XSLT เพื่อ Map ลง DTO โดยตรง
			FidsAfttab f = transformUsingSaxon(xmlString, actionType, adid, originator);
			if (f == null) {
				return null;
			}

			f.setHopo(hopo);
			f.setAdid(adid);

			// 2. จัดการ Format ฟิลด์เพิ่มเติมและกรอง HOLD ออกจาก Counter List
			if (f.getLstFidsCcatab() != null && !f.getLstFidsCcatab().isEmpty()) {
				f.setLstFidsCcatab(getCountersCommon(f.getLstFidsCcatab()));
			}

			return f;
		} catch (Exception e) {
			log.error("buildCommonCounter error: ", e);
			return null;
		}
		/* FidsAfttab f = new FidsAfttab();
		f.setHopo(hopo);
		NodeList counterList = doc.getElementsByTagName("pl_desk");
		if (counterList.getLength() > 0) {
			//f.setLstFidsCcatab(getCounters(f.getLstFidsCcatab(), f.getCounter(), f.getFlno(), actionType));
			f.setLstFidsCcatab(getCountersCommon((Element) counterList.item(0), true));
		}
		return f; */
	}

	// ─── FLIGHT MODE ───────────────────────────────────────────────────────
	private FidsAfttab buildFlight(String xmlString, Document doc, XPath xpath,
			String actionType, String hopo, String adid,
			boolean hasArrival, boolean hasDeparture, String originator) throws Exception {
		boolean isArrival = "A".equalsIgnoreCase(adid);
		if (isArrival && !hasArrival)
			return null;
		if (!isArrival && !hasDeparture)
			return null;

		Element flightElement = isArrival
				? (Element) doc.getElementsByTagName("pl_arrival").item(0)
				: (Element) doc.getElementsByTagName("pl_departure").item(0);
		String action = flightElement.getAttribute("action");
		log.info("Flight action (" + adid + "): " + action);

		if (!"DATASET".equalsIgnoreCase(actionType)
				&& !"update".equalsIgnoreCase(action) && !"insert".equalsIgnoreCase(action)) {
			return null;
		}

		// 1. XSL transform → action-filtered path mappings deserialised into
		// FidsAfttab.
		String transformedXml = transformXmlUsingSaxon(xmlString, actionType, adid, originator);
		FidsAfttab f = transformUsingSaxon(xmlString, actionType, adid, originator);
		if (f == null)
			return null;
		f.setAdid(adid);
		f.setAction(action);

		// 2. fieldsNotNull — captured before unconditional fixed paths so that
		// URNO/RKEY/SIBT/SOBT/FLNO/CSGN/FLTI/ALC2/ALC3 are only tracked when
		// they were set via the action-filtered XSL pass (matches legacy behaviour).
		if ("UPDATE".equalsIgnoreCase(actionType) || "INSERT".equalsIgnoreCase(actionType)) {
			try {
				// log.info("--- transformedXml Output ---");
				// log.info(transformedXml);
				// 1. แปลง XML String เป็น Document
				DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
				dbf.setNamespaceAware(false);
				Document transformedDoc = dbf.newDocumentBuilder().parse(
						new ByteArrayInputStream(transformedXml.getBytes(StandardCharsets.UTF_8)));

				// 2. ดึง Node ทั้งหมดที่ลูกของมันมี attribute action="UPDATE"
				Set<String> updateTagNames = new HashSet<>();

				// เก็บรายชื่อ Tag Name ที่มีการ UPDATE จริงๆ ไว้ใน Set
				NodeList allElements = transformedDoc.getElementsByTagName("*");
				for (int i = 0; i < allElements.getLength(); i++) {
					Element elem = (Element) allElements.item(i);
					String actionAttr = elem.getAttribute("action");

					// เช็กว่ามี attribute action เป็น update หรือ insert หรือไม่
					// (ไม่สนตัวพิมพ์เล็ก-ใหญ่)
					if ("update".equalsIgnoreCase(actionAttr.trim()) || "insert".equalsIgnoreCase(actionAttr.trim())) {
						String nodeName = elem.getNodeName().toLowerCase();

						// กรณีที่ 1: แท็กชื่อ <field action="update"> -> ให้เอาชื่อแท็กแม่ (เช่น
						// <csgn>)
						if ("field".equals(nodeName)) {
							Node parent = elem.getParentNode();
							if (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
								updateTagNames.add(parent.getNodeName().toLowerCase());
								// log.info("Found updated field (from child <field>): " +
								// parent.getNodeName().toLowerCase());
							}
						}
						// กรณีที่ 2: แท็กชื่อ <csgn action="update">
						else if (!"fidsafttab".equals(nodeName) && !"root".equals(nodeName)) {
							updateTagNames.add(nodeName);
							// log.info("Found updated field (direct tag): " + nodeName);
						}
					}
				}
				// log.info("=== Matched Update Nodes Count: " + updateTagNames.size() + "
				// ===");
				// 3. กรองข้อมูลจาก FieldInspector โดยเอาเฉพาะฟิลด์ที่มีอยู่ใน updateTagNames
				List<String> nonNullFields = FieldInspector.getNonNullFields(f);
				List<String> actualUpdatedFields = new ArrayList<>();

				for (String fieldName : nonNullFields) {
					// เช็คว่าฟิลด์นั้นมี tag UPDATE ใน XML หรือไม่
					// (เปรียบเทียบแบบไม่สนตัวพิมพ์เล็ก-ใหญ่)
					if (updateTagNames.contains(fieldName.toLowerCase())) {
						actualUpdatedFields.add(fieldName);
					}
				}

				// 4. บันทึกรายชื่อฟิลด์ที่โดนอัปเดตจริงกลับเข้าไป
				f.setFieldsNotNull(actualUpdatedFields);
				log.info("Fields " + (isArrival ? "arrival" : "departure") + " updated : " + f.getFieldsNotNull());

			} catch (Exception e) {
				log.error("Error filtering updated fields", e);
			}
		}

		// 3. Derived time fields (EIBT/ETAI/ETOA/LAND/AIRB/AXIT/AXOT/ONBL/OFBL/REMP)
		// applyDerivedTimes(f); ทำใน XSL

		// 4. VIA from routing
		// applyVial(f, xpath, doc, flightElement, hopo, isArrival, actionType);
		applyVial(f, hopo, isArrival, actionType);
		applyFtyp(f);
		applyFlightNumber(f);
		// applyTrkn(f); ทำใน XSL

		// 7. Indexed nested structures
		// applyBeltDetails(f, flightElement, isArrival);

		// ต้องดูว่ายังจะใส่ค่าทับ XSL อยู่มั้ย
		// f.setB1ba(f.getAibt());

		// applyGateDetails(f, flightElement, isArrival);
		// applyDelayReasons(f, flightElement);

		if (!isArrival) {
			f.setLstFidsCcatab(getCounters(f.getLstFidsCcatab(), f.getCounter(), f.getFlno(), actionType));
		}

		// 8. Shared derivations (AURN, BAGS, DCD2, STOA, STOD, FLDA, DTD2, DOOA, DOOD)
		applySharedDerivations(f, isArrival);

		// 9. Airport lookup
		applyAirportLookup(f, hopo, isArrival);

		// 10. Outside per-adid: HOPO, HOLD→empty, RTYP, MTOW ceiling
		f.setHopo(hopo);
		FieldInspector.replaceHoldWithEmpty(f);
		if (hasArrival && hasDeparture) {
			f.setRtyp("J");
		} else if (hasArrival || hasDeparture) {
			f.setRtyp("S");
		}
		// applyMtow(f); ทำใน XSL

		return f;
	}

	// ─── XSL TRANSFORMATION ────────────────────────────────────────────────
	public static String transformXmlUsingSaxon(String xmlString, String actionType, String adid, String originator)
			throws Exception {
		StringWriter sw = new StringWriter();
		Serializer out = SAXON_PROCESSOR.newSerializer(sw);
		out.setOutputProperty(Serializer.Property.METHOD, "xml");
		out.setOutputProperty(Serializer.Property.INDENT, "yes");
		out.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes");

		Xslt30Transformer trans = xsltExecutable().load30();
		Map<QName, XdmValue> params = new HashMap<>();
		params.put(new QName("syncMode"), new XdmAtomicValue(actionType));
		params.put(new QName("adidMode"), new XdmAtomicValue(adid));
		params.put(new QName("originator"), new XdmAtomicValue(originator != null ? originator : ""));
		trans.setStylesheetParameters(params);
		trans.transform(new StreamSource(new StringReader(xmlString)), out);

		return sw.toString();
	}

	public static FidsAfttab transformUsingSaxon(String xmlString, String actionType, String adid, String originator)
			throws Exception {
		String transformedXml = transformXmlUsingSaxon(xmlString, actionType, adid, originator);
		// ลบ attribute action="..." ออกก่อนส่งให้ Jackson
		String removeActionXml = transformedXml.replaceAll("(?i)\\s+action=\"[^\"]*\"", "");
		return XML_MAPPER.readValue(removeActionXml, FidsAfttab.class);
	}

	/**
	 * Compiles the stylesheet once (lazily) and reuses the immutable executable.
	 */
	private static XsltExecutable xsltExecutable() throws Exception {
		XsltExecutable exec = xsltExecutable;
		if (exec == null) {
			synchronized (TranformFidsAfttab.class) {
				exec = xsltExecutable;
				if (exec == null) {
					try (InputStream is = TranformFidsAfttab.class.getResourceAsStream(XSL_RESOURCE)) {
						if (is == null) {
							throw new IllegalStateException("XSL resource not found on classpath: " + XSL_RESOURCE);
						}
						exec = SAXON_PROCESSOR.newXsltCompiler().compile(new StreamSource(is));
					}
					xsltExecutable = exec;
				}
			}
		}
		return exec;
	}

	/**
	 * Builds the date-aware XmlMapper once; ObjectMapper is thread-safe after
	 * setup.
	 */
	private static XmlMapper buildXmlMapper() {
		XmlMapper xmlMapper = new XmlMapper();
		xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		SimpleModule module = new SimpleModule();
		module.addDeserializer(String.class, new JsonDeserializer<String>() {
			@Override
			public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
				return convertDateStringIfNeeded(p.getText());
			}
		});
		xmlMapper.registerModule(module);
		return xmlMapper;
	}

	// ─── UNCONDITIONAL FIXED IDENTITY FIELDS ───────────────────────────────
	/**
	 * Sets URNO/RKEY/SIBT/SOBT/FLNO/CSGN/FLTI/ALC2/ALC3 without applying the
	 * descendant @action filter. These identity fields are required even when
	 * an UPDATE message did not flag them as insert/update, so downstream code
	 * (e.g. lookups by URNO, display formatting) keeps working.
	 */
	/*
	 * private void applyFixedPaths(FidsAfttab f, Document doc, XPath xpath, boolean
	 * isArrival) {
	 * String idseq = isArrival ? "/pl_arrival/pa_idseq" : "/pl_departure/pd_idseq";
	 * evaluateRawText(doc, xpath, idseq).ifPresent(v -> {
	 * try {
	 * f.setUrno(new BigDecimal(v));
	 * } catch (NumberFormatException e) {
	 * log.error(idseq + " is not BigDecimal.");
	 * }
	 * });
	 * evaluateRawText(doc, xpath, "/pl_turn/pt_idseq").ifPresent(v -> {
	 * try {
	 * f.setRkey(new BigDecimal(v));
	 * } catch (NumberFormatException e) {
	 * log.error("/pl_turn/pt_idseq is not BigDecimal.");
	 * }
	 * });
	 * 
	 * if (isArrival) {
	 * evaluateRawText(doc, xpath, "/pl_arrival/pa_sibt").ifPresent(v ->
	 * f.setSibt(convertDateStringIfNeeded(v)));
	 * evaluateRawText(doc, xpath, "/pl_arrival/pa_flightnumber")
	 * .ifPresent(v -> f.setFlno(convertDateStringIfNeeded(v)));
	 * evaluateRawText(doc, xpath, "/pl_arrival/pa_callsign")
	 * .ifPresent(v -> f.setCsgn(convertDateStringIfNeeded(v)));
	 * evaluateRawText(doc, xpath, "/pl_arrival/pa_rctt_countrytype")
	 * .ifPresent(v -> f.setFlti(convertDateStringIfNeeded(v)));
	 * evaluateRawText(doc, xpath, "/pl_arrival/pa_ral_airline/ref_airline/ral_2lc")
	 * .ifPresent(v -> f.setAlc2(convertDateStringIfNeeded(v)));
	 * evaluateRawText(doc, xpath, "/pl_arrival/pa_ral_airline/ref_airline/ral_3lc")
	 * .ifPresent(v -> f.setAlc3(convertDateStringIfNeeded(v)));
	 * } else {
	 * evaluateRawText(doc, xpath, "/pl_departure/pd_sobt")
	 * .ifPresent(v -> f.setSobt(convertDateStringIfNeeded(v)));
	 * evaluateRawText(doc, xpath, "/pl_departure/pd_flightnumber")
	 * .ifPresent(v -> f.setFlno(convertDateStringIfNeeded(v)));
	 * evaluateRawText(doc, xpath, "/pl_departure/pd_callsign")
	 * .ifPresent(v -> f.setCsgn(convertDateStringIfNeeded(v)));
	 * evaluateRawText(doc, xpath, "/pl_departure/pd_rctt_countrytype")
	 * .ifPresent(v -> f.setFlti(convertDateStringIfNeeded(v)));
	 * evaluateRawText(doc, xpath,
	 * "/pl_departure/pd_ral_airline/ref_airline/ral_2lc")
	 * .ifPresent(v -> f.setAlc2(convertDateStringIfNeeded(v)));
	 * evaluateRawText(doc, xpath,
	 * "/pl_departure/pd_ral_airline/ref_airline/ral_3lc")
	 * .ifPresent(v -> f.setAlc3(convertDateStringIfNeeded(v)));
	 * }
	 * }
	 */

	/*
	 * private Optional<String> evaluateRawText(Document doc, XPath xpath, String
	 * path) {
	 * try {
	 * String fixed = "string(//" + (path.startsWith("/") ? path.substring(1) :
	 * path);
	 * String textValue = (String) xpath.evaluate(fixed + "/text()[1])", doc,
	 * XPathConstants.STRING);
	 * if (textValue == null)
	 * return Optional.empty();
	 * String trimmed = textValue.trim();
	 * return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
	 * } catch (XPathExpressionException e) {
	 * log.error("XPath error for path: " + path, e);
	 * return Optional.empty();
	 * }
	 * }
	 */

	// ─── DERIVED BUSINESS LOGIC ────────────────────────────────────────────
	private void applyDerivedTimes(FidsAfttab f) {
		/*
		 * if (f.getAldt() != null) {
		 * f.setEibt(f.getAldt());
		 * } else {
		 * f.setRemp(f.getEibt() != null ? "    " : f.getRemp());
		 * }
		 */
	}

	private void applyFlightNumber(FidsAfttab f) {
		String flno = f.getFlno();
		if (flno == null || flno.isEmpty())
			return;

		if (flno != null && !flno.isEmpty()) {
			Map<String, String> parts = parseFlightNumber(flno);
			if (parts.isEmpty()) {
				f.setFlns("");
			} else {
				f.setFlno(toFlno(parts));
				f.setFlns(parts.get("suffix") != null ? parts.get("suffix") : "");
				f.setFltn(parts.get("number"));
			}
			if (f.getCsgn() != null && f.getFlns() != null) {
				//f.setCsgn(f.getCsgn() + f.getFlns().trim());
				f.setCsgn(f.getCsgn());
			}
		}

		String jfno = f.getJfno();
		if (jfno != null && !jfno.isEmpty()) {
			JfnoResult result = processJfno(f.getJfno());
			f.setJcnt(result.count());
			f.setJfno(result.formattedJfno());
		}

		List<String> updatedFields = f.getFieldsNotNull(); // รายชื่อแท็กที่มี action UPDATE/INSERT จริง
		if (updatedFields == null) {
			updatedFields = new ArrayList<>();
			f.setFieldsNotNull(updatedFields);
		}

		if (updatedFields.contains("flno")) {
			updatedFields.add("flns");
			updatedFields.add("fltn");
		}

		if (updatedFields.contains("jfno")) {
			updatedFields.add("jcnt");
		}
	}

	private void applyFtyp(FidsAfttab f) {
		String ftyp = f.getFtyp();
		//ถ้าเป็น Towing FTYP=T 
		if ("T".equalsIgnoreCase(ftyp)) {
			return;
		}

		//ถ้าไม่เป็น Towing เช็ค REMP ถ้าเป็น RFT FTYP=B
		if (f.getRem1() != null) {
			if ("RFT".equalsIgnoreCase(f.getRem1())) {
				f.setFtyp("B");
				return;
			}else if ("RFA".equalsIgnoreCase(f.getRem1())) {
				f.setFtyp("Z");
				return;
			}else if ("DIV".equalsIgnoreCase(f.getRem1()) || "DIVO".equalsIgnoreCase(f.getRem1())) {
				f.setFtyp("D");
				return;
			}else if ("NOOP".equalsIgnoreCase(f.getRem1())) {
				f.setFtyp("N");
				return;
			}
		}

		//ถ้าไม่เป็น return flight,return taxi,cancel flight,divert flight เช็ค REM1
		if (f.getRemp() != null) {
			if ("PLN".equalsIgnoreCase(f.getRemp())) {
				f.setFtyp("S");
				return;
			}else if ("CNL".equalsIgnoreCase(f.getRemp())) {
				f.setFtyp("X");
				return;
			}
		}

		if (f.getRem1() != null || f.getRemp() != null) {
			//IBK,OBK,RDY,FNL,ARR,DEP,GHS
			f.setFtyp("O");
		}
	}

	/* private void applyFtyp(FidsAfttab f, String actionType) {
		List<String> updatedFields = f.getFieldsNotNull(); // รายชื่อแท็กที่มี action UPDATE/INSERT จริง
		if (updatedFields == null) {
			updatedFields = new ArrayList<>();
			f.setFieldsNotNull(updatedFields);
		}

		boolean isDataset = "DATASET".equalsIgnoreCase(actionType);
		String ftyp = f.getFtyp();
		//ถ้าเป็น Towing FTYP=T 
		if ("T".equalsIgnoreCase(ftyp) && (isDataset || updatedFields.contains("ftyp"))) {
			return;
		}

		//ถ้าไม่เป็น Towing เช็ค REMP ถ้าเป็น RFT FTYP=B
		if ((isDataset || updatedFields.contains("remp")) && f.getRemp() != null) {
			if ("RFT".equalsIgnoreCase(f.getRemp())) {
				f.setFtyp("B");
				updatedFields.add("ftyp");
				return;
			}else if ("RFA".equalsIgnoreCase(f.getRemp())) {
				f.setFtyp("Z");
				updatedFields.add("ftyp");
				return;
			}
		}

		//ถ้าไม่เป็น return flight,return taxi,cancel flight,divert flight เช็ค REM1
		if ((isDataset || updatedFields.contains("rem1")) && f.getRem1() != null) {
			if ("PLN".equalsIgnoreCase(f.getRem1())) {
				f.setFtyp("S");
				updatedFields.add("ftyp");
				return;
			}else {
				//IBK,OBK,RDY,FNL,ARR,DEP,GHS
				f.setFtyp("O");
				updatedFields.add("ftyp");
			} 
		}
	} */

	private void applyAirportLookup(FidsAfttab f, String hopo, boolean isArrival) {
		String apc4 = fidsAirportRepository.findById(hopo)
				.map(FidsAirport::getApc4)
				.orElse("");
		if (isArrival) {
			f.setDes3(hopo);
			f.setDes4(apc4);
		} else {
			f.setOrg3(hopo);
			f.setOrg4(apc4);
		}
	}

	/*
	 * private void applyTrkn(FidsAfttab f) {
	 * String trkn = f.getTrkn();
	 * if (trkn != null && !trkn.isEmpty() && trkn.length() > 4) {
	 * f.setTrkn(trkn.substring(2));
	 * }
	 * }
	 */

	/*
	 * private void applyMtow(FidsAfttab f) {
	 * String mtow = f.getMtow();
	 * if (mtow == null || mtow.trim().isEmpty())
	 * return;
	 * try {
	 * int n = Integer.parseInt(mtow.trim());
	 * f.setMtow(Integer.toString((int) Math.ceil(n / 1000.0)));
	 * } catch (NumberFormatException ignored) {
	 * }
	 * }
	 */

	// package-private เพื่อให้ unit test เรียกตรงได้ (โฟกัสเฉพาะ logic VIA
	// ไม่ต้องผ่าน XSL ทั้งชุด)
	void applyVial(FidsAfttab f, String hopo, boolean isArrival, String actionType) {
		String route = f.getRoute();
		List<String> vias = viaAirports(route, hopo, isArrival);
		if (vias.isEmpty())
			return;

		// map iata -> icao จาก pl_routing nodes (ใช้เป็น via4) + ดูว่ามี routing
		// เปลี่ยนไหม
		Map<String, String> iataToIcao = new HashMap<>();
		boolean routingChanged = false;
		if (f.getLstRouting() != null) {
			for (FidsAfttab.Routing item : f.getLstRouting()) {
				if (item.getIata() != null && !item.getIata().isEmpty()) {
					iataToIcao.put(item.getIata(), Optional.ofNullable(item.getIcao()).orElse(""));
				}
				String act = item.getAction();
				if ("update".equalsIgnoreCase(act) || "insert".equalsIgnoreCase(act)) {
					routingChanged = true;
				}
			}
		}

		// เหมือนพฤติกรรมเดิม: UPDATE จะ set VIA เฉพาะตอน routing เปลี่ยน, DATASET set
		// เสมอ
		if (!"DATASET".equalsIgnoreCase(actionType) && !routingChanged)
			return;

		// Vial = getVial ของแต่ละ via ต่อกัน (ตามลำดับใน route ซ้าย→ขวา)
		StringBuilder vial = new StringBuilder();
		for (String via : vias) {
			vial.append(getVial(via, iataToIcao.getOrDefault(via, "")));
		}

		// via3/via4 (ช่องเดียว) = via ที่ติด hopo (arrival = ตัวสุดท้าย, departure =
		// ตัวแรก)
		String adjacent = isArrival ? vias.get(vias.size() - 1) : vias.get(0);
		f.setVia3(adjacent);
		f.setVia4(iataToIcao.getOrDefault(adjacent, ""));
		f.setVial(vial.toString());
		f.setVian(String.valueOf(vias.size()));

		List<String> updatedFields = f.getFieldsNotNull(); // รายชื่อแท็กที่มี action UPDATE/INSERT จริง
		if (updatedFields == null) {
			updatedFields = new ArrayList<>();
			f.setFieldsNotNull(updatedFields);
		}
		updatedFields.add("vial");
		updatedFields.add("vian");
	}
	/*
	 * void applyVial(FidsAfttab f, XPath xpath, Document doc, Element
	 * flightElement,
	 * String hopo, boolean isArrival, String actionType) {
	 * try {
	 * String route = (String) xpath.evaluate("//pt_routingiata3lc", doc,
	 * XPathConstants.STRING);
	 * List<String> vias = viaAirports(route, hopo, isArrival);
	 * if (vias.isEmpty())
	 * return;
	 * 
	 * // map iata -> icao จาก pl_routing nodes (ใช้เป็น via4) + ดูว่ามี routing
	 * // เปลี่ยนไหม
	 * Map<String, String> iataToIcao = new HashMap<>();
	 * boolean routingChanged = false;
	 * NodeList routingNodes = flightElement.getElementsByTagName("pl_routing");
	 * for (int i = 0; i < routingNodes.getLength(); i++) {
	 * Node node = routingNodes.item(i);
	 * if (node == null)
	 * continue;
	 * String iata = xpath.evaluate("prt_rap_refairport/ref_airport/rap_iata3lc",
	 * node);
	 * String icao = xpath.evaluate("prt_rap_refairport/ref_airport/rap_icao4lc",
	 * node);
	 * if (iata != null && !iata.isEmpty()) {
	 * iataToIcao.put(iata, icao == null ? "" : icao);
	 * }
	 * String act =
	 * xpath.evaluate("prt_rap_refairport/ref_airport/rap_iata3lc/@action", node);
	 * if ("update".equalsIgnoreCase(act) || "insert".equalsIgnoreCase(act)) {
	 * routingChanged = true;
	 * }
	 * }
	 * 
	 * // เหมือนพฤติกรรมเดิม: UPDATE จะ set VIA เฉพาะตอน routing เปลี่ยน, DATASET
	 * set
	 * // เสมอ
	 * if (!"DATASET".equalsIgnoreCase(actionType) && !routingChanged)
	 * return;
	 * 
	 * // Vial = getVial ของแต่ละ via ต่อกัน (ตามลำดับใน route ซ้าย→ขวา)
	 * StringBuilder vial = new StringBuilder();
	 * for (String via : vias) {
	 * vial.append(getVial(via, iataToIcao.getOrDefault(via, "")));
	 * }
	 * 
	 * // via3/via4 (ช่องเดียว) = via ที่ติด hopo (arrival = ตัวสุดท้าย, departure =
	 * // ตัวแรก)
	 * String adjacent = isArrival ? vias.get(vias.size() - 1) : vias.get(0);
	 * f.setVia3(adjacent);
	 * f.setVia4(iataToIcao.getOrDefault(adjacent, ""));
	 * f.setVial(vial.toString());
	 * f.setVian(String.valueOf(vias.size()));
	 * } catch (XPathExpressionException e) {
	 * log.error("applyVial error: ", e);
	 * }
	 * }
	 */

	/**
	 * รายชื่อสนามบิน VIA จาก route (คั่นด้วย "-") เทียบกับ hopo:
	 * - Arrival: ฝั่งซ้ายของ hopo ยกเว้นซ้ายสุด → airports[1 .. hopoIdx-1]
	 * - Departure: ฝั่งขวาของ hopo ยกเว้นขวาสุด → airports[hopoIdx+1 .. len-2]
	 * เรียงตามลำดับใน route (ซ้าย→ขวา) คืน list ว่างถ้าไม่พบ hopo หรือไม่มี via.
	 */
	static List<String> viaAirports(String route, String hopo, boolean isArrival) {
		List<String> vias = new ArrayList<>();
		if (route == null || route.isEmpty()) {
			return vias;
		}
		String[] airports = route.split("-");
		int hopoIdx = -1;
		for (int i = 0; i < airports.length; i++) {
			if (hopo.equals(airports[i])) {
				hopoIdx = i;
				break;
			}
		}
		if (hopoIdx < 0) {
			return vias;
		}
		if (isArrival) {
			for (int i = 1; i < hopoIdx; i++) {
				vias.add(airports[i]);
			}
		} else {
			for (int i = hopoIdx + 1; i < airports.length - 1; i++) {
				vias.add(airports[i]);
			}
		}
		return vias;
	}

	private void applySharedDerivations(FidsAfttab f, boolean isArrival) {
		// FLDA from SIBT (arrival) or SOBT (departure)
		String src = isArrival ? f.getSibt() : f.getSobt();
		if (src != null && src.length() >= 14 && !src.trim().isEmpty()) {
			try {
				f.setFlda(dateTimeFormatHelper.convertUTCToLocal(src).substring(0, 8));
			} catch (Exception e) {
				log.error("applyFlda parse error for value: " + src, e);
			}
		}

		// DOOA/DOOD + handle null cases
		if (f.getStoa() != null) {
			try {
				LocalDateTime ld = LocalDateTime.parse(f.getStoa(), YMD_HMS);
				OffsetDateTime utc = ld.atOffset(ZoneOffset.UTC);
				f.setDooa(Integer.toString(utc.getDayOfWeek().getValue()));
			} catch (Exception e) {
				log.error("DOOA parse error for STOA=" + f.getStoa(), e);
			}
		}

		if (f.getStod() != null) {
			try {
				LocalDateTime ld = LocalDateTime.parse(f.getStod(), YMD_HMS);
				OffsetDateTime utc = ld.atOffset(ZoneOffset.UTC);
				f.setDood(Integer.toString(utc.getDayOfWeek().getValue()));
			} catch (Exception e) {
				log.error("DOOD parse error for STOD=" + f.getStod(), e);
			}
		}
	}

	// ─── INDEXED NESTED STRUCTURES ─────────────────────────────────────────
	/*
	 * private void applyBeltDetails(FidsAfttab f, Element flightElement, boolean
	 * isArrival) {
	 * NodeList nodes = flightElement.getElementsByTagName(isArrival ?
	 * "pl_baggagebelt" : "pl_departurebelt");
	 * String prefix = isArrival ? "pbb_" : "pdb_";
	 * String beltRefTag = prefix + (isArrival ? "rbb_baggagebelt" :
	 * "rdb_departurebelt");
	 * String tmbTag = prefix + (isArrival
	 * ? "rbb_refbaggagebelt/ref_baggagebelt/rbb_rctt_countrytype"
	 * : "rdb_refbaggagebelt/ref_baggagebelt/rdb_rctt_countrytype");
	 * 
	 * XPath xp = XPathFactory.newInstance().newXPath();
	 * for (int i = 0; i < nodes.getLength(); i++) {
	 * Node n = nodes.item(i);
	 * if ("delete".equalsIgnoreCase(((Element) n).getAttribute("action")))
	 * continue;
	 * try {
	 * String ba = convertDateStringIfNeeded(xp.evaluate(prefix + "beginactual",
	 * n));
	 * String bs = convertDateStringIfNeeded(xp.evaluate(prefix + "beginplan", n));
	 * String ea = convertDateStringIfNeeded(xp.evaluate(prefix + "endactual", n));
	 * String es = convertDateStringIfNeeded(xp.evaluate(prefix + "endplan", n));
	 * String blt = convertDateStringIfNeeded(xp.evaluate(beltRefTag, n));
	 * String tmb = convertDateStringIfNeeded(xp.evaluate(tmbTag, n));
	 * String bast = convertDateStringIfNeeded(xp.evaluate(prefix + "status", n));
	 * 
	 * setDynamicValue(f, "b", i, "ba", ba);
	 * setDynamicValue(f, "b", i, "bs", bs);
	 * setDynamicValue(f, "b", i, "ea", ea);
	 * setDynamicValue(f, "b", i, "es", es);
	 * setDynamicValue(f, "bas", i, "", ba);
	 * setDynamicValue(f, "bao", i, "", bs);
	 * setDynamicValue(f, "bae", i, "", ea);
	 * setDynamicValue(f, "bac", i, "", es);
	 * setDynamicValue(f, "blt", i, "", blt);
	 * setDynamicValue(f, "tmb", i, "", tmb);
	 * setDynamicValue(f, "baz", i, "", blt);
	 * f.setBast(bast);
	 * f.setB1ba(f.getAibt());
	 * } catch (XPathExpressionException e) {
	 * log.error("applyBeltDetails error: ", e);
	 * }
	 * }
	 * }
	 */

	/*
	 * private void applyGateDetails(FidsAfttab f, Element flightElement, boolean
	 * isArrival) {
	 * NodeList gatesNodes = flightElement.getElementsByTagName(isArrival ?
	 * "pa_arrivalgates" : "pd_departuregates");
	 * String gts = gatesNodes.getLength() > 0 ? gatesNodes.item(0).getTextContent()
	 * : "";
	 * if (gts != null && !gts.isEmpty()) {
	 * List<String> lstGates = new ArrayList<>(new
	 * LinkedHashSet<>(Arrays.asList(gts.split(","))));
	 * String fieldPrefix = "gt" + (isArrival ? "a" : "d");
	 * for (int i = 0; i < lstGates.size(); i++) {
	 * setDynamicValue(f, fieldPrefix, i, "", lstGates.get(i));
	 * }
	 * }
	 * 
	 * NodeList gateNodes = flightElement.getElementsByTagName(isArrival ?
	 * "pl_arrivalgate" : "pl_departuregate");
	 * String prefix = isArrival ? "pag_" : "pdg_";
	 * String fieldPrefix = "g" + (isArrival ? "a" : "d");
	 * XPath xp = XPathFactory.newInstance().newXPath();
	 * for (int i = 0; i < gateNodes.getLength(); i++) {
	 * Node n = gateNodes.item(i);
	 * try {
	 * setDynamicValue(f, fieldPrefix, i, "b",
	 * convertDateStringIfNeeded(xp.evaluate(prefix + "beginplan", n)));
	 * setDynamicValue(f, fieldPrefix, i, "x",
	 * convertDateStringIfNeeded(xp.evaluate(prefix + "beginactual", n)));
	 * setDynamicValue(f, fieldPrefix, i, "y",
	 * convertDateStringIfNeeded(xp.evaluate(prefix + "endactual", n)));
	 * setDynamicValue(f, fieldPrefix, i, "e",
	 * convertDateStringIfNeeded(xp.evaluate(prefix + "endplan", n)));
	 * } catch (XPathExpressionException e) {
	 * log.error("applyGateDetails error: ", e);
	 * }
	 * }
	 * }
	 */

	/*
	 * private void applyDelayReasons(FidsAfttab f, Element flightElement) {
	 * NodeList nodes = flightElement.getElementsByTagName("pl_delayreason");
	 * XPath xp = XPathFactory.newInstance().newXPath();
	 * for (int i = 0; i < nodes.getLength(); i++) {
	 * Node n = nodes.item(i);
	 * try {
	 * String dcd = convertDateStringIfNeeded(xp.evaluate("pdlr_rdlr_delayreason",
	 * n));
	 * String dela = convertDateStringIfNeeded(xp.evaluate("pdlr_delay", n));
	 * setDynamicValue(f, "dcd", i, "", dcd);
	 * setDynamicValue(f, "dtd", i, "", dela);
	 * f.setDela(dela);
	 * } catch (XPathExpressionException e) {
	 * log.error("applyDelayReasons error: ", e);
	 * }
	 * }
	 * }
	 */

	private List<FidsCcatab> getCounters(List<FidsCcatab> lstFidsCcatab, String counter, String flno,
			String actionType) {
		if (lstFidsCcatab == null || lstFidsCcatab.isEmpty()) {
			return new ArrayList<>();
		}

		List<FidsCcatab> lst = new ArrayList<>();
		boolean isDataset = "DATASET".equalsIgnoreCase(actionType);

		for (FidsCcatab item : lstFidsCcatab) {
			String action = item.getAction();

			// 2. เช็ก HOLD
			String ckic = item.getCkic();
			if (ckic != null && "HOLD".equalsIgnoreCase(ckic.trim())) {
				continue;
			}

			// 1. เช็ก action: ถ้าไม่มี action หรือไม่ใช่ DATASET/UPDATE/INSERT ให้ข้าม
			if (!isDataset && !"UPDATE".equalsIgnoreCase(action) && !"INSERT".equalsIgnoreCase(action) && !"DELETE".equalsIgnoreCase(action)) {
				continue;
			}

			// 3. จัด Format เติมช่องว่างให้ครบ 9 ตัวอักษร
			if (item.getFlno() == null || item.getFlno().trim().isEmpty()) {
				item.setFlno(flno); // ดึง flno จาก flight หลักมาใช้แทน
			}

			if (item.getFlno() != null) {
				item.setFlno(String.format("%-9s", item.getFlno()));
			}

			// 4. ถ้าเป็น Common ให้ตั้งค่า ckic ตาม pd_counters
			if ("C".equalsIgnoreCase(item.getCtyp())) {
				if (counter != null && !counter.trim().isEmpty()) {
					String[] counters = counter.split(",");
					for (String c : counters) {
						FidsCcatab fidsCcatab = new FidsCcatab();
						BeanUtils.copyProperties(item, fidsCcatab);
						fidsCcatab.setCkic(c.trim());
						lst.add(fidsCcatab);
					}
				}
			} else {
				FidsCcatab fidsCcatab = new FidsCcatab();
				BeanUtils.copyProperties(item, fidsCcatab);
				lst.add(fidsCcatab);
			}
		}

		return lst;
	}

	private List<FidsCcatab> getCountersCommon(List<FidsCcatab> rawList) {
		if (rawList == null || rawList.isEmpty()) {
			return Collections.emptyList();
		}

		List<FidsCcatab> list = new ArrayList<>();
		for (FidsCcatab item : rawList) {
			// 1. ดักจับกรณีเป็น HOLD ให้ข้าม
			if (item.getCkic() != null && "HOLD".equalsIgnoreCase(item.getCkic().trim())) {
				continue;
			}

			list.add(item);
		}
		return list;
	}

	/* private List<FidsCcatab> getCountersCommon(Element element, boolean isCommon) {
		List<FidsCcatab> lst = new ArrayList<>();
		NodeList counterNodes = isCommon
				? new ImplementNodeList(Arrays.asList((Node) element))
				: element.getElementsByTagName("pl_desk");

		XPath xp = XPathFactory.newInstance().newXPath();
		for (int i = 0; i < counterNodes.getLength(); i++) {
			FidsCcatab c = new FidsCcatab();
			Node n = counterNodes.item(i);
			try {
				String flnu = convertDateStringIfNeeded(xp.evaluate("pdk_idseq", n));
				String flno = convertDateStringIfNeeded(xp.evaluate(
						"pdk_rcnt_refmastercci/ref_counter/rcnt_ral_airline/ref_airline/ral_2lc", n));
				String ctyp = convertDateStringIfNeeded(xp.evaluate("pdk_rcnt_refmastercci/ref_counter/rcnt_type", n));
				String ckic = convertDateStringIfNeeded(xp.evaluate("pdk_rcnt_counter", n));
				String ckbs = convertDateStringIfNeeded(xp.evaluate("pdk_beginplan", n));
				String ckes = convertDateStringIfNeeded(xp.evaluate("pdk_endplan", n));
				String ckba = convertDateStringIfNeeded(xp.evaluate("pdk_beginactual", n));
				String ckea = convertDateStringIfNeeded(xp.evaluate("pdk_endactual", n));
				String ckit = convertDateStringIfNeeded(
						xp.evaluate("pdk_rcnt_refcounter/ref_counter/rcnt_rco_concourse", n));
				String disp = convertDateStringIfNeeded(xp.evaluate("pdk_checkinclassid", n));
				String act3 = convertDateStringIfNeeded(
						xp.evaluate("pdk_rcnt_refmastercci/ref_counter/rcnt_ral_airline/ref_airline/ral_3lc", n));
				if ("HOLD".equals(ckic))
					continue;

				c.setFlnu(new BigDecimal(flnu));
				c.setFlno(String.format("%-9s", flno));
				c.setCtyp(ctyp);
				c.setCkic(ckic);
				c.setCkbs(ckbs);
				c.setCkes(ckes);
				c.setCkba(ckba);
				c.setCkea(ckea);
				c.setCkit(ckit);
				c.setDisp(disp);
				c.setAct3(act3);
				lst.add(c);
			} catch (XPathExpressionException e) {
				log.error("getCounters error: ", e);
			}
		}
		return lst;
	} */

	// ─── HELPERS ───────────────────────────────────────────────────────────
	private Document parseDocument(String xmlString) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		DocumentBuilder builder = factory.newDocumentBuilder();
		return builder.parse(new InputSource(new StringReader(xmlString)));
	}

	private static String convertDateStringIfNeeded(String input) {
		if (input == null)
			return null;
		return ISO_8601_Z.matcher(input).matches() ? input.replaceAll("[-:TZ]", "") : input;
	}

	public String getVial(String via3, String via4) {
		String pad = "              ";
		return " " + via3 + via4 + pad + pad + pad + pad + pad + pad + pad + pad;// 120 length
	}

	/**
	 * แยกหมายเลขเที่ยวบินเป็น prefix (รหัสสายการบิน) + number + suffix แบบ
	 * "อ่านจากตำแหน่ง"
	 * โดยไม่ต้องใช้รหัสสายการบินจากภายนอก (alc2/alc3):
	 * - prefix = 2 ตัวแรก และรวมตัวที่ 3 ด้วยถ้าเป็น "ตัวอักษร" (รองรับรหัส 3
	 * ตัวที่มีอักษร เช่น J5T)
	 * - number = ตัวเลข 1-4 หลักถัดมา (zero-pad อย่างน้อย 3 หลัก)
	 * - suffix = ตัวอักษรท้าย 1 ตัว (ถ้ามี)
	 * คืน emptyMap ถ้าแยกไม่ได้ (สั้นกว่า 3 ตัว หรือส่วนหลัง prefix ไม่ใช่ "เลข 1-4
	 * หลัก + อักษร 0-1 ตัว")
	 */
	public Map<String, String> parseFlightNumber(String flightNumber) {
		if (flightNumber == null || flightNumber.length() < 3) {
			return Collections.emptyMap();
		}
		// Regex แยกส่วน: 
		// Group 1: อักษร/เลข 2 ตัว หรือ อักษร 3 ตัว (เช่น G9, TG, THY)
		// \\s*   : Space คั่นกลางกี่ตัวก็ได้ (หรือไม่มีเลย)
		// Group 2: เลขไฟลท์ 1-4 หลัก (เช่น 415, 0123)
		// Group 3: Suffix ตัวอักษร 0-1 ตัวปิดท้าย (เช่น A, B)
		Pattern pattern = Pattern.compile("^([A-Za-z0-9]{2}|[A-Za-z]{3})\\s*(\\d{1,5})\\s*([A-Za-z]?)$");
    	Matcher m = pattern.matcher(flightNumber.trim());
		if (!m.find()) {
			return Collections.emptyMap();
		}
		String prefix = m.group(1).toUpperCase();
		String number = String.format("%-4s", m.group(2)); // เติม space ด้านหลังให้กว้าง 4
		String suffix = m.group(3).toUpperCase();
		if (prefix.length() == 2)
			prefix = prefix + " "; // airline กว้าง 3

		Map<String, String> map = new HashMap<>();
		map.put("prefix", prefix);
		map.put("number", number);
		map.put("suffix", suffix);
		return map;
	}

	/**
	 * ประกอบหมายเลขเที่ยวบินเป็นความกว้างคงที่ 9 ตัว:
	 * airline(3) + number(4) + ช่องว่างคั่น(1) + suffix หรือช่องว่าง(1)
	 * (prefix/number ถูก pad มาจาก {@link #parseFlightNumber} แล้วเป็น 3 และ 4
	 * ตามลำดับ)
	 */
	public static String toFlno(Map<String, String> parts) {
		if (parts == null || parts.isEmpty()) {
			return "";
		}
		String suffix = parts.get("suffix");
		return parts.get("prefix") + parts.get("number") + " "
				+ (suffix == null || suffix.isEmpty() ? " " : suffix);
	}

	public String toFlnoNonSuffix(Map<String, String> parts) {
		if (parts == null || parts.isEmpty()) {
			return "";
		}
		return parts.get("prefix") + parts.get("number");
	}

	// Data Record หรือ Helper Class สำหรับเก็บผลลัพธ์
	public record JfnoResult(String formattedJfno, String count) {
	}

	/**
	 * แปลง JFNO (รายการเที่ยวบิน codeshare คั่นด้วย ",")
	 * โดยจัดรูปแต่ละเที่ยวบินเหมือน FLNO
	 * (9 ตัวคงที่ ผ่าน {@link #toFlno}) แล้ว "ต่อกันตรงๆ" ไม่มี separator (split
	 * ทุก 9 ตัวได้).
	 * ถ้าตัวไหนแยกไม่ได้ ใช้ค่าเดิม (trim) และข้ามตัวที่ว่าง.
	 */
	public JfnoResult processJfno(String jfno) {
		if (jfno == null || jfno.isBlank()) {
			return new JfnoResult(jfno, "0");
		}

		StringBuilder sb = new StringBuilder();
		int count = 0;

		for (String part : jfno.split(",")) {
			String j = part.trim();
			if (j.isEmpty()) {
				continue;
			}
			count++;
			Map<String, String> p = parseFlightNumber(j);
			sb.append(p.isEmpty() ? j : toFlno(p));
		}

		return new JfnoResult(sb.toString(), String.valueOf(count));
	}

	/*
	 * public void setDynamicValue(FidsAfttab obj, String fieldPrefix, int index,
	 * String fieldSuffix, String value) {
	 * try {
	 * String fieldName = fieldPrefix + (index + 1) + fieldSuffix;
	 * Field field = FidsAfttab.class.getDeclaredField(fieldName);
	 * field.setAccessible(true);
	 * field.set(obj, value);
	 * } catch (Exception e) {
	 * log.error("setDynamicValue: ", e);
	 * }
	 * }
	 */
}
