<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:xs="http://www.w3.org/2001/XMLSchema"
	xmlns:soap-env="http://schemas.xmlsoap.org/soap/envelope/"
	xmlns:custom="http://example.com/custom-functions"
	exclude-result-prefixes="soap-env custom xs">

	<xsl:output method="xml" indent="yes" encoding="UTF-8"/>
	<!-- syncMode: DATASET = emit every mapped field; UPDATE = emit only when descendant @action is insert/update -->
	<xsl:param name="syncMode" select="'UPDATE'"/>
	<!-- adidMode: A = produce arrival record; D = produce departure record -->
	<xsl:param name="adidMode" select="'A'"/>
	<xsl:param name="originator" select="''"/>

	<!-- ฟังก์ชันแปลง Date String -->
    <xsl:function name="custom:convertDate" as="xs:string?">
		<xsl:param name="rawInput" as="item()*"/>
        <xsl:variable name="input" select="string($rawInput[1])"/>
        
        <!-- กำหนด Regex ให้ตรงกับ ISO_8601_Z เช่น 2026-08-11T09:47:30Z -->
        <xsl:variable name="isoPattern" select="'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$'"/>
        
        <xsl:choose>
            <xsl:when test="empty($input) or $input = ''">
                <xsl:sequence select="()"/>
            </xsl:when>
            <xsl:when test="matches($input, $isoPattern)">
                <xsl:value-of select="replace($input, '[-:TZ]', '')"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$input"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:function>
	<!-- ============================================================
	     HELPER TEMPLATES
	     ============================================================ -->

	<!-- Action-filtered emit. Skip when not DATASET and no descendant action=insert/update, or value is empty.
	     HOLD values are passed through so FieldInspector.replaceHoldWithEmpty can normalise them to " " in Java
	     (matches legacy setter-map behaviour).
	     The "node" parameter is supplied via <xsl:with-param><xsl:copy-of select="//path"/></xsl:with-param>, so
	     $node is a result-tree fragment whose immediate children are the copies of every matching element.
	     We pick the first child with non-empty text so multi-match XPaths (e.g. several pl_stand under pl_stand_list)
	     yield the same value the legacy Java code obtained via string(//path/text()[1]). -->
	<xsl:template name="getValue">
		<xsl:param name="tagName"/>
		<xsl:param name="node"/>
		<xsl:param name="forceEmit" select="false()"/>

		<!-- ดึงค่า Text และ ค่า action จาก $node -->
		<xsl:variable name="act" select="$node//@action"/>

		<!-- เช็ก เงื่อนไขการแสดงผล -->
		<xsl:variable name="isSetting">
			<xsl:choose>
				<xsl:when test="$forceEmit">true</xsl:when>
				<xsl:when test="$syncMode = 'DATASET'">true</xsl:when>
				<xsl:when test="$act = 'insert' or $act = 'update' or $act = 'delete'">true</xsl:when>
				<xsl:otherwise>false</xsl:otherwise>
			</xsl:choose>
		</xsl:variable>

		<!-- พ่น Tag ออกมาเพียงครั้งเดียว -->
		<xsl:if test="$isSetting = 'true'">
			<xsl:element name="{$tagName}">
				
				<!-- ใส่ attribute action ออกมาด้วย (ถ้ามี) -->
				<xsl:if test="string($act) != ''">
					<xsl:attribute name="action">
						<xsl:value-of select="$act"/>
					</xsl:attribute>
				</xsl:if>

				<xsl:variable name="val" select="normalize-space($node)"/>
            	<xsl:value-of select="$val"/>
			</xsl:element>
		</xsl:if>
	</xsl:template>

	<xsl:template match="/">
		<xsl:choose>
            <!-- กรณีเป็น Flight Mode (มี pl_turn) -->
            <xsl:when test="//pl_turn">
				<FidsAfttab>

					<!-- ============================================================
						ACTION-FILTERED FIXED IDENTITY FIELDS
						Note: URNO/RKEY/SIBT/SOBT/FLNO/CSGN/FLTI/ALC2/ALC3 also have an
						unconditional Java fallback in TranformFidsAfttab.applyFixedPaths
						so they are still set on UPDATE messages without descendant
						action=insert/update. Tracked in fieldsNotNull only when XSL
						emits them here (matches original setter map behaviour).
						============================================================ -->

					<xsl:variable name="urnoNode" select="
						if ($adidMode = 'A') then //pl_arrival/pa_idseq 
						else //pl_departure/pd_idseq"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'urno'"/>
						<xsl:with-param name="forceEmit" select="true()"/>
						<xsl:with-param name="node">
							<field action="{$urnoNode/@action}">
								<xsl:value-of select="$urnoNode"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="rkeyNode" select="//pl_turn/pt_idseq"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'rkey'"/>
						<xsl:with-param name="forceEmit" select="true()"/>
						<xsl:with-param name="node">
							<field action="{$rkeyNode/@action}">
								<xsl:value-of select="$rkeyNode"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'aurn'"/>
						<xsl:with-param name="node">
							<field action="{$rkeyNode/@action}">
								<xsl:value-of select="custom:convertDate($rkeyNode)"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="flnoNode" select="
						if ($adidMode = 'A') then //pl_arrival/pa_flightnumber 
						else //pl_departure/pd_flightnumber"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'flno'"/>
						<xsl:with-param name="forceEmit" select="true()"/>
						<xsl:with-param name="node">
							<field action="{$flnoNode/@action}">
								<xsl:value-of select="$flnoNode"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="csgnNode" select="
						if ($adidMode = 'A') then //pl_arrival/pa_callsign 
						else //pl_departure/pd_callsign"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'csgn'"/>
						<xsl:with-param name="forceEmit" select="true()"/>
						<xsl:with-param name="node">
							<field action="{$csgnNode/@action}">
								<xsl:value-of select="$csgnNode"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="fltiNode" select="
						if ($adidMode = 'A') then //pl_arrival/pa_rctt_countrytype 
						else //pl_departure/pd_rctt_countrytype"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'flti'"/>
						<xsl:with-param name="forceEmit" select="true()"/>
						<xsl:with-param name="node">
							<field action="{$fltiNode/@action}">
								<xsl:value-of select="$fltiNode/ref_countrytype/rctt_code"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="alc2Node" select="
						if ($adidMode = 'A') then //pl_arrival/pa_ral_airline 
						else //pl_departure/pd_ral_airline"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'alc2'"/>
						<xsl:with-param name="forceEmit" select="true()"/>
						<xsl:with-param name="node">
							<field action="{$alc2Node/@action}">
								<xsl:value-of select="$alc2Node/ref_airline/ral_2lc"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="alc3Node" select="
						if ($adidMode = 'A') then //pl_arrival/pa_ral_airline 
						else //pl_departure/pd_ral_airline"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'alc3'"/>
						<xsl:with-param name="forceEmit" select="true()"/>
						<xsl:with-param name="node">
							<field action="{$alc3Node/@action}">
								<xsl:value-of select="$alc3Node/ref_airline/ral_3lc"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="sibtSobtNode" select="
						if ($adidMode = 'A') then //pl_arrival/pa_sibt
						else if ($adidMode = 'D') then //pl_departure/pd_sobt
						else ()"/>
					<xsl:if test="exists($sibtSobtNode)">
						<xsl:variable name="formattedDate" select="custom:convertDate($sibtSobtNode)"/>
						<xsl:variable name="actionAttr" select="$sibtSobtNode/@action"/>
						<!-- 1. sibt หรือ sobt -->
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="if ($adidMode = 'A') then 'sibt' else 'sobt'"/>
							<xsl:with-param name="forceEmit" select="true()"/>
							<xsl:with-param name="node">
								<field action="{$actionAttr}">
									<xsl:value-of select="$formattedDate"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<!-- 2. stoa หรือ stod -->
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="if ($adidMode = 'A') then 'stoa' else 'stod'"/>
							<xsl:with-param name="forceEmit" select="true()"/>
							<xsl:with-param name="node">
								<field action="{$actionAttr}">
									<xsl:value-of select="$formattedDate"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
					</xsl:if>

					<!-- RTYP is computed in Java (TranformFidsAfttab.buildFlight) after fieldsNotNull capture,
						so it does not pollute the change-tracking list. -->

					<!-- ============================================================
						ACTION-FILTERED COMMON FIELDS (both A and D variants)
						============================================================ -->
					<xsl:variable name="jfnoNode" select="
						if ($adidMode = 'A') then //pl_arrival/pa_codeshareflightnumbers 
						else //pl_departure/pd_codeshareflightnumbers"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'jfno'"/>
						<xsl:with-param name="node">
							<field action="{$jfnoNode/@action}">
								<xsl:value-of select="$jfnoNode"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="stypNode" select="
						if ($adidMode = 'A') then //pl_arrival/pa_rstc_servicetypecode 
						else //pl_departure/pd_rstc_servicetypecode"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'styp'"/>
						<xsl:with-param name="node">
							<field action="{$stypNode/@action}">
								<xsl:value-of select="$stypNode"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="act3Node" select="
						if ($adidMode = 'A') then //pl_arrival/pa_ract_aircrafttype 
						else //pl_departure/pd_ract_aircrafttype"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'act3'"/>
						<xsl:with-param name="node">
							<field action="{$act3Node/@action}">
								<xsl:value-of select="$act3Node/ref_aircrafttype/ract_iatatype"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="act5Node" select="
						if ($adidMode = 'A') then //pl_arrival/pa_ract_aircrafttype 
						else //pl_departure/pd_ract_aircrafttype"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'act5'"/>
						<xsl:with-param name="node">
							<field action="{$act5Node/@action}">
								<xsl:value-of select="$act5Node/ref_aircrafttype/ract_icaotype"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="actiNode" select="
						if ($adidMode = 'A') then //pl_arrival/pa_ract_aircrafttype 
						else //pl_departure/pd_ract_aircrafttype"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'acti'"/>
						<xsl:with-param name="node">
							<field action="{$actiNode/@action}">
								<xsl:value-of select="$actiNode/ref_aircrafttype/ract_internalcode"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="regnNode" select="
						if ($adidMode = 'A') then //pl_arrival/pa_registration 
						else //pl_departure/pd_registration"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'regn'"/>
						<xsl:with-param name="node">
							<field action="{$regnNode/@action}">
								<xsl:value-of select="$regnNode"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="ttypNode" select="
						if ($adidMode = 'A') then //pl_arrival/pa_rnc_naturecode 
						else //pl_departure/pd_rnc_naturecode"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'ttyp'"/>
						<xsl:with-param name="node">
							<field action="{$ttypNode/@action}">
								<xsl:value-of select="$ttypNode"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="mtowNode" select="
						if ($adidMode = 'A') then //pl_arrival/pa_rac_aircraft/ref_aircraft/rac_mtow 
						else //pl_departure/pd_rac_aircraft/ref_aircraft/rac_mtow"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'mtow'"/>
						<xsl:with-param name="node">
							<field action="{$mtowNode/@action}">
								<xsl:value-of select="$mtowNode"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="rempNode" select="
						if ($adidMode = 'A') then //pl_arrival/pa_rfst_refflightstatus/ref_flightstatus/rfst_code3l 
						else //pl_departure/pd_rfst_refflightstatus/ref_flightstatus/rfst_code3l"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'remp'"/>
						<xsl:with-param name="forceEmit" select="true()"/>
						<xsl:with-param name="node">
							<field action="{$rempNode/@action}">
								<xsl:call-template name="transformRempValue">
									<xsl:with-param name="val" select="$rempNode"/>
								</xsl:call-template>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="lastStand" select="//pl_turn/pl_stand_list/pl_stand[last()]"/>
					<xsl:variable name="m" select="if ($adidMode = 'D') then 'd' else 'a'"/>
					<xsl:variable name="deletedPosition" select="
						some $s in $lastStand satisfies (
							let $pst := $s/pst_rsta_stand
							return lower-case($s/@action) = 'delete'
								or lower-case($pst/@action) = 'delete'
								or (upper-case(normalize-space($pst)) = 'HOLD' 
									and $pst/@old 
									and upper-case(normalize-space($pst/@old)) != 'HOLD')
						)"/>
					<xsl:variable name="hasStandChange" select="
						$lastStand/@action or 
						$lastStand/@old or 
						$lastStand/*/@action or 
						$lastStand/*/@old"/>

					<!-- 1. คำนวณ positionAction (insert / update / delete / none) -->
					<xsl:variable name="positionAction">
						<xsl:choose>
							<!-- กรณีไม่มีหลุมจอด หรือถูกเปลี่ยนเป็น HOLD หรือโดน action DELETE -> ให้เป็น delete -->
							<xsl:when test="$deletedPosition">
								<xsl:text>delete</xsl:text>
							</xsl:when>
							<xsl:when test="count($lastStand) = 0 or not($hasStandChange)">
								<xsl:text>none</xsl:text>
							</xsl:when>
							<!-- กรณีเปลี่ยนจาก HOLD มาเป็นหลุมจอดใหม่ -> เป็น insert -->
							<xsl:when test="count($lastStand) = 1 and upper-case(normalize-space($lastStand[1]/pst_rsta_stand/@old)) = 'HOLD'">
								<xsl:text>insert</xsl:text>
							</xsl:when>
							<!-- กรณีอื่นๆ มีข้อมูลหลุมจอด -> เป็น update -->
							<xsl:otherwise>
								<xsl:text>update</xsl:text>
							</xsl:otherwise>
						</xsl:choose>
					</xsl:variable>
					
					<!-- 🛑 ดักเงื่อนไข: ทำงานเฉพาะเมื่อมีข้อมูล และ pst_rsta_stand ไม่เท่ากับ 'HOLD' -->
					<xsl:if test="$positionAction != 'none'">
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'positionAction'"/>
							<xsl:with-param name="node">
								<field action="{$positionAction}">
									<xsl:value-of select="$positionAction"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
						<xsl:variable name="standAction" select="$lastStand/*/@action"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'toid'"/>
							<xsl:with-param name="node">
								<field action="{$lastStand/pst_idseq/@action}">
									<xsl:value-of select="$lastStand/pst_idseq"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="concat('pst', $m)"/>
							<xsl:with-param name="forceEmit" select="true()"/>
							<xsl:with-param name="node">
								<field action="{$standAction[1]}">
									<xsl:value-of select="$lastStand/pst_rsta_stand"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="concat('p', $m, 'bs')"/>
							<xsl:with-param name="forceEmit" select="true()"/>
							<xsl:with-param name="node">
								<field action="{$lastStand/pst_beginplan/@action}">
									<xsl:value-of select="custom:convertDate($lastStand/pst_beginplan)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="concat('p', $m, 'es')"/>
							<xsl:with-param name="forceEmit" select="true()"/>
							<xsl:with-param name="node">
								<field action="{$lastStand/pst_endplan/@action}">
									<xsl:value-of select="custom:convertDate($lastStand/pst_endplan)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="concat('p', $m, 'ba')"/>
							<xsl:with-param name="forceEmit" select="true()"/>
							<xsl:with-param name="node">
								<field action="{$lastStand/pst_beginactual/@action}">
									<xsl:value-of select="custom:convertDate($lastStand/pst_beginactual)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="concat('p', $m, 'ea')"/>
							<xsl:with-param name="forceEmit" select="true()"/>
							<xsl:with-param name="node">
								<field action="{$lastStand/pst_endactual/@action}">
									<xsl:value-of select="custom:convertDate($lastStand/pst_endactual)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
					</xsl:if>
					
					<xsl:variable name="pstOrderNode" select="$lastStand/pst_towindicator"/>
					<xsl:variable name="pstOrder" select="normalize-space($pstOrderNode)"/>
					<xsl:variable name="ftypValue" select="if ($pstOrder = '1' or $pstOrder = '50' or $pstOrder = '99') then 'T' else '-'"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'ftyp'"/>
						<xsl:with-param name="forceEmit" select="true()"/>
						<xsl:with-param name="node">
							<field action="{$pstOrderNode/@action}">
								<xsl:value-of select="$ftypValue"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="tifdNode" select="
						if ($adidMode = 'A') then //pl_arrival/pa_bibt 
						else //pl_departure/pd_bobt"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'tifd'"/>
						<xsl:with-param name="node">
							<field action="{$tifdNode/@action}">
								<xsl:value-of select="custom:convertDate($tifdNode)"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="bagnNode" select="
						if ($adidMode = 'A') then //pl_arrival/pl_arrivalloadstatistics_list/pl_arrivalloadstatistics/pals_baggagecount 
						else //pl_departure/pl_departureloadstatistics_list/pl_departureloadstatistics/pdls_baggagecount"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'bagn'"/>
						<xsl:with-param name="node">
							<field action="{$bagnNode/@action}">
								<xsl:value-of select="$bagnNode"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="bagsNode" select="
						if ($adidMode = 'A') then //pl_arrival/pl_arrivalloadstatistics_list/pl_arrivalloadstatistics/pals_baggagecount 
						else //pl_departure/pl_departureloadstatistics_list/pl_departureloadstatistics/pdls_baggagecount"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'bags'"/>
						<xsl:with-param name="node">
							<field action="{$bagsNode/@action}">
								<xsl:value-of select="$bagsNode"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="bagwNode" select="
						if ($adidMode = 'A') then //pl_arrival/pl_arrivalloadstatistics_list/pl_arrivalloadstatistics/pals_baggageweight 
						else //pl_departure/pl_departureloadstatistics_list/pl_departureloadstatistics/pdls_baggageweight"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'bagw'"/>
						<xsl:with-param name="node">
							<field action="{$bagwNode/@action}">
								<xsl:value-of select="$bagwNode"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="cdatNode" select="
						if ($adidMode = 'A') then //pl_arrival/pa_createtime 
						else //pl_departure/pd_createtime"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'cdat'"/>
						<xsl:with-param name="node">
							<field action="{$cdatNode/@action}">
								<xsl:value-of select="custom:convertDate($cdatNode)"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="cgotNode" select="
						if ($adidMode = 'A') then //pl_arrival/pl_arrivalloadstatistics_list/pl_arrivalloadstatistics/pals_cargoweight 
						else //pl_departure/pl_departureloadstatistics_list/pl_departureloadstatistics/pdls_cargoweight"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'cgot'"/>
						<xsl:with-param name="node">
							<field action="{$cgotNode/@action}">
								<xsl:value-of select="$cgotNode"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="dcd1Node" select="
						if ($adidMode = 'A') then //pl_arrival/pa_delayreasons 
						else //pl_departure/pd_delayreasons"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'dcd1'"/>
						<xsl:with-param name="node">
							<field action="{$dcd1Node/@action}">
								<xsl:value-of select="$dcd1Node"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="dcd2Node" select="
						if ($adidMode = 'A') then //pl_arrival/pa_delayreasons 
						else //pl_departure/pd_delayreasons"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'dcd2'"/>
						<xsl:with-param name="node">
							<field action="{$dcd2Node/@action}">
								<xsl:value-of select="$dcd2Node"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="hdllNode" select="
						if ($adidMode = 'A') then //pl_arrival/pl_handlingagent_list/pl_handlingagent/pha_rha_handlingagent 
						else //pl_departure/pl_handlingagent_list/pl_handlingagent/pha_rha_handlingagent"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'hdll'"/>
						<xsl:with-param name="node">
							<field action="{$hdllNode/@action}">
								<xsl:value-of select="$hdllNode"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="lstuNode" select="
						if ($adidMode = 'A') then //pl_arrival/pa_modtime 
						else //pl_departure/pd_modtime"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'lstu'"/>
						<xsl:with-param name="node">
							<field action="{$lstuNode/@action}">
								<xsl:value-of select="$lstuNode"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="mailNode" select="
						if ($adidMode = 'A') then //pl_arrival/pl_arrivalloadstatistics_list/pl_arrivalloadstatistics/pals_mailweight 
						else //pl_departure/pl_departureloadstatistics_list/pl_departureloadstatistics/pdls_mailweight"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'mail'"/>
						<xsl:with-param name="node">
							<field action="{$mailNode/@action}">
								<xsl:value-of select="$mailNode"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="noseNode" select="
						if ($adidMode = 'A') then //pl_arrival/pa_seats 
						else //pl_departure/pd_seats"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'nose'"/>
						<xsl:with-param name="node">
							<field action="{$noseNode/@action}">
								<xsl:value-of select="$noseNode"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<!-- nxti -->
					<xsl:variable name="nxtiNode" select="
						if ($adidMode = 'A') then //pl_arrival/pa_nextinfotime 
						else //pl_departure/pd_nextinfotime"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'nxti'"/>
						<xsl:with-param name="node">
							<field action="{$nxtiNode/@action}">
								<xsl:value-of select="$nxtiNode"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="paidNode" select="
						if ($adidMode = 'A') then //pl_arrival/pa_cashind 
						else //pl_departure/pd_cashind"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'paid'"/>
						<xsl:with-param name="node">
							<field action="{$paidNode/@action}">
								<xsl:value-of select="$paidNode"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="pax1Node" select="
						if ($adidMode = 'A') then //pl_arrival/pl_arrivalloadstatistics_list/pl_arrivalloadstatistics/pals_paxf 
						else //pl_departure/pl_departureloadstatistics_list/pl_departureloadstatistics/pdls_paxf"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'pax1'"/>
						<xsl:with-param name="node">
							<field action="{$pax1Node/@action}">
								<xsl:value-of select="$pax1Node"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="pax2Node" select="
						if ($adidMode = 'A') then //pl_arrival/pl_arrivalloadstatistics_list/pl_arrivalloadstatistics/pals_paxc 
						else //pl_departure/pl_departureloadstatistics_list/pl_departureloadstatistics/pdls_paxc"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'pax2'"/>
						<xsl:with-param name="node">
							<field action="{$pax2Node/@action}">
								<xsl:value-of select="$pax2Node"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="pax3Node" select="
						if ($adidMode = 'A') then //pl_arrival/pl_arrivalloadstatistics_list/pl_arrivalloadstatistics/pals_paxy 
						else //pl_departure/pl_departureloadstatistics_list/pl_departureloadstatistics/pdls_paxy"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'pax3'"/>
						<xsl:with-param name="node">
							<field action="{$pax3Node/@action}">
								<xsl:value-of select="$pax3Node"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="paxfNode" select="
						if ($adidMode = 'A') then //pl_arrival/pa_transferpax 
						else //pl_departure/pd_transferpax"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'paxf'"/>
						<xsl:with-param name="node">
							<field action="{$paxfNode/@action}">
								<xsl:value-of select="$paxfNode"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="paxiNode" select="
						if ($adidMode = 'A') then //pl_arrival/pa_transitpax 
						else //pl_departure/pd_transitpax"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'paxi'"/>
						<xsl:with-param name="node">
							<field action="{$paxiNode/@action}">
								<xsl:value-of select="$paxiNode"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="paxtNode" select="
						if ($adidMode = 'A') then //pl_arrival/pa_totalpax 
						else //pl_departure/pd_totalpax"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'paxt'"/>
						<xsl:with-param name="node">
							<field action="{$paxtNode/@action}">
								<xsl:value-of select="$paxtNode"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="ssrcNode" select="
						if ($adidMode = 'A') then //pl_arrival/paa_ssrcode 
						else //pl_departure/pad_ssrcode"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'ssrc'"/>
						<xsl:with-param name="node">
							<field action="{$ssrcNode/@action}">
								<xsl:value-of select="$ssrcNode"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="trknNode" select="
						if ($adidMode = 'A') then //pl_arrival/pa_externalflightnumber 
						else //pl_departure/pd_externalflightnumber"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'trkn'"/>
						<xsl:with-param name="node">
							<field action="{$trknNode/@action}">
								<xsl:value-of select="
									if (string-length($trknNode) > 4) 
									then substring($trknNode, 3) 
									else $trknNode"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="useuNode" select="
						if ($adidMode = 'A') then //pl_arrival/pa_moduser 
						else //pl_departure/pd_moduser"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'useu'"/>
						<xsl:with-param name="node">
							<field action="{$useuNode/@action}">
								<xsl:value-of select="$useuNode"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>

					<xsl:variable name="etotNode" select="//pl_departure/pd_etot"/>
					<xsl:call-template name="getValue">
						<xsl:with-param name="tagName" select="'etot'"/>
						<xsl:with-param name="node">
							<field action="{$etotNode/@action}">
								<xsl:value-of select="custom:convertDate($etotNode)"/>
							</field>
						</xsl:with-param>
					</xsl:call-template>
					
					<!-- Gates -->
					<xsl:variable name="gate" select="
						if ($adidMode = 'A') then //pl_arrivalgate_list/pl_arrivalgate 
						else //pl_departuregate_list/pl_departuregate"/>

					<xsl:variable name="lstGate" select="
						$gate[
							let $gtd := if ($adidMode = 'A') then pag_rgt_gate else pdg_rgt_gate
							return upper-case(normalize-space($gtd)) != 'HOLD' and not(@action = 'delete' or $gtd/@action = 'delete')
						]"/>
					<!-- เช็กว่า Gate เป็นเคส Delete หรือไม่ โดยต้องมีค่า old ที่ไม่ใช่ HOLD และค่าใหม่ต้องเป็น HOLD -->
					<xsl:variable name="deletedGate" select="
						some $g in $gate satisfies (
							let $gtd := if ($adidMode = 'A') then $g/pag_rgt_gate else $g/pdg_rgt_gate
							return $g/@action = 'delete' 
								or $gtd/@action = 'delete'
								or (upper-case(normalize-space($gtd)) = 'HOLD' 
									and $gtd/@old 
									and upper-case(normalize-space($gtd/@old)) != 'HOLD')
						)"/>
					<xsl:variable name="firstGate" select="$lstGate[1]"/>
					<xsl:variable name="firstGateNode" select="if ($adidMode = 'A') then $firstGate/pag_rgt_gate else $firstGate/pdg_rgt_gate"/>
					<xsl:variable name="hasGateChange" select="
						some $g in $gate satisfies (
							let $gtd := if ($adidMode = 'A') then $g/pag_rgt_gate else $g/pdg_rgt_gate
							return $g/@action 
								or $gtd/@action 
								or $gtd/@old 
								or $g/*/@action
						)"/>
					<!-- 2. คำนวณ gateAction ในภาพรวม -->
					<xsl:variable name="gtdAction">
						<xsl:choose>
							<!-- กรณีไม่เหลือ Gate จริงเลย (กลายเป็น HOLD ทั้งหมด) -> เป็น delete -->
							<xsl:when test="count($lstGate) = 0 and $deletedGate">
								<xsl:text>delete</xsl:text>
							</xsl:when>
							<xsl:when test="count($lstGate) = 0 or not($hasGateChange)">
								<xsl:text>none</xsl:text>
							</xsl:when>
							<!-- เปลี่ยนจาก HOLD มาเป็น Gate ใหม่ -->
							<xsl:when test="count($lstGate) = 1 and upper-case(normalize-space($firstGateNode/@old)) = 'HOLD'">
            					<xsl:text>insert</xsl:text>
							</xsl:when>
							<!-- นอกนั้นถ้ายังมี Gate เหลืออยู่ -> เป็น update -->
							<xsl:otherwise>
								<xsl:text>update</xsl:text>
							</xsl:otherwise>
						</xsl:choose>
					</xsl:variable>

					<!-- ตั้งค่า gateAction เมื่อมีการ Insert, Update, Delete Gate -->
					<xsl:if test="$gtdAction != 'none'">
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'gateAction'"/>
							<xsl:with-param name="node">
								<field action="{$gtdAction}">
									<xsl:value-of select="$gtdAction"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
					</xsl:if>

					<xsl:for-each select="$lstGate">
						<xsl:variable name="gtdNode" select="if ($adidMode = 'A') then pag_rgt_gate else pdg_rgt_gate"/>
						<xsl:variable name="pos" select="position()"/>
						<!-- กำหนด Prefix สำหรับ Tag Name ตาม Mode (A = gta/ga, อื่นๆ = gtd/gd) -->
						<xsl:variable name="prefix1" select="if ($adidMode = 'A') then 'gta' else 'gtd'"/>
						<xsl:variable name="prefix2" select="if ($adidMode = 'A') then 'ga' else 'gd'"/>

						<!-- 1. Gate Name -->
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="concat($prefix1, $pos)"/>
							<xsl:with-param name="forceEmit" select="true()"/>
							<xsl:with-param name="node">
								<field action="{$gtdAction}">
									<xsl:value-of select="$gtdNode"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<!-- 2. Begin Plan (b) -->
						<xsl:variable name="beginPlanNode" select="if ($adidMode = 'A') then pag_beginplan else pdg_begingateplan"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="concat($prefix2, $pos, 'b')"/>
							<xsl:with-param name="forceEmit" select="true()"/>
							<xsl:with-param name="node">
								<field action="{$beginPlanNode/@action}">
									<xsl:value-of select="custom:convertDate($beginPlanNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<!-- 3. End Plan (e) -->
						<xsl:variable name="endPlanNode" select="if ($adidMode = 'A') then pag_endplan else pdg_endplan"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="concat($prefix2, $pos, 'e')"/>
							<xsl:with-param name="forceEmit" select="true()"/>
							<xsl:with-param name="node">
								<field action="{$endPlanNode/@action}">
									<xsl:value-of select="custom:convertDate($endPlanNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<!-- 4. Begin Actual (x) -->
						<xsl:variable name="beginActualNode" select="if ($adidMode = 'A') then pag_beginactual else pdg_begingateactual"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="concat($prefix2, $pos, 'x')"/>
							<xsl:with-param name="forceEmit" select="true()"/>
							<xsl:with-param name="node">
								<field action="{$beginActualNode/@action}">
									<xsl:value-of select="custom:convertDate($beginActualNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<!-- 5. End Actual (y) -->
						<xsl:variable name="endActualNode" select="if ($adidMode = 'A') then pag_endactual else pdg_endactual"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="concat($prefix2, $pos, 'y')"/>
							<xsl:with-param name="forceEmit" select="true()"/>
							<xsl:with-param name="node">
								<field action="{$endActualNode/@action}">
									<xsl:value-of select="custom:convertDate($endActualNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
					</xsl:for-each>

					<!-- ============================================================
						ARRIVAL-ONLY FIELDS (action-filtered)
						============================================================ -->
					<xsl:if test="$adidMode = 'A'">
						<xsl:variable name="org3Node" select="//pl_arrival/pa_rap_refpreviousairport/ref_airport/rap_iata3lc"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'org3'"/>
							<xsl:with-param name="node">
								<field action="{$org3Node/@action}">
									<xsl:value-of select="$org3Node"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="org4Node" select="//pl_arrival/pa_rap_refpreviousairport/ref_airport/rap_icao4lc"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'org4'"/>
							<xsl:with-param name="node">
								<field action="{$org4Node/@action}">
									<xsl:value-of select="$org4Node"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="divrNode" select="//pl_arrival/pa_diversiontime"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'divr'"/>
							<xsl:with-param name="node">
								<field action="{$divrNode/@action}">
									<xsl:value-of select="custom:convertDate($divrNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="racoNode" select="//pl_arrival/pl_atcarrival_list/pl_atcarrival/paa_firstcontact"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'raco'"/>
							<xsl:with-param name="node">
								<field action="{$racoNode/@action}">
									<xsl:value-of select="$racoNode"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
						
						<xsl:variable name="ifraNode" select="//pl_arrival/pl_atcarrival_list/pl_atcarrival/paa_flightrule"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'ifra'"/>
							<xsl:with-param name="node">
								<field action="{$ifraNode/@action}">
									<xsl:value-of select="$ifraNode"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="fplaNode" select="//pl_arrival/pa_fplactivationtime"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'fpla'"/>
							<xsl:with-param name="node">
								<field action="{$fplaNode/@action}">
									<xsl:value-of select="custom:convertDate($fplaNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="bbaaNode" select="//pl_arrival/pa_onbridge"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'bbaa'"/>
							<xsl:with-param name="node">
								<field action="{$bbaaNode/@action}">
									<xsl:value-of select="$bbaaNode"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="tmoaNode" select="//pl_arrival/pa_fnlt"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'tmoa'"/>
							<xsl:with-param name="node">
								<field action="{$tmoaNode/@action}">
									<xsl:value-of select="custom:convertDate($tmoaNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="rwyaNode" select="//pl_arrival/pa_rrwy_runway"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'rwya'"/>
							<xsl:with-param name="node">
								<field action="{$rwyaNode/@action}">
									<xsl:value-of select="$rwyaNode"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<!-- Arrival time fields -->
						<xsl:variable name="aibtNode" select="//pl_arrival/pa_aibt"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'aibt'"/>
							<xsl:with-param name="node">
								<field action="{$aibtNode/@action}">
									<xsl:value-of select="custom:convertDate($aibtNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'onbl'"/>
							<xsl:with-param name="node">
								<field action="{$aibtNode/@action}">
									<xsl:value-of select="custom:convertDate($aibtNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
						
						<xsl:variable name="aldtNode" select="//pl_arrival/pa_aldt"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'aldt'"/>
							<xsl:with-param name="node">
								<field action="{$aldtNode/@action}">
									<xsl:value-of select="custom:convertDate($aldtNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'land'"/>
							<xsl:with-param name="node">
								<field action="{$aldtNode/@action}">
									<xsl:value-of select="custom:convertDate($aldtNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
						
						<xsl:variable name="eibtOriginalNode" select="//pl_arrival/pa_eibt"/>
						<xsl:variable name="hasAldt" select="normalize-space($aldtNode) != ''"/>
						<xsl:variable name="hasEibt" select="normalize-space($eibtOriginalNode) != ''"/>
						<xsl:variable name="eibtNode" select="if ($hasAldt) then $aldtNode else $eibtOriginalNode"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'eibt'"/>
							<xsl:with-param name="node">
								<field action="{$eibtNode/@action}">
									<xsl:value-of select="custom:convertDate($eibtNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'etai'"/>
							<xsl:with-param name="node">
								<field action="{$eibtNode/@action}">
									<xsl:value-of select="custom:convertDate($eibtNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'etoa'"/>
							<xsl:with-param name="node">
								<field action="{$eibtNode/@action}">
									<xsl:value-of select="custom:convertDate($eibtNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<!-- <xsl:if test="not($hasAldt and $hasEibt)"> -->
							<xsl:variable name="arrRem1Node" select="//pl_arrival/pa_rrmk_remark"/>
							<xsl:call-template name="getValue">
								<xsl:with-param name="tagName" select="'rem1'"/>
								<xsl:with-param name="forceEmit" select="true()"/>
								<xsl:with-param name="node">
									<field action="{$arrRem1Node/@action}">
										<xsl:value-of select="$arrRem1Node"/>
									</field>
								</xsl:with-param>
							</xsl:call-template>
						<!-- </xsl:if> -->

						<xsl:variable name="eldtNode" select="//pl_arrival/pa_eldt"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'eldt'"/>
							<xsl:with-param name="node">
								<field action="{$eldtNode/@action}">
									<xsl:value-of select="custom:convertDate($eldtNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="exitNode" select="//pl_arrival/pa_exit"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'exit'"/>
							<xsl:with-param name="node">
								<field action="{$exitNode/@action}">
									<xsl:value-of select="$exitNode"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'axit'"/>
							<xsl:with-param name="node">
								<field action="{$exitNode/@action}">
									<xsl:value-of select="custom:convertDate($exitNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
						
						<xsl:variable name="starNode" select="//pl_arrival/pa_star"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'star'"/>
							<xsl:with-param name="node">
								<field action="{$starNode/@action}">
									<xsl:value-of select="$starNode"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
						
						<xsl:variable name="tldtNode" select="//pl_arrival/pa_tldt"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'tldt'"/>
							<xsl:with-param name="node">
								<field action="{$tldtNode/@action}">
									<xsl:value-of select="custom:convertDate($tldtNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="vipaNode" select="//pl_arrival/pa_vipind"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'vipa'"/>
							<xsl:with-param name="node">
								<field action="{$vipaNode/@action}">
									<xsl:value-of select="$vipaNode"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
						
						<xsl:variable name="trmaNode" select="//pl_arrival/pa_rtrm_terminal"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'trma'"/>
							<xsl:with-param name="node">
								<field action="{$trmaNode/@action}">
									<xsl:value-of select="$trmaNode"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="acorNode" select="//pl_arrival/pl_atcarrival_list/pl_atcarrival/paa_firstcontact"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'acor'"/>
							<xsl:with-param name="node">
								<field action="{$acorNode/@action}">
									<xsl:value-of select="custom:convertDate($acorNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
					</xsl:if>

					<!-- Belts -->
					<xsl:variable name="belt" select="//pl_baggagebelt_list/pl_baggagebelt"/>
					<xsl:variable name="lstBelt" select="
						$belt[
							let $blt := pbb_rbb_baggagebelt
							return upper-case(normalize-space($blt)) != 'HOLD'
								and not(@action = 'delete' or $blt/@action = 'delete')
						]"/>
					<xsl:variable name="deletedBelt" select="
						some $b in $belt satisfies (
							let $blt := $b/pbb_rbb_baggagebelt
							return $b/@action = 'delete' 
								or $blt/@action = 'delete'
								or (upper-case(normalize-space($blt)) = 'HOLD' 
									and $blt/@old 
									and upper-case(normalize-space($blt/@old)) != 'HOLD')
						)"/>
					<xsl:variable name="firstBelt" select="$lstBelt[1]"/>
					<xsl:variable name="firstBeltNode" select="$firstBelt/pbb_rbb_baggagebelt"/>
					<xsl:variable name="hasBeltChange" select="
						some $b in $belt satisfies (
							let $blt := $b/pbb_rbb_baggagebelt
							return $b/@action 
								or $blt/@action 
								or $blt/@old 
								or $b/*/@action
						)"/>
					<xsl:variable name="beltAction">
						<xsl:choose>
							<xsl:when test="count($lstBelt) = 0 and $deletedBelt">
								<xsl:text>delete</xsl:text>
							</xsl:when>
							<xsl:when test="count($lstBelt) = 0 or not($hasBeltChange)">
								<xsl:text>none</xsl:text>
							</xsl:when>
							<xsl:when test="count($lstBelt) = 1 and upper-case(normalize-space($firstBeltNode/@old)) = 'HOLD'">
								<xsl:text>insert</xsl:text>
							</xsl:when>
							<xsl:otherwise>
								<xsl:text>update</xsl:text>
							</xsl:otherwise>
						</xsl:choose>
					</xsl:variable>
					<xsl:if test="$beltAction != 'none'">
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'beltAction'"/>
							<xsl:with-param name="node">
								<field action="{$beltAction}">
									<xsl:value-of select="$beltAction"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
					</xsl:if>
					<xsl:for-each select="$lstBelt">
						<xsl:variable name="bltNode" select="pbb_rbb_baggagebelt"/>
						<xsl:variable name="pos" select="position()"/>
							
						<!-- 1. Belt Name -->
						<xsl:variable name="bltAction" select="*/@action"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="concat('blt', $pos)"/>
							<xsl:with-param name="forceEmit" select="true()"/>
							<xsl:with-param name="node">
								<field action="{$bltAction[1]}">
									<xsl:value-of select="$bltNode"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<!-- 2. Begin Plan (bs) -->
						<xsl:variable name="beginPlanBeltNode" select="pbb_beginplan"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="concat('b', $pos, 'bs')"/>
							<xsl:with-param name="forceEmit" select="true()"/>
							<xsl:with-param name="node">
								<field action="{$beginPlanBeltNode/@action}">
									<xsl:value-of select="custom:convertDate($beginPlanBeltNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<!-- 3. End Plan (be) -->
						<xsl:variable name="endPlanBeltNode" select="pbb_endplan"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="concat('b', $pos, 'es')"/>
							<xsl:with-param name="forceEmit" select="true()"/>
							<xsl:with-param name="node">
								<field action="{$endPlanBeltNode/@action}">
									<xsl:value-of select="custom:convertDate($endPlanBeltNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<!-- 4. Begin Actual (ba) -->
						<xsl:variable name="beginActualBeltNode" select="pbb_beginactual"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="concat('b', $pos, 'ba')"/>
							<xsl:with-param name="forceEmit" select="true()"/>
							<xsl:with-param name="node">
								<field action="{$beginActualBeltNode/@action}">
									<xsl:value-of select="custom:convertDate($beginActualBeltNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<!-- 5. End Actual (ea) -->
						<xsl:variable name="endActualBeltNode" select="pbb_endactual"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="concat('b', $pos, 'ea')"/>
							<xsl:with-param name="forceEmit" select="true()"/>
							<xsl:with-param name="node">
								<field action="{$endActualBeltNode/@action}">
									<xsl:value-of select="custom:convertDate($endActualBeltNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
					</xsl:for-each>

					<!-- ============================================================
						DEPARTURE-ONLY FIELDS (action-filtered)
						============================================================ -->
					<xsl:if test="$adidMode = 'D'">
						<xsl:variable name="des3Node" select="//pl_departure/pd_rap_refdestinationairport/ref_airport/rap_iata3lc"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'des3'"/>
							<xsl:with-param name="node">
								<field action="{$des3Node/@action}">
									<xsl:value-of select="$des3Node"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="des4Node" select="//pl_departure/pd_rap_refdestinationairport/ref_airport/rap_icao4lc"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'des4'"/>
							<xsl:with-param name="node">
								<field action="{$des4Node/@action}">
									<xsl:value-of select="$des4Node"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="ctotNode" select="//pl_departure/pd_ctot"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'ctot'"/>
							<xsl:with-param name="node">
								<field action="{$ctotNode/@action}">
									<xsl:value-of select="custom:convertDate($ctotNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'slot'"/>
							<xsl:with-param name="node">
								<field action="{$ctotNode/@action}">
									<xsl:value-of select="custom:convertDate($ctotNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
						
						<xsl:variable name="deldNode" select="//pl_departure/pd_delay"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'deld'"/>
							<xsl:with-param name="node">
								<field action="{$deldNode/@action}">
									<xsl:value-of select="$deldNode"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="fpldNode" select="//pl_departure/pd_fplactivationtime"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'fpld'"/>
							<xsl:with-param name="node">
								<field action="{$fpldNode/@action}">
									<xsl:value-of select="custom:convertDate($fpldNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="bbfaNode" select="//pl_departure/pd_offbridge"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'bbfa'"/>
							<xsl:with-param name="node">
								<field action="{$bbfaNode/@action}">
									<xsl:value-of select="custom:convertDate($bbfaNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="fcalNode" select="//pl_departure/pd_secondcall"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'fcal'"/>
							<xsl:with-param name="node">
								<field action="{$fcalNode/@action}">
									<xsl:value-of select="custom:convertDate($fcalNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="boacNode" select="//pl_departure/pd_gotogate"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'boac'"/>
							<xsl:with-param name="node">
								<field action="{$boacNode/@action}">
									<xsl:value-of select="custom:convertDate($boacNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="rwydNode" select="//pl_departure/pd_rrwy_runway"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'rwyd'"/>
							<xsl:with-param name="node">
								<field action="{$rwydNode/@action}">
									<xsl:value-of select="$rwydNode"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<!-- Departure time fields -->
						<xsl:variable name="aobtNode" select="//pl_departure/pd_aobt"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'aobt'"/>
							<xsl:with-param name="node">
								<field action="{$aobtNode/@action}">
									<xsl:value-of select="custom:convertDate($aobtNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'ofbl'"/>
							<xsl:with-param name="node">
								<field action="{$aobtNode/@action}">
									<xsl:value-of select="custom:convertDate($aobtNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
						
						<xsl:variable name="atotNode" select="//pl_departure/pd_atot"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'atot'"/>
							<xsl:with-param name="node">
								<field action="{$atotNode/@action}">
									<xsl:value-of select="custom:convertDate($atotNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'airb'"/>
							<xsl:with-param name="node">
								<field action="{$atotNode/@action}">
									<xsl:value-of select="custom:convertDate($atotNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
						
						<xsl:variable name="eobtNode" select="//pl_departure/pd_eobt"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'eobt'"/>
							<xsl:with-param name="node">
								<field action="{$eobtNode/@action}">
									<xsl:value-of select="custom:convertDate($eobtNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'etdi'"/>
							<xsl:with-param name="node">
								<field action="{$eobtNode/@action}">
									<xsl:value-of select="custom:convertDate($eobtNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'etod'"/>
							<xsl:with-param name="node">
								<field action="{$eobtNode/@action}">
									<xsl:value-of select="custom:convertDate($eobtNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="depRem1Node" select="//pl_departure/pd_rrmk_remark"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'rem1'"/>
							<xsl:with-param name="forceEmit" select="true()"/>
							<xsl:with-param name="node">
								<field action="{$depRem1Node/@action}">
									<xsl:value-of select="$depRem1Node"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="exotNode" select="//pl_departure/pd_exot"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'exot'"/>
							<xsl:with-param name="node">
								<field action="{$exotNode/@action}">
									<xsl:value-of select="$exotNode"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'axot'"/>
							<xsl:with-param name="node">
								<field action="{$exotNode/@action}">
									<xsl:value-of select="custom:convertDate($exotNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="aghtNode" select="//pl_departure/pd_aght"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'aght'"/>
							<xsl:with-param name="node">
								<field action="{$aghtNode/@action}">
									<xsl:value-of select="$aghtNode"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="tobcNode" select="//pl_departure/pd_tobtchanges"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'tobc'"/>
							<xsl:with-param name="node">
								<field action="{$tobcNode/@action}">
									<xsl:value-of select="$tobcNode"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="vipdNode" select="//pl_departure/pd_vipind"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'vipd'"/>
							<xsl:with-param name="node">
								<field action="{$vipdNode/@action}">
									<xsl:value-of select="$vipdNode"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="trmdNode" select="//pl_departure/pd_rtrm_terminal"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'trmd'"/>
							<xsl:with-param name="node">
								<field action="{$trmdNode/@action}">
									<xsl:value-of select="$trmdNode"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="ifrdNode" select="//pl_departure/pd_flightrule"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'ifrd'"/>
							<xsl:with-param name="node">
								<field action="{$ifrdNode/@action}">
									<xsl:value-of select="$ifrdNode"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="acgtNode" select="//pl_departure/pd_acgt"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'acgt'"/>
							<xsl:with-param name="node">
								<field action="{$acgtNode/@action}">
									<xsl:value-of select="custom:convertDate($acgtNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="acztNode" select="//pl_departure/pd_aczt"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'aczt'"/>
							<xsl:with-param name="node">
								<field action="{$acztNode/@action}">
									<xsl:value-of select="custom:convertDate($acztNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="aegtNode" select="//pl_departure/pd_aegt"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'aegt'"/>
							<xsl:with-param name="node">
								<field action="{$aegtNode/@action}">
									<xsl:value-of select="custom:convertDate($aegtNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="aeztNode" select="//pl_departure/pd_aezt"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'aezt'"/>
							<xsl:with-param name="node">
								<field action="{$aeztNode/@action}">
									<xsl:value-of select="custom:convertDate($aeztNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'ardt'"/>
							<xsl:with-param name="node">
								<field action="{$aeztNode/@action}">
									<xsl:value-of select="custom:convertDate($aeztNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="arztNode" select="//pl_departure/pd_arzt"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'arzt'"/>
							<xsl:with-param name="node">
								<field action="{$arztNode/@action}">
									<xsl:value-of select="custom:convertDate($arztNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>
						
						<xsl:variable name="asatNode" select="//pl_departure/pd_asat"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'asat'"/>
							<xsl:with-param name="node">
								<field action="{$asatNode/@action}">
									<xsl:value-of select="custom:convertDate($asatNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="asbtNode" select="//pl_departure/pd_asbt"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'asbt'"/>
							<xsl:with-param name="node">
								<field action="{$asbtNode/@action}">
									<xsl:value-of select="custom:convertDate($asbtNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="asrtNode" select="//pl_departure/pd_asrt"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'asrt'"/>
							<xsl:with-param name="node">
								<field action="{$asrtNode/@action}">
									<xsl:value-of select="custom:convertDate($asrtNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="ecztNode" select="//pl_departure/pd_eczt"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'eczt'"/>
							<xsl:with-param name="node">
								<field action="{$ecztNode/@action}">
									<xsl:value-of select="custom:convertDate($ecztNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="eeztNode" select="//pl_departure/pd_eezt"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'eezt'"/>
							<xsl:with-param name="node">
								<field action="{$eeztNode/@action}">
									<xsl:value-of select="custom:convertDate($eeztNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="erztNode" select="//pl_departure/pd_erzt"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'erzt'"/>
							<xsl:with-param name="node">
								<field action="{$erztNode/@action}">
									<xsl:value-of select="custom:convertDate($erztNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="rtotNode" select="//pl_departure/pd_rtot"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'rtot'"/>
							<xsl:with-param name="node">
								<field action="{$rtotNode/@action}">
									<xsl:value-of select="custom:convertDate($rtotNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="stotNode" select="//pl_departure/pd_stot"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'stot'"/>
							<xsl:with-param name="node">
								<field action="{$stotNode/@action}">
									<xsl:value-of select="custom:convertDate($stotNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="tobtNode" select="//pl_departure/pd_tobt"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'tobt'"/>
							<xsl:with-param name="node">
								<field action="{$tobtNode/@action}">
									<xsl:value-of select="custom:convertDate($tobtNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="isIDEP" select="$adidMode = 'D' and 
							$originator = 'IDEP'"/>
						<xsl:variable name="tsatNode" select="//pl_departure/pd_tsat"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'tsat'"/>
							<xsl:with-param name="forceEmit" select="$isIDEP"/>
							<xsl:with-param name="node">
								<field action="{$tsatNode/@action}">
									<xsl:value-of select="custom:convertDate($tsatNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<xsl:variable name="ttotNode" select="//pl_departure/pd_ttot"/>
						<xsl:call-template name="getValue">
							<xsl:with-param name="tagName" select="'ttot'"/>
							<xsl:with-param name="node">
								<field action="{$ttotNode/@action}">
									<xsl:value-of select="custom:convertDate($ttotNode)"/>
								</field>
							</xsl:with-param>
						</xsl:call-template>

						<counter>
							<xsl:value-of select="//pl_departure/pd_counters"/>
						</counter>
						<xsl:variable name="terminal" select="//pl_departure/pd_rtrm_terminal"/>
						<lstFidsCcatab>
							<xsl:for-each select="//pl_desk">
								<xsl:variable name="ckicCode" select="pdk_rcnt_counter"/>
								<xsl:if test="upper-case(normalize-space($ckicCode)) != 'HOLD'">
									<fidsCcatab>
										<action>
											<!-- 1. ดึง action จาก pl_desk ก่อน -->
											<xsl:variable name="pldeskAction" select="@action"/>
											<!-- 2. ดึง action จาก pd_counters กรณีที่ pl_desk ไม่มี -->
											<xsl:variable name="pdcountersAction" select="//pl_departure/pd_counters/@action"/>
											<xsl:value-of select="if (string($pldeskAction)) then $pldeskAction else $pdcountersAction"/>
										</action>
										<flnu><xsl:value-of select="pdk_idseq"/></flnu>
										<ckic><xsl:value-of select="$ckicCode"/></ckic>
										<ckbs><xsl:value-of select="custom:convertDate(pdk_beginplan)"/></ckbs>
										<ckes><xsl:value-of select="custom:convertDate(pdk_endplan)"/></ckes>
										<ckba><xsl:value-of select="custom:convertDate(pdk_beginactual)"/></ckba>
										<ckea><xsl:value-of select="custom:convertDate(pdk_endactual)"/></ckea>
										<ctyp>
											<xsl:variable name="nodeCtyp" select="pdk_rcnt_refcounter/ref_counter/rcnt_type"/>
											<xsl:variable name="ctyp" select="if (string($nodeCtyp)) then $nodeCtyp else $nodeCtyp/@old"/>
											<xsl:value-of select="if ($ctyp = 'C') then 'C' else 'D'"/>
										</ctyp>
										<ckit><xsl:value-of select="$terminal"/></ckit>
										<disp><xsl:value-of select="pdk_checkinclassid"/></disp>
										<act3><xsl:value-of select="pdk_rcnt_refcounter/ref_counter/rcnt_ral_airline"/></act3>
										<lstu><xsl:value-of select="custom:convertDate(pdk_modtime)"/></lstu>
										<usec><xsl:value-of select="pdk_moduser"/></usec>
										<useu><xsl:value-of select="pdk_moduser"/></useu>
										<flno>
											<!-- กรณี Common จะเป็น Y -->
											<xsl:value-of select="if (pdk_rcnt_refcounter/ref_counter/rcnt_type = 'C') 
												then pdk_rcnt_refcounter/ref_counter/rcnt_ral_airline 
												else pdk_pd_flightnumber"/>
										</flno>
										<urno><xsl:value-of select="$urnoNode"/></urno>
									</fidsCcatab>
								</xsl:if>
							</xsl:for-each>
						</lstFidsCcatab>
					</xsl:if>

					<route>
						<xsl:value-of select="//pt_routingiata3lc"/>
					</route>
					<lstRouting>
						<xsl:variable name="routingNode" select="
							if ($adidMode = 'A') then //pl_arrival/pl_routing_list/pl_routing/prt_rap_refairport/ref_airport 
							else //pl_departure/pl_routing_list/pl_routing/prt_rap_refairport/ref_airport"/>
						<xsl:for-each select="$routingNode">
							<routing>
								<iata><xsl:value-of select="rap_iata3lc"/></iata>
								<icao><xsl:value-of select="rap_icao4lc"/></icao>
								<action><xsl:value-of select="rap_iata3lc/@action"/></action>
							</routing>
						</xsl:for-each>
					</lstRouting>
				</FidsAfttab>
			</xsl:when>
			<!-- กรณีเป็น Common Counter Mode (เข้า root มาเจอ pl_desk) -->
			<xsl:otherwise>
				<xsl:apply-templates select="//pl_desk"/>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	<xsl:template name="transformRempValue">
		<xsl:param name="val"/>
		<xsl:choose>
			<!-- ต้องใส่ $val = 'BRD' -->
			<xsl:when test="$val = 'BRD'">BOA</xsl:when>
			<xsl:when test="$val = '2ND'">FNC</xsl:when>
			<xsl:otherwise><xsl:value-of select="$val"/></xsl:otherwise>
		</xsl:choose>
	</xsl:template>

	<xsl:template match="//pl_desk">
        <FidsAfttab>
            <lstFidsCcatab>
				<fidsCcatab>
					<flnu><xsl:value-of select="pdk_idseq"/></flnu>
					<ckic><xsl:value-of select="pdk_rcnt_counter"/></ckic>
					<ckbs><xsl:value-of select="custom:convertDate(pdk_beginplan)"/></ckbs>
					<ckes><xsl:value-of select="custom:convertDate(pdk_endplan)"/></ckes>
					<ckba><xsl:value-of select="custom:convertDate(pdk_beginactual)"/></ckba>
					<ckea><xsl:value-of select="custom:convertDate(pdk_endactual)"/></ckea>
					<ctyp><xsl:value-of select="pdk_rcnt_refmastercci/ref_counter/rcnt_type"/></ctyp>
					<ckit><xsl:value-of select="pdk_rcnt_refcounter//rcnt_rco_concourse"/></ckit>
					<disp><xsl:value-of select="pdk_checkinclassid"/></disp>
					<act3><xsl:value-of select="pdk_rcnt_refmastercci//ral_3lc"/></act3>
					<lstu><xsl:value-of select="custom:convertDate(pdk_modtime)"/></lstu>
					<usec><xsl:value-of select="pdk_moduser"/></usec>
					<useu><xsl:value-of select="pdk_moduser"/></useu>
					<!-- ดัก flno ให้ไม่เป็น NULL พร้อมเติม Space 9 ช่อง -->
					<flno>
						<xsl:variable name="rawFlno" select="pdk_rcnt_refmastercci//ral_2lc"/>
						<xsl:value-of select="substring(concat($rawFlno, '         '), 1, 9)"/>
					</flno>
				</fidsCcatab>
			</lstFidsCcatab>
        </FidsAfttab>
    </xsl:template>
</xsl:stylesheet>
