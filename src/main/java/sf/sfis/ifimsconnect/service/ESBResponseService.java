package sf.sfis.ifimsconnect.service;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import jakarta.mail.*;
import jakarta.mail.internet.*;

import org.springframework.stereotype.Service;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.SOAPBody;
import jakarta.xml.soap.SOAPConnection;
import jakarta.xml.soap.SOAPConnectionFactory;
import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPEnvelope;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;
import jakarta.xml.soap.SOAPPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sf.sfis.ifimsconnect.MQWebSphereProducer;
import sf.sfis.ifimsconnect.aodb.Envelope;
import sf.sfis.ifimsconnect.aodb.Fault;
import sf.sfis.ifimsconnect.aodb.IfAdexpmessage;
import sf.sfis.ifimsconnect.controller.RedisController;
import sf.sfis.ifimsconnect.esb.MSG.NACKDETAIL;
import sf.sfis.ifimsconnect.esb.ObjectFactory;
import sf.sfis.ifimsconnect.esb.realtimeoutbound.ACTIONTYPE;
import sf.sfis.ifimsconnect.esb.realtimeoutbound.ADID;
import sf.sfis.ifimsconnect.esb.realtimeoutbound.CTYP;
import sf.sfis.ifimsconnect.esb.realtimeoutbound.FLTI;
import sf.sfis.ifimsconnect.esb.realtimeoutbound.MSG;
import sf.sfis.ifimsconnect.esb.realtimeoutbound.MSG.MSGSTREAMOUT;
import sf.sfis.ifimsconnect.esb.realtimeoutbound.MSG.MSGSTREAMOUT.BULKDATA;
import sf.sfis.ifimsconnect.esb.realtimeoutbound.MSG.MSGSTREAMOUT.CONCAT;
import sf.sfis.ifimsconnect.esb.realtimeoutbound.MSG.MSGSTREAMOUT.INFOBJACPOSITION;
import sf.sfis.ifimsconnect.esb.realtimeoutbound.MSG.MSGSTREAMOUT.INFOBJBELT;
import sf.sfis.ifimsconnect.esb.realtimeoutbound.MSG.MSGSTREAMOUT.INFOBJCOUNTER;
import sf.sfis.ifimsconnect.esb.realtimeoutbound.MSG.MSGSTREAMOUT.INFOBJFLIGHT;
import sf.sfis.ifimsconnect.esb.realtimeoutbound.MSG.MSGSTREAMOUT.INFOBJGATE;
import sf.sfis.ifimsconnect.esb.realtimeoutbound.MSG.MSGSTREAMOUT.INFOBJGENERIC;
import sf.sfis.ifimsconnect.esb.realtimeoutbound.MSG.MSGSTREAMOUT.INFOBJVDGS;
import sf.sfis.ifimsconnect.esb.realtimeoutbound.TIMEID;
import sf.sfis.ifimsconnect.model.FidsAfttab;
import sf.sfis.ifimsconnect.model.FidsCcatab;
import sf.sfis.ifimsconnect.utility.DateTimeFormatHelper;
import sf.sfis.ifimsconnect.utility.FieldInspector;
import sf.sfis.ifimsconnect.utility.GetterAccess;
import sf.sfis.ifimsconnect.utility.TranformFidsAfttab;

@Slf4j
@Service
@RequiredArgsConstructor
public class ESBResponseService {
	private final DateTimeFormatHelper dateTimeFormatHelper;
	private final TranformFidsAfttab tranformFidsAfttab;
	private final FidsAfttabService fidsAfttabService;
	private final FidsCcatabService fidsCcatabService;
	private final FidsGateHistoryService fidsGateHistoryService;
	private final FidsFinalcallHistoryService fidsFinalcallHistoryService;
	private final RedisController redisController;
	private final MQWebSphereProducer webSphereProducer;
	// Dedicated logger for outbound XML to ESB → routed to
	// logs/outbound/<hopo>/<queue>.log
	private static final Logger sendEsbLog = LoggerFactory.getLogger("SEND_ESB_XML");

	// true = ส่ง flight update ออก ESB ผ่าน WebSphere MQ (IBM MQ อย่างเดียว)
	// false = ส่งผ่าน webservice (callWebserviceUpdate) แทน
	// ใช้ flag เดียวกับที่ปิด/เปิด WebSphere MQ เพื่อไม่ให้ส่งซ้ำ และ prod (MQ
	// ยังไม่มา) จะ fallback เป็น webservice
	@Value("${websphere.mq.enabled:true}")
	private boolean webSphereEnabled;

	// Cached JAXBContexts — thread-safe and expensive to build, so create once
	// instead
	// of per message. (Marshaller/Unmarshaller are not thread-safe and stay per
	// call.)
	private static final JAXBContext ENVELOPE_CTX = newContext(Envelope.class);
	private static final JAXBContext ESB_MSG_CTX = newContext(sf.sfis.ifimsconnect.esb.MSG.class);
	private static final JAXBContext OUT_MSG_CTX = newContext(MSG.class);

	private static JAXBContext newContext(Class<?> clazz) {
		try {
			return JAXBContext.newInstance(clazz);
		} catch (JAXBException e) {
			throw new IllegalStateException("Failed to init JAXBContext for " + clazz.getName(), e);
		}
	}

	public void convertXMLtoObject(String xml) {
		try {
			log.info("ESBResponseService...");
			JAXBContext jaxbContext = ENVELOPE_CTX;
			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

			Envelope envelope = (Envelope) unmarshaller.unmarshal(new StringReader(xml));
			String type = envelope.getHeader().getControl().getMessageType();
			String timestamp = dateTimeFormatHelper.convertLocalToUTC(envelope.getHeader().getControl().getTimestamp());
			String hopo = envelope.getHeader().getControl().getStation();
			String originator = envelope.getHeader().getControl().getOriginator();

			log.info("Message Type: " + type);
			if ("ACK".equalsIgnoreCase(type)) {
				log.info("Received ACK message. Skip convert XML.");
				return; // ข้ามการแปลงข้อมูล
			}

			StringWriter writer = new StringWriter();
			Marshaller marshaller = jaxbContext.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			marshaller.marshal(envelope, writer);

			// TranformFidsAfttab tranformFidsAfttab = new TranformFidsAfttab();
			// Insert or Update all fields on FidsAfttab.
			FidsAfttab fidsAfttab = tranformFidsAfttab.convertPlTurntoAfftab(writer.toString(), "DATASET", hopo, "A");
			if (fidsAfttab != null) {
				if (fidsAfttab.getUrno() != null) {
					fidsAfttab = fidsAfttabService.saveFidsAfttab(fidsAfttab);
					if ((fidsAfttab.getGtd1() != null && fidsAfttab.getGtd1().length() > 0)
							|| (fidsAfttab.getGtd2() != null && fidsAfttab.getGtd2().length() > 0)) {
						fidsGateHistoryService.updateGateChangeHistory(fidsAfttab);
					}
					if (fidsAfttab.getRemp() != null && fidsAfttab.getRemp().length() > 0) {
						fidsFinalcallHistoryService.updateFinalCallHistory(fidsAfttab);
					}
				}
			}
			fidsAfttab = tranformFidsAfttab.convertPlTurntoAfftab(writer.toString(), "DATASET", hopo, "D");
			if (fidsAfttab != null) {
				fidsCcatabService.updateCcatab(fidsAfttab);
				if (fidsAfttab.getUrno() != null) {
					fidsAfttab = fidsAfttabService.saveFidsAfttab(fidsAfttab);
					if ((fidsAfttab.getGtd1() != null && fidsAfttab.getGtd1().length() > 0)
							|| (fidsAfttab.getGtd2() != null && fidsAfttab.getGtd2().length() > 0)) {
						fidsGateHistoryService.updateGateChangeHistory(fidsAfttab);
					}
					if (fidsAfttab.getRemp() != null && fidsAfttab.getRemp().length() > 0) {
						fidsFinalcallHistoryService.updateFinalCallHistory(fidsAfttab);
					}
				}
			}

			if (type.equalsIgnoreCase("UPDATE")) {// Send update fields to ESB by Web service.
				// Arrival
				FidsAfttab arrivalAfttab = tranformFidsAfttab.convertPlTurntoAfftab(writer.toString(), type, hopo, "A",
						originator);
				if (arrivalAfttab != null) {
					String xmlEsb = convertFidsAfftabtoEsb(timestamp, originator, arrivalAfttab);
					if (xmlEsb != null) {
						log.info("Update arrival flight to ESB...");
						// log.info(xmlEsb);
						sendToOutboundQueue("UFIS_FLIGHT_OUT", hopo, xmlEsb);
					} else {
						log.info("No data found for ESB update.");
					}

					// Send counter to Outbound UFIS_COUNTER_OUT
					// sendCounter(timestamp, arrivalAfttab); Arrival ไม่มี Counter
					// Send gate to Outbound UFIS_GATE_OUT
					sendGate(timestamp, arrivalAfttab);
					// Send belt to Outbound UFIS_BELT_OUT
					sendBelt(timestamp, arrivalAfttab);
					// Send acposition to Outbound UFIS_ACPOSITION_OUT
					sendAcposition(timestamp, arrivalAfttab);
				}

				// Departure
				FidsAfttab departureAfttab = tranformFidsAfttab.convertPlTurntoAfftab(writer.toString(), type, hopo,
						"D", originator);
				if (departureAfttab != null) {
					String xmlEsb = convertFidsAfftabtoEsb(timestamp, originator, departureAfttab);
					if (xmlEsb != null) {
						log.info("Update departure flight to ESB...");
						// log.info(xmlEsb);
						sendToOutboundQueue("UFIS_FLIGHT_OUT", hopo, xmlEsb);
					} else {
						log.info("No data found for ESB update.");
					}

					// Send counter to Outbound UFIS_COUNTER_OUT
					sendCounter(timestamp, departureAfttab);
					// Send gate to Outbound UFIS_GATE_OUT
					sendGate(timestamp, departureAfttab);
					// Send belt to Outbound UFIS_BELT_OUT
					// sendBelt(timestamp, departureAfttab); Departure ไม่มี Belt
					// Send acposition to Outbound UFIS_ACPOSITION_OUT
					sendAcposition(timestamp, departureAfttab);
				}

				// Save data to Redis.
				redisController.saveData(hopo);
			} else if (!type.equalsIgnoreCase("DATASET")) {// Send ACK or NACK to ESB by Web service.
				// ส่ง Webservice ในกรณีที่ ESB Request อัพเดทข้อมูล Flight แล้ว TSystem ส่ง
				// ACK/NACK กลับมา
				String contentBody = getContentBody(writer.toString());
				String xmlEsb = convertResponseMessagetoEsb(timestamp, envelope, contentBody);
				// ACK (หรือ marshal ล้มเหลว) จะได้ xmlEsb == null → ไม่ต้องส่ง webservice
				// (ถ้าไม่เช็คจะเรียก callWebserviceResponse(null) แล้วโยน NPE ตอน
				// addTextNode(null)
				// ทำให้ได้ ERROR log ทุกครั้งที่มี ACK และ SOAPConnection ไม่ถูกปิด)
				if (xmlEsb != null) {
					log.info("Call Web service response NACK...");
					// log.info(xmlEsb);
					MDC.put("sendEsbKey", hopo + "/outbound-NACK");
					try {
						sendEsbLog.info(xmlEsb);
					} finally {
						MDC.remove("sendEsbKey");
					}
					// callWebserviceResponse(xmlEsb);
					sendEmailResponse(xmlEsb, hopo);
				} else {
					log.info("ACK message ignored (no ESB response to send).");
				}
			}
		} catch (JAXBException e) {
			log.error("convertXMLtoObject: ", e);
			// e.printStackTrace();
		} catch (Exception e) {
			log.error("convertXMLtoObject: ", e);
			e.printStackTrace();
		}
	}

	public String convertResponseMessagetoEsb(String updateTime, Envelope envelope, String bodyContent) {
		try {
			StringWriter writer = new StringWriter();
			ObjectFactory factory = new ObjectFactory();
			sf.sfis.ifimsconnect.esb.MSG msgReturn = new sf.sfis.ifimsconnect.esb.MSG();
			msgReturn.setMSGORIGIN(envelope.getHeader().getControl().getSender());
			msgReturn.setTIME(updateTime);
			msgReturn.setACKTYPE(envelope.getHeader().getControl().getMessageType());

			IfAdexpmessage ifAdexpmessage = envelope.getBody().getIfAdexpmessage();
			if (ifAdexpmessage != null) {
				String iamOriginalmessage = GetterAccess.get(ifAdexpmessage, p -> p.getIamOriginalmessage(),
						v1 -> v1.getValue(), v2 -> v2.getValue(), v -> v.toString()).orElse("");
				// log.info("AFTN Message : " + iamOriginalmessage);
				msgReturn.setMESSAGE(iamOriginalmessage);
			} else {
				msgReturn.setMESSAGE(bodyContent);
			}

			String type = envelope.getHeader().getControl().getMessageType();
			if (type.equals("NACK")) {
				Fault fault = envelope.getBody().getFault();
				NACKDETAIL nackdetail = new NACKDETAIL();
				nackdetail.setFaultcode(factory.createMSGNACKDETAILFaultcode(fault.getFaultcode()));
				nackdetail.setFaultactor(factory.createMSGNACKDETAILFaultactor(fault.getFaultactor()));
				nackdetail.setFaultstring(factory.createMSGNACKDETAILFaultstring(fault.getFaultstring()));
				nackdetail.setFaultdetail(factory.createMSGNACKDETAILFaultdetail(fault.getDetail()));
				msgReturn.setNACKDETAIL(factory.createMSGNACKDETAIL(nackdetail));
			} else {
				// ใช้สำหรับปิดการรับข้อมูลที่เป็น ACK
				return null;
			}

			JAXBContext context = ESB_MSG_CTX;
			Marshaller marshaller = context.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			marshaller.marshal(msgReturn, writer);
			// log.info("convertResponseMessagetoEsb...");
			// log.info(writer.toString());
			return writer.toString();
		} catch (JAXBException e) {
			log.error("convertResponseMessagetoEsb: ", e);
			// e.printStackTrace();
		}
		return null;
	}

	public String convertFidsAfftabtoEsb(String updateTime, String originator, FidsAfttab fidsAfttab) {
		StringWriter writer = new StringWriter();
		MSG esbAfttab = new MSG();
		MSG.MSGSTREAMOUT msgstreamout = new MSGSTREAMOUT();
		// INFOBJ_GENERIC — ใช้ helper ร่วมกับ counter/gate (แก้ logic ที่เดียว)
		MSG.MSGSTREAMOUT.INFOBJGENERIC infobjgeneric = buildOutboundGeneric("UFISFLTUD", updateTime, fidsAfttab,
				fidsAfttab.getAction());

		MSG.MSGSTREAMOUT.INFOBJFLIGHT infobjflight = new INFOBJFLIGHT();
		// Set<String> copyFields = new HashSet<>(Arrays.asList("fpla" ,"fpld", "eldt",
		// "etdi", "atot", "etot", "ctot", "etai", "tldt", "tmoa",
		// "rwya", "rwyd", "land", "airb", "ifra", "ifrd", "onbl", "ofbl", "acgt",
		// "ttot", "tobt", "tsat", "asbt",
		// "remp", "ardt", "asrt", "asat"));
		copyMatchingFields(fidsAfttab.getFieldsNotNull(), fidsAfttab, infobjflight);

		// IDEP: ต้องส่ง TSAT ไป ESB เสมอ แม้ TSAT จะไม่ได้อยู่ในชุด field ที่เปลี่ยน
		// (fieldsNotNull)
		if ("IDEP".equalsIgnoreCase(originator)
				&& fidsAfttab.getTsat() != null && !fidsAfttab.getTsat().trim().isEmpty()) {
			infobjflight.setTSAT(fidsAfttab.getTsat());
		}

		if (!FieldInspector.allFieldsAreNull(infobjflight)) {
			// Fix field for ESB
			infobjflight.setFLTI(FLTI.valueOf(fidsAfttab.getFlti()));
			infobjflight.setRKEY(fidsAfttab.getRkey().toString());
			infobjflight.setRTYP(fidsAfttab.getRtyp());
			infobjflight.setFTYP(fidsAfttab.getFtyp());

			// infobjflight.setFLNO(infobjflight.getFLNO() != null ?
			// infobjflight.getFLNO().trim() : null);
			infobjflight.setFLTN(infobjflight.getFLTN() != null ? infobjflight.getFLTN().trim() : null);
			// Different field between FIDS and ESB
			// infobjflight.setSLOT(fidsAfttab.getCtot()); ทำใน XSL
			if (!"D".equals(fidsAfttab.getFtyp())) {
				infobjflight.setFLNS(null);
			}

			msgstreamout.setINFOBJGENERIC(infobjgeneric);
			msgstreamout.setINFOBJFLIGHT(infobjflight);
			esbAfttab.setMSGSTREAMOUT(msgstreamout);
			try {
				JAXBContext context = OUT_MSG_CTX;
				Marshaller marshaller = context.createMarshaller();
				marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
				marshaller.marshal(esbAfttab, writer);
				return writer.toString();
			} catch (JAXBException e) {
				log.error("convertFidsAfftabtoEsb: ", e);
				// e.printStackTrace();
			}
		}
		return null;
	}

	/**
	 * ปั้น XML สำหรับคิว UFIS_COUNTER_OUT (UFISCHKUD — check-in dedicated counter
	 * update).
	 */
	public String convertDedicatedCounterToEsb(MSG.MSGSTREAMOUT.INFOBJGENERIC generic, FidsAfttab fidsAfttab,
			FidsCcatab fidsCcatab) {
		StringWriter writer = new StringWriter();
		MSG esb = new MSG();
		MSG.MSGSTREAMOUT msgstreamout = new MSGSTREAMOUT();

		// INFOBJ_GENERIC — dynamic จาก FidsAfttab (identity counter = UFISCHKUD)
		// MSG.MSGSTREAMOUT.INFOBJGENERIC generic = buildOutboundGeneric("UFISCHKUD",
		// updateTime, fidsAfttab);

		MSG.MSGSTREAMOUT.INFOBJCOUNTER counter = new INFOBJCOUNTER();

		// แมปค่าจาก FidsCcatab ลงใน INFOBJCOUNTER
		counter.setCKIC(fidsCcatab.getCkic());
		counter.setCKIT(fidsCcatab.getCkit());
		counter.setCKBS(fidsCcatab.getCkbs());
		counter.setCKES(fidsCcatab.getCkes());
		counter.setDISP(fidsCcatab.getDisp());
		// Only ESB set switch value.
		String urno = fidsAfttab.getUrno() != null ? fidsAfttab.getUrno().toString() : null;
		String flnu = fidsCcatab.getFlnu() != null ? fidsCcatab.getFlnu().toString() : null;
		counter.setFLNU(urno);
		counter.setURNO(flnu);
		counter.setCTYP(CTYP.valueOf(fidsCcatab.getCtyp()));

		// sanitizeEmptyToNull(counter);
		msgstreamout.setINFOBJCOUNTER(counter);
		msgstreamout.setINFOBJGENERIC(generic);
		esb.setMSGSTREAMOUT(msgstreamout);
		try {
			Marshaller marshaller = OUT_MSG_CTX.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			marshaller.marshal(esb, writer);
			return writer.toString();
		} catch (JAXBException e) {
			log.error("convertCountertoEsb: ", e);
		}
		return null;
	}

	/**
	 * ปั้น XML สำหรับคิว UFIS_COUNTER_OUT (UFISCCIUD — check-in common counter
	 * update).
	 */
	public String convertCommonCounterToEsb(MSG.MSGSTREAMOUT.INFOBJGENERIC generic, FidsAfttab fidsAfttab,
			FidsCcatab fidsCcatab) {
		StringWriter writer = new StringWriter();
		MSG esb = new MSG();
		MSG.MSGSTREAMOUT msgstreamout = new MSGSTREAMOUT();

		// สร้างโครงสร้างชั้น STATIC -> RESOURCES -> COMMON_COUNTERS
		MSG.MSGSTREAMOUT.STATIC staticNode = new MSG.MSGSTREAMOUT.STATIC();
		MSG.MSGSTREAMOUT.STATIC.RESOURCES resources = new MSG.MSGSTREAMOUT.STATIC.RESOURCES();
		MSG.MSGSTREAMOUT.STATIC.RESOURCES.COMMONCOUNTERS commonCounter = new MSG.MSGSTREAMOUT.STATIC.RESOURCES.COMMONCOUNTERS();

		// เซ็ตฟิลด์ตามโครงสร้าง Common Counter จากรูปภาพ
		commonCounter.setCKIC(fidsCcatab.getCkic());
		commonCounter.setALCD(fidsAfttab.getAlc2());
		commonCounter.setCTYP(fidsCcatab.getCtyp());
		commonCounter.setCKIT(fidsCcatab.getCkit());
		commonCounter.setCKBS(fidsCcatab.getCkbs());
		commonCounter.setCKES(fidsCcatab.getCkes());
		commonCounter.setCKEA(fidsCcatab.getCkea());

		// Switch value FLNU / URNO
		String urno = fidsAfttab.getUrno() != null ? fidsAfttab.getUrno().toString() : null;
		String flnu = fidsCcatab.getFlnu() != null ? fidsCcatab.getFlnu().toString() : null;

		commonCounter.setFLNU(urno);
		commonCounter.setURNO(flnu);
		// sanitizeEmptyToNull(commonCounter);
		// ประกอบโครงสร้างเข้าด้วยกัน
		// (หมายเหตุ: ถ้า JAXB เจนมาเป็น List ให้ใช้
		// .getCOMMONCOUNTERS().add(commonCounter))
		resources.setCOMMONCOUNTERS(commonCounter);
		staticNode.setRESOURCES(resources);

		msgstreamout.setINFOBJGENERIC(generic);
		msgstreamout.setSTATIC(staticNode);
		esb.setMSGSTREAMOUT(msgstreamout);

		try {
			Marshaller marshaller = OUT_MSG_CTX.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			marshaller.marshal(esb, writer);
			return writer.toString();
		} catch (JAXBException e) {
			log.error("convertCommonCounterToEsb: ", e);
		}
		return null;
	}

	/**
	 * ส่ง counter เข้าคิว UFIS_COUNTER_OUT_{HOPO} โดย INFOBJ_GENERIC ดึงจาก
	 * FidsAfttab
	 */
	public void sendCounter(String updateTime, FidsAfttab fidsAfttab) {
		if (fidsAfttab != null && fidsAfttab.getLstFidsCcatab() != null && !fidsAfttab.getLstFidsCcatab().isEmpty()) {
			for (FidsCcatab item : fidsAfttab.getLstFidsCcatab()) {
				boolean isCommon = "C".equalsIgnoreCase(item.getCtyp());
				String msgType = isCommon ? "UFISCCIUD" : "UFISCHKUD";
				MSG.MSGSTREAMOUT.INFOBJGENERIC generic = buildOutboundGeneric(msgType, updateTime, fidsAfttab,
						item.getAction());
				String xmlEsb;
				if (isCommon) {
					xmlEsb = convertCommonCounterToEsb(generic, fidsAfttab, item);
				} else {
					xmlEsb = convertDedicatedCounterToEsb(generic, fidsAfttab, item);
				}

				if (xmlEsb != null) {
					log.info("Update {} counter ({}) to ESB...", isCommon ? "Common" : "Dedicated", item.getCkic());
					sendToOutboundQueue("UFIS_COUNTER_OUT", fidsAfttab.getHopo(), xmlEsb);
				}
			}
		}
	}

	/**
	 * ปั้น XML สำหรับคิว UFIS_GATE_OUT (UFISGTDUD — gate update).
	 * INFOBJ_GENERIC ดึงจาก FidsAfttab (ดู {@link #buildOutboundGeneric}).
	 * INFOBJ_GATE เลือก GATEARR/GATEDEP ตาม ADID
	 */
	public String convertGatetoEsb(String updateTime, FidsAfttab fidsAfttab) {
		StringWriter writer = new StringWriter();
		MSG esb = new MSG();
		MSG.MSGSTREAMOUT msgstreamout = new MSGSTREAMOUT();

		MSG.MSGSTREAMOUT.INFOBJGENERIC generic = buildOutboundGeneric("UFISGTDUD", updateTime, fidsAfttab,
				"delete".equalsIgnoreCase(fidsAfttab.getGateAction()) ? "update" : fidsAfttab.getGateAction());

		// --- INFOBJ_GATE: เลือก arr/dep ตาม ADID ---
		MSG.MSGSTREAMOUT.INFOBJGATE gate = new INFOBJGATE();

		if (generic != null && generic.getADID() == ADID.A) {
			INFOBJGATE.GATEARR arr = new INFOBJGATE.GATEARR();
			copyMatchingFields(fidsAfttab.getFieldsNotNull(), fidsAfttab, arr);
			if ("delete".equalsIgnoreCase(fidsAfttab.getGateAction())) {
				arr.setGTA1(" ");
				arr.setGTA2(" ");
			} else if ((fidsAfttab.getGta1() != null && !fidsAfttab.getGta1().trim().equalsIgnoreCase(""))
				|| (fidsAfttab.getGta2() != null && !fidsAfttab.getGta2().trim().equalsIgnoreCase(""))) {
				arr.setGTA1(formatEmpty(fidsAfttab.getGta1()));
				arr.setGA1B(formatEmpty(fidsAfttab.getGa1b()));
				arr.setGA1E(formatEmpty(fidsAfttab.getGa1e()));
				arr.setGA1X(formatEmpty(fidsAfttab.getGa1x()));
				arr.setGA1Y(formatEmpty(fidsAfttab.getGa1y()));

				arr.setGTA2(formatEmpty(fidsAfttab.getGta2()));
				arr.setGA2B(formatEmpty(fidsAfttab.getGa2b()));
				arr.setGA2E(formatEmpty(fidsAfttab.getGa2e()));
				arr.setGA2X(formatEmpty(fidsAfttab.getGa2x()));
				arr.setGA2Y(formatEmpty(fidsAfttab.getGa2y()));
			} else {
				return null;
			}
			// sanitizeEmptyToNull(arr);
			gate.setGATEARR(arr);
		} else {
			INFOBJGATE.GATEDEP dep = new INFOBJGATE.GATEDEP();
			copyMatchingFields(fidsAfttab.getFieldsNotNull(), fidsAfttab, dep);
			if ("delete".equalsIgnoreCase(fidsAfttab.getGateAction())) {
				dep.setGTD1(" ");
				dep.setGTD2(" ");
			} else if ((fidsAfttab.getGtd1() != null && !fidsAfttab.getGtd1().trim().equalsIgnoreCase(""))
				|| (fidsAfttab.getGtd2() != null && !fidsAfttab.getGtd2().trim().equalsIgnoreCase(""))) {
				dep.setGTD1(formatEmpty(fidsAfttab.getGtd1()));
				dep.setGD1B(formatEmpty(fidsAfttab.getGd1b()));
				dep.setGD1E(formatEmpty(fidsAfttab.getGd1e()));
				dep.setGD1X(formatEmpty(fidsAfttab.getGd1x()));
				dep.setGD1Y(formatEmpty(fidsAfttab.getGd1y()));

				dep.setGTD2(formatEmpty(fidsAfttab.getGtd2()));
				dep.setGD2B(formatEmpty(fidsAfttab.getGd2b()));
				dep.setGD2E(formatEmpty(fidsAfttab.getGd2e()));
				dep.setGD2X(formatEmpty(fidsAfttab.getGd2x()));
				dep.setGD2Y(formatEmpty(fidsAfttab.getGd2y()));
			} else {
				return null;
			}
			// sanitizeEmptyToNull(dep);
			gate.setGATEDEP(dep);
		}

		if (!hasAnyData(gate)) {
			return null;
		}

		msgstreamout.setINFOBJGENERIC(generic);
		msgstreamout.setINFOBJGATE(gate);
		esb.setMSGSTREAMOUT(msgstreamout);

		try {
			Marshaller marshaller = OUT_MSG_CTX.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			marshaller.marshal(esb, writer);
			return writer.toString();
		} catch (JAXBException e) {
			log.error("convertGatetoEsb JAXB error: ", e);
		}
		return null;
	}

	/**
	 * ส่ง gate เข้าคิว UFIS_GATE_OUT_{HOPO};
	 */
	public void sendGate(String updateTime, FidsAfttab fidsAfttab) {
		if (fidsAfttab != null && fidsAfttab.getGateAction() != null) {
			String xmlEsb = convertGatetoEsb(updateTime, fidsAfttab);
			if (xmlEsb != null) {
				log.info("Update gate to ESB...");
				sendToOutboundQueue("UFIS_GATE_OUT", fidsAfttab.getHopo(), xmlEsb);
			}
		}
	}

	/**
	 * ปั้น XML สำหรับคิว UFIS_BELT_OUT (UFISBLTUD — baggage belt update).
	 * INFOBJ_GENERIC ดึงจาก FidsAfttab (ดู {@link #buildOutboundGeneric}).
	 */
	public String convertBelttoEsb(String updateTime, FidsAfttab fidsAfttab) {
		StringWriter writer = new StringWriter();
		MSG esb = new MSG();
		MSG.MSGSTREAMOUT msgstreamout = new MSGSTREAMOUT();

		MSG.MSGSTREAMOUT.INFOBJGENERIC generic = buildOutboundGeneric("UFISBLTUD", updateTime, fidsAfttab,
				"delete".equalsIgnoreCase(fidsAfttab.getBeltAction()) ? "update" : fidsAfttab.getBeltAction());

		// --- INFOBJ_BELT
		MSG.MSGSTREAMOUT.INFOBJBELT belt = new INFOBJBELT();
		/*
		 * belt.setBLT1("");
		 * belt.setB1BS("");
		 * belt.setB1ES("");
		 */
		copyMatchingFields(fidsAfttab.getFieldsNotNull(), fidsAfttab, belt);
		if ("delete".equalsIgnoreCase(fidsAfttab.getBeltAction())) {
			belt.setBLT1(" ");
			belt.setBLT2(" ");
		} else if ((fidsAfttab.getBlt1() != null && !fidsAfttab.getBlt1().trim().equalsIgnoreCase(""))
				|| (fidsAfttab.getBlt2() != null && !fidsAfttab.getBlt2().trim().equalsIgnoreCase(""))) {
			belt.setBLT1(formatEmpty(fidsAfttab.getBlt1()));
			belt.setB1BS(formatEmpty(fidsAfttab.getB1bs()));
			belt.setB1ES(formatEmpty(fidsAfttab.getB1es()));
			belt.setB1BA(formatEmpty(fidsAfttab.getB1ba()));
			belt.setB1EA(formatEmpty(fidsAfttab.getB1ea()));

			belt.setBLT2(formatEmpty(fidsAfttab.getBlt2()));
			belt.setB2BS(formatEmpty(fidsAfttab.getB2bs()));
			belt.setB2ES(formatEmpty(fidsAfttab.getB2es()));
			belt.setB2BA(formatEmpty(fidsAfttab.getB2ba()));
			belt.setB2EA(formatEmpty(fidsAfttab.getB2ea()));
		} else {
			return null;
		}
		// sanitizeEmptyToNull(belt);

		if (!hasAnyData(belt)) {
			return null;
		}

		msgstreamout.setINFOBJGENERIC(generic);
		msgstreamout.setINFOBJBELT(belt);
		esb.setMSGSTREAMOUT(msgstreamout);
		try {
			Marshaller marshaller = OUT_MSG_CTX.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			marshaller.marshal(esb, writer);
			return writer.toString();
		} catch (JAXBException e) {
			log.error("convertBelttoEsb: ", e);
		}
		return null;
	}

	/**
	 * ส่ง belt เข้าคิว UFIS_BELT_OUT_{HOPO}
	 */
	public void sendBelt(String updateTime, FidsAfttab fidsAfttab) {
		if (fidsAfttab != null && fidsAfttab.getBeltAction() != null) {
			String xmlEsb = convertBelttoEsb(updateTime, fidsAfttab);
			if (xmlEsb != null) {
				log.info("Update belt to ESB...");
				sendToOutboundQueue("UFIS_BELT_OUT", fidsAfttab.getHopo(), xmlEsb);
			}
		}
	}

	/**
	 * ปั้น XML สำหรับคิว UFIS_ACPOSITION_OUT (UFISPOSUD — aircraft position/stand
	 * update).
	 * INFOBJ_GENERIC ดึงจาก FidsAfttab (ดู {@link #buildOutboundGeneric}).
	 * INFOBJ_ACPOSITION เลือก ACPOSITIONARR/ACPOSITIONDEP ตาม ADID
	 */
	public String convertAcpositiontoEsb(String updateTime, FidsAfttab fidsAfttab) {
		StringWriter writer = new StringWriter();
		MSG esb = new MSG();
		MSG.MSGSTREAMOUT msgstreamout = new MSGSTREAMOUT();

		MSG.MSGSTREAMOUT.INFOBJGENERIC generic = buildOutboundGeneric("UFISPOSUD", updateTime, fidsAfttab,
				"delete".equalsIgnoreCase(fidsAfttab.getPositionAction()) ? "update" : fidsAfttab.getPositionAction());

		// --- INFOBJ_ACPOSITION: เลือก arr/dep ตาม ADID
		MSG.MSGSTREAMOUT.INFOBJACPOSITION pos = new INFOBJACPOSITION();
		if (generic.getADID() == ADID.A) {
			INFOBJACPOSITION.ACPOSITIONARR arr = new INFOBJACPOSITION.ACPOSITIONARR();
			copyMatchingFields(fidsAfttab.getFieldsNotNull(), fidsAfttab, arr);
			if ("delete".equalsIgnoreCase(fidsAfttab.getPositionAction())) {
				arr.setPSTA(" ");
			} else if (fidsAfttab.getPsta() != null && !fidsAfttab.getPsta().trim().equalsIgnoreCase("")) {
				arr.setPSTA(formatEmpty(fidsAfttab.getPsta()));
				arr.setPABA(formatEmpty(fidsAfttab.getPaba()));
				arr.setPAEA(formatEmpty(fidsAfttab.getPaea()));
				arr.setPABS(formatEmpty(fidsAfttab.getPabs()));
				arr.setPAES(formatEmpty(fidsAfttab.getPaes()));
			}else{
				return null;
			}
			// sanitizeEmptyToNull(arr);
			pos.setACPOSITIONARR(arr);
		} else {
			INFOBJACPOSITION.ACPOSITIONDEP dep = new INFOBJACPOSITION.ACPOSITIONDEP();
			copyMatchingFields(fidsAfttab.getFieldsNotNull(), fidsAfttab, dep);
			if ("delete".equalsIgnoreCase(fidsAfttab.getPositionAction())) {
				dep.setPSTD(" ");
			} else if (fidsAfttab.getPstd() != null && !fidsAfttab.getPstd().trim().equalsIgnoreCase("")) {
				dep.setPSTD(formatEmpty(fidsAfttab.getPstd()));
				dep.setPDBA(formatEmpty(fidsAfttab.getPdba()));
				dep.setPDEA(formatEmpty(fidsAfttab.getPdea()));
				dep.setPDBS(formatEmpty(fidsAfttab.getPdbs()));
				dep.setPDES(formatEmpty(fidsAfttab.getPdes()));
			}else{
				return null;
			}
			// sanitizeEmptyToNull(dep);
			pos.setACPOSITIONDEP(dep);
		}

		if (!hasAnyData(pos)) {
			return null;
		}

		msgstreamout.setINFOBJGENERIC(generic);
		msgstreamout.setINFOBJACPOSITION(pos);
		esb.setMSGSTREAMOUT(msgstreamout);
		try {
			Marshaller marshaller = OUT_MSG_CTX.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			marshaller.marshal(esb, writer);
			return writer.toString();
		} catch (JAXBException e) {
			log.error("convertAcpositiontoEsb: ", e);
		}
		return null;
	}

	/**
	 * ส่ง acposition เข้าคิว UFIS_ACPOSITION_OUT_{HOPO}
	 */
	public void sendAcposition(String updateTime, FidsAfttab fidsAfttab) {
		if (fidsAfttab != null && fidsAfttab.getPositionAction() != null) {
			String xmlEsb = convertAcpositiontoEsb(updateTime, fidsAfttab);
			if (xmlEsb != null) {
				sendToOutboundQueue("UFIS_ACPOSITION_OUT", fidsAfttab.getHopo(), xmlEsb);
			}
		}
	}

	/**
	 * ปั้น XML สำหรับคิว UFIS_TRIGGER_OUT — VDGS (UFISVDGUD).
	 * INFOBJ_GENERIC ดึงจาก FidsAfttab (ดู {@link #buildOutboundGeneric}) —
	 * ACTIONTYPE เป็น
	 * U/I ปกติเหมือนคิวอื่น. INFOBJ_VDGS เลือก VDGSARR/VDGSDEP ตาม ADID
	 */
	public String convertVdgstoEsb(String updateTime, FidsAfttab fidsAfttab) {
		StringWriter writer = new StringWriter();
		MSG esb = new MSG();
		MSG.MSGSTREAMOUT msgstreamout = new MSGSTREAMOUT();

		MSG.MSGSTREAMOUT.INFOBJGENERIC generic = buildOutboundGeneric("UFISVDGUD", updateTime, fidsAfttab,
				fidsAfttab.getAction());

		// --- INFOBJ_VDGS: เลือก arr/dep ตาม ADID, field ค่าว่าง "" (รอ backend map)
		// ---
		MSG.MSGSTREAMOUT.INFOBJVDGS vdgs = new INFOBJVDGS();
		if (generic.getADID() == ADID.A) {
			INFOBJVDGS.VDGSARR arr = new INFOBJVDGS.VDGSARR();
			/*
			 * arr.setPSTA("");
			 * arr.setACT5("");
			 * arr.setFTYP("");
			 */
			copyMatchingFields(fidsAfttab.getFieldsNotNull(), fidsAfttab, arr);
			// sanitizeEmptyToNull(arr);
			vdgs.setVDGSARR(arr);
		} else {
			INFOBJVDGS.VDGSDEP dep = new INFOBJVDGS.VDGSDEP();
			/*
			 * dep.setPSTD("");
			 * dep.setACT5("");
			 * dep.setFTYP("");
			 * dep.setTIFD("");
			 */
			copyMatchingFields(fidsAfttab.getFieldsNotNull(), fidsAfttab, dep);
			// sanitizeEmptyToNull(dep);
			vdgs.setVDGSDEP(dep);
		}

		msgstreamout.setINFOBJGENERIC(generic);
		msgstreamout.setINFOBJVDGS(vdgs);
		esb.setMSGSTREAMOUT(msgstreamout);
		try {
			Marshaller marshaller = OUT_MSG_CTX.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			marshaller.marshal(esb, writer);
			return writer.toString();
		} catch (JAXBException e) {
			log.error("convertVdgstoEsb: ", e);
		}
		return null;
	}

	/**
	 * ส่ง VDGS เข้าคิว UFIS_TRIGGER_OUT_{HOPO}
	 */
	public void sendEmptyVdgs(String updateTime, FidsAfttab fidsAfttab) {
		String xmlEsb = convertVdgstoEsb(updateTime, fidsAfttab);
		if (xmlEsb != null) {
			sendToOutboundQueue("UFIS_TRIGGER_OUT", fidsAfttab.getHopo(), xmlEsb);
		}
	}

	/**
	 * ปั้น XML สำหรับคิว UFIS_OTHERS_OUT — towing (UFISTOWUD). ต่างจากคิวอื่นตรงที่
	 * payload
	 * อยู่ใต้ {@code CONCAT > TOWINGS} (ไม่ใช่ INFOBJ_XXX). INFOBJ_GENERIC ดึงจาก
	 * FidsAfttab
	 * (ดู {@link #buildOutboundGeneric}); TOWINGS (TOID/TWTP/SCHE) ค่าว่าง ""
	 * ไปก่อน (รอ backend map).
	 */
	public String convertTowingtoEsb(String updateTime, FidsAfttab fidsAfttab) {
		StringWriter writer = new StringWriter();
		MSG esb = new MSG();
		MSG.MSGSTREAMOUT msgstreamout = new MSGSTREAMOUT();

		MSG.MSGSTREAMOUT.INFOBJGENERIC generic = buildOutboundGeneric("UFISTOWUD", updateTime, fidsAfttab,
				fidsAfttab.getAction());

		// --- CONCAT/TOWINGS: ยังไม่มี source ใน FidsAfttab → ค่าว่าง "" (รอ backend
		// map) ---
		MSG.MSGSTREAMOUT.CONCAT concat = new CONCAT();
		CONCAT.TOWINGS towings = new CONCAT.TOWINGS();
		towings.setTOID(fidsAfttab.getToid());
		towings.setTWTP("T");
		towings.setSCHE("");
		towings.setSCHS("");
		concat.setTOWINGS(towings);

		msgstreamout.setINFOBJGENERIC(generic);
		msgstreamout.setCONCAT(concat);
		esb.setMSGSTREAMOUT(msgstreamout);
		try {
			Marshaller marshaller = OUT_MSG_CTX.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			marshaller.marshal(esb, writer);
			return writer.toString();
		} catch (JAXBException e) {
			log.error("convertTowingtoEsb: ", e);
		}
		return null;
	}

	/**
	 * ส่ง towing เข้าคิว UFIS_OTHERS_OUT_&lt;hopo&gt; (CONCAT/TOWINGS ยังว่างรอ
	 * backend map).
	 * เรียกจากภายนอกได้ — ยังไม่ได้ผูกเข้า flow convertXMLtoObject อัตโนมัติ.
	 */
	public void sendEmptyTowing(String updateTime, FidsAfttab fidsAfttab) {
		String xmlEsb = convertTowingtoEsb(updateTime, fidsAfttab);
		if (xmlEsb != null) {
			sendToOutboundQueue("UFIS_OTHERS_OUT", fidsAfttab.getHopo(), xmlEsb);
		}
	}

	/**
	 * ปั้น XML สำหรับคิว UFIS_OTHERS_OUT — SITA bulk file (UFISSITA).
	 * ต่างจากคิวอื่นตรงที่ SITA
	 * เป็นไฟล์ ไม่ผูกเที่ยวบิน → INFOBJ_GENERIC มีแค่ header ขั้นต่ำ (ไม่ใช้
	 * {@link #buildOutboundGeneric})
	 * และ payload เป็น {@code BULKDATA > SITA} (FILE_NAME + CONTENT
	 * ที่ส่งค่าจริงเข้ามา).
	 * หมายเหตุ: ACTIONTYPE = I (SITA เป็นไฟล์ใหม่) ตาม sample.
	 */
	public String convertSitatoEsb(String updateTime, String hopo, String fileName, String content) {
		StringWriter writer = new StringWriter();
		MSG esb = new MSG();
		MSG.MSGSTREAMOUT msgstreamout = new MSGSTREAMOUT();

		// INFOBJ_GENERIC — header ขั้นต่ำ (SITA ไม่ผูกเที่ยวบิน จึงไม่มี
		// URNO/ADID/FLNO/...)
		MSG.MSGSTREAMOUT.INFOBJGENERIC generic = new INFOBJGENERIC();
		generic.setMESSAGETYPE("UFISSITA");
		generic.setMESSAGEORIGIN("AOS");
		generic.setTIMEID(TIMEID.UTC);
		generic.setTIMESTAMP(updateTime);
		generic.setACTIONTYPE(ACTIONTYPE.I);
		generic.setHOPO(hopo);

		// BULKDATA/SITA — payload จริง (ไฟล์)
		MSG.MSGSTREAMOUT.BULKDATA bulkdata = new BULKDATA();
		BULKDATA.SITA sita = new BULKDATA.SITA();
		sita.setFILENAME(fileName);
		sita.setCONTENT(content);
		bulkdata.setSITA(sita);

		msgstreamout.setINFOBJGENERIC(generic);
		msgstreamout.setBULKDATA(bulkdata);
		esb.setMSGSTREAMOUT(msgstreamout);
		try {
			Marshaller marshaller = OUT_MSG_CTX.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			marshaller.marshal(esb, writer);
			return writer.toString();
		} catch (JAXBException e) {
			log.error("convertSitatoEsb: ", e);
		}
		return null;
	}

	/**
	 * ส่ง SITA bulk file เข้าคิว UFIS_OTHERS_OUT_{HOPO} (ใช้คิวร่วมกับ towing).
	 */
	public void sendSita(String updateTime, String hopo, String fileName, String content) {
		String xmlEsb = convertSitatoEsb(updateTime, hopo, fileName, content);
		if (xmlEsb != null) {
			sendToOutboundQueue("UFIS_OTHERS_OUT", hopo, xmlEsb);
		}
	}

	/**
	 * ส่งต่อ File Ready เข้าคิว UFIS_TRIGGER_OUT_&lt;hopo&gt; แบบ passthrough — รับ
	 * XML ที่ปั้นมา
	 * เรียบร้อยแล้วจากต้นทาง แล้วลงคิวต่อเลย ไม่ parse/ไม่ build/ไม่เก็บ backend
	 * (iFIMSConnect เป็น
	 * แค่ตัวกลางรับ-ส่งต่อ). ใช้คิวร่วมกับ VDGS.
	 *
	 * @param hopo   สำหรับ routing/ชื่อคิวเท่านั้น (payload ไม่มี hopo)
	 * @param xmlEsb XML File Ready ที่พร้อมส่ง (ปั้นมาจากต้นทางแล้ว)
	 */
	public void sendFileReady(String hopo, String xmlEsb) {
		if (xmlEsb != null) {
			sendToOutboundQueue("UFIS_TRIGGER_OUT", hopo, xmlEsb);
		}
	}

	/**
	 * สร้าง INFOBJ_GENERIC (ส่วนหัว) สำหรับ outbound message ทุกคิว โดยดึงค่าจาก
	 * {@link FidsAfttab}
	 * แบบเดียวกับ {@link #convertFidsAfftabtoEsb} — ต่างกันแค่ MESSAGETYPE ที่เป็น
	 * identity ของ
	 * แต่ละ message. MESSAGEORIGIN ใช้ "AOS" เหมือนกันทุกคิว.
	 */
	private MSG.MSGSTREAMOUT.INFOBJGENERIC buildOutboundGeneric(String messageType, String updateTime,
			FidsAfttab fidsAfttab, String action) {
		MSG.MSGSTREAMOUT.INFOBJGENERIC generic = new INFOBJGENERIC();
		generic.setMESSAGETYPE(messageType);
		generic.setMESSAGEORIGIN("AOS");
		generic.setTIMEID(TIMEID.UTC);
		generic.setTIMESTAMP(updateTime);
		generic.setACTIONTYPE(action.equalsIgnoreCase("insert") ? ACTIONTYPE.I
				: action.equalsIgnoreCase("update") ? ACTIONTYPE.U : ACTIONTYPE.D);
		generic.setHOPO(fidsAfttab.getHopo());

		if (!"UFISCCIUD".equalsIgnoreCase(messageType)) {
			generic.setURNO(fidsAfttab.getUrno() != null ? fidsAfttab.getUrno().toString() : null);
			generic.setADID(ADID.valueOf(fidsAfttab.getAdid()));
			generic.setSTDT(generic.getADID() == ADID.A ? fidsAfttab.getStoa() : fidsAfttab.getStod());
			generic.setRKEY(fidsAfttab.getRkey() != null ? fidsAfttab.getRkey().toString() : null);
			generic.setRTYP(fidsAfttab.getRtyp());

			if (!"D".equals(fidsAfttab.getFtyp())) {
				String flno = fidsAfttab.getFlno().trim();
				if (flno != null && !flno.isEmpty()) {
					Map<String, String> parts = tranformFidsAfttab.parseFlightNumber(flno);
					String newFlno = tranformFidsAfttab.toFlnoNonSuffix(parts).trim();
					generic.setFLNO(newFlno);
					if (!flno.equals(newFlno)) {
						String csgn = fidsAfttab.getCsgn();
						if (csgn != null && csgn.length() > 0) {
							generic.setCSGN(csgn.substring(0, csgn.length() - 1));
						}
					}else{
						generic.setCSGN(fidsAfttab.getCsgn());
					}
				}
			} else {
				generic.setFLNO(fidsAfttab.getFlno() != null ? fidsAfttab.getFlno().trim() : null);
				generic.setCSGN(fidsAfttab.getCsgn());
			}
		}
		return generic;
	}

	private boolean hasAnyData(Object obj) {
		if (obj == null) {
			return false;
		}

		// วนลูปอ่านทุก Field ใน Object (รวมถึง Object ซ้อน Object เช่น GATE -> GATEARR)
		for (Field field : obj.getClass().getDeclaredFields()) {
			field.setAccessible(true);
			try {
				Object value = field.get(obj);
				if (value != null) {
					// ถ้าเป็น String เช็กว่าไม่ว่าง
					if (value instanceof String) {
						if (!((String) value).isEmpty()) {
							return true; // เจอข้อมูลแล้ว
						}
					}
					// 2. ถ้าเป็น Data Type พื้นฐาน (Primitive / Wrapper / Enum / Date / Number)
					// ถือว่ามีข้อมูลทันที
					else if (isPrimitiveOrWrapper(value.getClass()) || value instanceof Enum<?>) {
						return true;
					}
					// 3. ถ้าเป็น Custom DTO Object ซ้อนข้างใน (เช่น GATEARR / GATEDEP) ค่อยลงไปเช็ก
					// Recursive
					else if (hasAnyData(value)) {
						return true;
					}
				}
			} catch (IllegalAccessException e) {
				log.error("hasAnyData error: ", e);
			}
		}
		return false; // ไม่พบข้อมูลใดๆ เลย
	}

	// Helper method เช็กว่าเป็น Class พื้นฐานหรือไม่
	private boolean isPrimitiveOrWrapper(Class<?> clazz) {
		return clazz.isPrimitive()
				|| Number.class.isAssignableFrom(clazz)
				|| Boolean.class.isAssignableFrom(clazz)
				|| Character.class.isAssignableFrom(clazz)
				|| java.util.Date.class.isAssignableFrom(clazz);
	}

	/**
	 * ส่ง XML ออก ESB สำหรับคิว outbound ใดๆ (flight/counter/gate/...) —
	 * เลือกช่องทางเดียวตาม
	 * flag webSphereEnabled:
	 * <ul>
	 * <li>true = WebSphere MQ, route ตาม hopo (BKK→machine1, อื่น→machine2);
	 * producer เก็บ log payload ลง outbound-&lt;queue&gt;.log ให้เอง</li>
	 * <li>false = webservice ({@link #callWebserviceUpdate}) + เก็บ log payload ลง
	 * outbound-WS</li>
	 * </ul>
	 *
	 * <p>
	 * ⚠️ caveat: callWebserviceUpdate ยิงไป endpoint ของ flight
	 * (AODB_FlightOutbound) —
	 * ถ้า webSphereEnabled=false ตอนส่ง counter/gate จะไป endpoint นี้ด้วย
	 * (ไม่ตรงชนิด message).
	 * ใน prod ที่ใช้ MQ (flag=true) ไม่มีปัญหา; ถ้าจะใช้ WS จริงกับคิวอื่นต้องเพิ่ม
	 * endpoint แยก.
	 *
	 * @param queueBase ชื่อคิวฐาน (ไม่ต้องมี suffix hopo) เช่น "UFIS_GATE_OUT" —
	 *                  method จะต่อ
	 *                  "_&lt;HOPO&gt;" ให้เอง เป็น "UFIS_GATE_OUT_BKK"
	 */
	private void sendToOutboundQueue(String queueBase, String hopo, String xmlEsb) {
		String queueName = queueBase + "_" + hopo;
		if (webSphereEnabled) {
			if (hopo.equalsIgnoreCase("BKK")) {
				webSphereProducer.sendToMachine1(queueName, hopo, xmlEsb);
			} else {
				webSphereProducer.sendToMachine2(queueName, hopo, xmlEsb);
			}
		} else {
			// webSphereEnabled=false → ส่งผ่าน webservice แทน (เก็บ payload ลง outbound
			// ด้วย)
			MDC.put("sendEsbKey", hopo + "/outbound-WS");
			try {
				sendEsbLog.info(xmlEsb);
			} finally {
				MDC.remove("sendEsbKey");
			}
			callWebserviceUpdate(xmlEsb);
		}
	}

	/*
	 * public void callWebserviceResponse(String xmlEsb) {
	 * // Create SOAP Connection
	 * SOAPConnectionFactory soapConnectionFactory;
	 * try {
	 * soapConnectionFactory = SOAPConnectionFactory.newInstance();
	 * 
	 * SOAPConnection soapConnection = soapConnectionFactory.createConnection();
	 * 
	 * // Define the endpoint URL
	 * String url =
	 * "http://esbv10:5555/ws/IFIMS.Service.CommonService:BKK_IFIMS_MessageResponse_WSD/IFIMS_Service_CommonService_BKK_IFIMS_MessageResponse_WSD_Port";
	 * 
	 * // Create the SOAP Request
	 * SOAPMessage soapRequest = createSoapRequest(xmlEsb, url,
	 * "BKK_IFIMS_MessageResponse");
	 * 
	 * // Send request and receive response
	 * SOAPMessage soapResponse = soapConnection.call(soapRequest, url);
	 * 
	 * // Print response
	 * // System.out.println("Response SOAP Message:");
	 * // soapResponse.writeTo(System.out);
	 * 
	 * soapConnection.close();
	 * } catch (UnsupportedOperationException e) {
	 * log.error("callWebserviceResponse: ", e);
	 * // e.printStackTrace();
	 * } catch (SOAPException e) {
	 * log.error("callWebserviceResponse: ", e);
	 * // e.printStackTrace();
	 * } catch (Exception e) {
	 * log.error("callWebserviceResponse: ", e);
	 * // e.printStackTrace();
	 * }
	 * }
	 */

	public void callWebserviceUpdate(String xmlEsb) {
		// Create SOAP Connection
		SOAPConnectionFactory soapConnectionFactory;
		try {
			soapConnectionFactory = SOAPConnectionFactory.newInstance();

			SOAPConnection soapConnection = soapConnectionFactory.createConnection();

			// Define the endpoint URL
			String url = "http://esbv10:5555/ws/IFIMS.Service.FlowService.Online.Publish.BKK:AODB_FlightOutbound_WSD/IFIMS_Service_FlowService_Online_Publish_BKK_AODB_FlightOutbound_WSD_Port";

			// Create the SOAP Request
			SOAPMessage soapRequest = createSoapRequest(xmlEsb, url, "AODB_FlightOutbound");

			// Send request and receive response
			SOAPMessage soapResponse = soapConnection.call(soapRequest, url);

			// Print response
			// System.out.println("Response SOAP Message:");
			// soapResponse.writeTo(System.out);

			soapConnection.close();
		} catch (UnsupportedOperationException e) {
			log.error("callWebserviceUpdate: ", e);
			// e.printStackTrace();
		} catch (SOAPException e) {
			log.error("callWebserviceUpdate: ", e);
			// e.printStackTrace();
		} catch (Exception e) {
			log.error("callWebserviceUpdate: ", e);
			// e.printStackTrace();
		}
	}

	private SOAPMessage createSoapRequest(String xmlEsb, String url, String elementName) throws Exception {
		// Create message
		MessageFactory messageFactory = MessageFactory.newInstance();
		SOAPMessage soapMessage = messageFactory.createMessage();

		// SOAP Envelope
		SOAPPart soapPart = soapMessage.getSOAPPart();
		SOAPEnvelope envelope = soapPart.getEnvelope();

		String namespace = "tns";
		envelope.addNamespaceDeclaration(namespace, url);

		// SOAP Body
		SOAPBody soapBody = envelope.getBody();
		SOAPElement responseElement = soapBody.addChildElement(elementName, namespace);
		SOAPElement inputMsg = responseElement.addChildElement("inputMsg");
		inputMsg.addTextNode(xmlEsb);
		soapMessage.saveChanges();
		soapMessage.writeTo(System.out);
		return soapMessage;
	}

	private void sendEmailResponse(String xmlEsb, String hopo) {
		String host = "192.168.10.11";
		String port = "1025";
		String toEmail = "pocwm1@esbv10.co.th";
		String fromEmail = "ifimsconnect@sfis.co.th";

		Properties properties = new Properties();
		properties.put("mail.smtp.host", host);
		properties.put("mail.smtp.port", port);
		properties.put("mail.smtp.auth", "false"); // หาก SMTP 1025 ไม่ต้องล็อกอิน
		properties.put("mail.smtp.starttls.enable", "false"); // สำหรับพอร์ต 1025 (MailHog/Dev Server) มักจะไม่ใช้ TLS

		Session session = Session.getInstance(properties);

		try {
			MimeMessage message = new MimeMessage(session);
			message.addRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
			message.setSubject("NACK - Error during process " + hopo + " AODB inbound message");
			message.setFrom(new InternetAddress(fromEmail, "iFIMSConnect", "UTF-8"));

			// ส่งเนื้อหา XML เข้าไปใน Body ของอีเมล
			message.setText(xmlEsb, "UTF-8", "xml");

			Transport.send(message);
			log.info("Successfully sent NACK email to: {}", toEmail);
		} catch (MessagingException e) {
			log.error("Failed to send NACK email", e);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	public void copyMatchingFields(List<String> updateFields, Object source, Object target) {
		if (source == null || target == null) {
			log.warn("copyMatchingFields: source or target is null");
			return;
		}

		// ป้องกัน NPE ถ้า updateFields เป็น null ให้มองเป็น List ว่าง
		List<String> safeUpdateFields = (updateFields != null) ? updateFields : Collections.emptyList();

		Class<?> sourceClass = source.getClass();
		Class<?> targetClass = target.getClass();

		while (sourceClass != null && sourceClass != Object.class) {
			for (Field sourceField : sourceClass.getDeclaredFields()) {
				sourceField.setAccessible(true);
				try {
					Object value = sourceField.get(source);

					// เช็กว่า value มีค่า และชื่อฟิลด์อยู่ใน updateFields หรือไม่
					if (value != null && isFieldInUpdateList(safeUpdateFields, sourceField.getName())) {

						// ค้นหา Target Field แบบ Case-Insensitive เผื่อ JAXB เจนชื่อตัวพิมพ์ต่างกัน
						Field targetField = findTargetField(targetClass, sourceField.getName());

						if (targetField != null) {
							targetField.setAccessible(true);

							Object spaceValue = value;
							if (value instanceof String) {
								String strVal = (String) value;
								if (strVal.isEmpty()) {
									spaceValue = " "; // แปลกค่าจาก "" เป็น 1 Space
								}
							}

							// กรณี Type เดียวกันเป๊ะ
							if (targetField.getType().equals(sourceField.getType())) {
								targetField.set(target, spaceValue);
							}
							// กรณี Target เป็น String แต่ Source เป็นประเภทอื่น (ให้สั่ง toString())
							else if (targetField.getType().equals(String.class)) {
								targetField.set(target, spaceValue.toString());
							}
						}
					}
				} catch (IllegalAccessException e) {
					log.error("copyMatchingFields access error on field {}: ", sourceField.getName(), e);
				}
			}
			sourceClass = sourceClass.getSuperclass();
		}
	}

	/**
	 * เช็กว่า fieldName อยู่ใน updateFields หรือไม่ โดยไม่สนใจตัวพิมพ์เล็ก-ใหญ่
	 */
	private boolean isFieldInUpdateList(List<String> updateFields, String fieldName) {
		for (String field : updateFields) {
			if (field.equalsIgnoreCase(fieldName)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Helper ค้นหา Field แบบไม่สนใจตัวพิมพ์เล็ก-ใหญ่ (Case-Insensitive)
	 */
	private Field findTargetField(Class<?> targetClass, String fieldName) {
		for (Field field : targetClass.getDeclaredFields()) {
			if (field.getName().equalsIgnoreCase(fieldName)) {
				return field;
			}
		}
		return null;
	}

	private <T> T sanitizeEmptyToNull(T object) {
		if (object == null)
			return null;

		for (Field field : object.getClass().getDeclaredFields()) {
			if (field.getType().equals(String.class)) {
				field.setAccessible(true);
				try {
					String value = (String) field.get(object);
					if (value != null && value.trim().isEmpty()) {
						field.set(object, null); // เปลี่ยน "" หรือ " " ให้เป็น null
					}
				} catch (IllegalAccessException e) {
					log.error("Error sanitizing field: {}", field.getName(), e);
				}
			}
		}
		return object;
	}

	private String formatEmpty(String val) {
		return (val == null || val.trim().isEmpty()) ? " " : val.trim();
	}

	public String getContentBody(String xml) {
		String startTag = "<pl_turn>";
		String endTag = "</pl_turn>";

		int start = xml.indexOf(startTag);
		int end = xml.indexOf(endTag);

		if (start != -1 && end != -1) {
			return xml.substring(start, end + endTag.length()).replaceAll("(?m)^[ \t]*\r?\n", "");
		} else {
			return null;
		}
	}
}