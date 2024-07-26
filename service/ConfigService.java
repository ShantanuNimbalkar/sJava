/**
 * 
 */
package com.godigit.endorsement.utils;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.godigit.endorsement.client.SpringRestClient;
import com.godigit.endorsement.exception.ServiceException;
import com.godigit.endorsement.model.CamundaRequestModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import com.godigit.abs.model.Claim;
import com.godigit.abs.model.HealthClaim;
import com.godigit.endorsement.constants.ABSConstants;
import com.godigit.endorsement.constants.ClaimConstants;
import com.godigit.endorsement.constants.EndorsementConstants;
import com.godigit.endorsement.entity.AdmissibilityRequest;
import com.godigit.endorsement.entity.EndorsementRequest;
import com.godigit.endorsement.entity.ProductMapping;
import com.godigit.endorsement.exception.ActivityServiceException;
import com.godigit.endorsement.exception.ValidationViolationException;
import com.godigit.endorsement.model.Abs64VbStatus;
import com.godigit.endorsement.model.DigitDeskRequest;
import com.godigit.endorsement.service.EndorsementServiceImpl;
import com.google.common.base.Strings;

import javax.annotation.PostConstruct;

/**
 * @author R.Pavankumar
 * @project s_endorsementservices
 * @version '1.0'
 * @date 11-Dec-2019
 */

@Component
public class UtilityHelper {
	
//	@Autowired
//	private static PropertyConfiguration PropertyConfiguration

	@Value("${CAMUNDA_DECISION}")
	private String camundaDecision;

	@Autowired
	private SpringRestClient springRestClient;
	
	private static final Logger logger = LogManager.getLogger(UtilityHelper.class);

	public static Map<String, Map<String, Integer>> endtReasonWithPriority = new ConcurrentHashMap<>();
	
	/**
	 * @param claimNumber
	 * @param claimInfo
	 * @param derivedFrom
	 * @return void
	 * @throws ActivityServiceException 
	 * @date 10-Dec-2019
	 * @description -- Once claim created respective ticket will be created in digitdesk.
	 *  This ticket will be used by CC team for searching the ticket based on Claim number/Policy number.
	 *   They can track the voice log and so on.
	 */
	public static String createDigitDeskTicketForMotorClaimsUri(String mainUri, String claimNumber, Claim claimInfo,String userNameApi) throws ActivityServiceException {
		logger.info("createDigitDeskTicket for the claimnumber : " + claimNumber);
		StringBuilder uriBuild = new StringBuilder(mainUri);
		uriBuild.append("issuetype=sr")
		.append("&project_key=sr")
		.append("&format=json")
		.append("&project_id=36")
				.append("&workflow_transition=Issue created")
				.append("&subprocess_id=FNOL")
				.append("&subproduct_id=Claim")
				.append("&reasonforcalling_id=")
				.append(claimInfo.getFnolIntimationPersonRole())
				.append("&title=")
				.append(claimNumber)
				.append('/')
				.append(claimInfo.getCustomerDetails().getCustomerName())
				.append("&description=")
				.append(claimInfo.getAccidentDetails().getAccidentDescription())
			    .append("&claimnumber_value=")
				.append(claimNumber)
				.append("&policynumber_value=")
				.append(claimInfo.getPolicyNumber());

		if (!ObjectUtils.isEmpty(userNameApi)) {
			uriBuild.append("&logined_email=").append(userNameApi);
		}
				
		// Assigning Person Communication details
		if (!ObjectUtils.isEmpty(claimInfo.getPerson())
				&& !ObjectUtils.isEmpty(claimInfo.getPerson().getCommunication())) {
			uriBuild.append("&emailaddress_value=" + claimInfo.getPerson().getCommunication().getEmail() + "");
			uriBuild.append("&phonenumber_value=" + claimInfo.getPerson().getCommunication().getMobile() + "");
		}
		if (!Objects.isNull(claimInfo.getProductId())) {
			// Assigning the productCode
			if (ABSConstants.MOTOR_2W_PRODUCT_CODE.contains(claimInfo.getProductId())) {
				uriBuild.append("&producttype_id=2W");
			} else if (ABSConstants.MOTOR_4W_PRODUCT_CODE.contains(claimInfo.getProductId())) {
				uriBuild.append("&producttype_id=Pvt Car");
			} else if (ABSConstants.MOTOR_CV_PRODUCT_CODE.contains(claimInfo.getProductId())) {
				uriBuild.append("&producttype_id=CV");
			}
		}
		// Assigning cause of loss
		if (!Strings.isNullOrEmpty(claimInfo.getLossCausesName())
				&& EndorsementConstants.MAP_LOSS_CAUSE_NAME.containsKey(claimInfo.getLossCausesName())) {
			uriBuild.append(
					"&process_id=" + EndorsementConstants.MAP_LOSS_CAUSE_NAME.get(claimInfo.getLossCausesName()));
		}
		// Assigning Reporter of the claim
		if (!Strings.isNullOrEmpty(claimInfo.getFnolIntimationPersonRole())
				&& EndorsementConstants.MAP_REPORTER_OF_CLAIM.containsKey(claimInfo.getFnolIntimationPersonRole())) {
			uriBuild.append(
					"&callertype_id=" + EndorsementConstants.MAP_REPORTER_OF_CLAIM.get(claimInfo.getFnolIntimationPersonRole()));
		} else {
			uriBuild.append("&callertype_id=Others");
		}
		
		if (!ObjectUtils.isEmpty(claimInfo.getNonABSAdditionalInfo())) {
			if (!Strings.isNullOrEmpty(claimInfo.getNonABSAdditionalInfo().getTicketCreatedBy())) {
				uriBuild.append(
						"&ticketcreatedby_value=" + claimInfo.getNonABSAdditionalInfo().getTicketCreatedBy() + "");
			}
			if (!Strings.isNullOrEmpty(claimInfo.getNonABSAdditionalInfo().getClaimLocation())) {
				uriBuild.append("&claimlocation_value=" + claimInfo.getNonABSAdditionalInfo().getClaimLocation() + "");
			}
			if (!Strings.isNullOrEmpty(claimInfo.getNonABSAdditionalInfo().getSteeringEligible())) {
				uriBuild.append(
						"&eligibleforsteering_value=" + claimInfo.getNonABSAdditionalInfo().getSteeringEligible() + "");
			}
		}
		return uriBuild.toString();
	}
	
	public static String updateDigitDeskTicketForHealthClaims(String digitDeskTicketCreationURL, String ddTicket,
            HealthClaim claimInfo) {
		logger.info("updateDigitDeskTicket for the claimnumber : " + claimInfo.getClaimNumber());
        StringBuilder uriBuild = new StringBuilder(digitDeskTicketCreationURL);
        uriBuild.append("issue_id="+ddTicket);
        uriBuild.append("&project_key=healthclaims");
        uriBuild.append("&issuetype=healthclaims");
        uriBuild.append("&format=json");
        uriBuild.append("&project_id=34");
        uriBuild.append("&subprocess_id=FNOL");
        uriBuild.append("&fields[status]=FNOL_SSL Link Sent");
        if(claimInfo.getTpaDetails() != null && !Strings.isNullOrEmpty(claimInfo.getTpaDetails().getTpaName()))
        	uriBuild.append("&fields[tpaname]="+claimInfo.getTpaDetails().getTpaName());
        if(claimInfo.getPolicyHolderDetails() != null && !Strings.isNullOrEmpty(claimInfo.getPolicyHolderDetails().getPhName()))
        	uriBuild.append("&fields[policyholderphname]="+claimInfo.getPolicyHolderDetails().getPhName());
        uriBuild.append("&substatus_value=SSL Link Sent");
        uriBuild.append("&reason_id=Claim Registered");
        uriBuild.append("&title=" + claimInfo.getPolicyNumber() + "/" + claimInfo.getClaimNumber() + "/" + claimInfo.getPerson().getFirstName() + "Claim Intimation");
        if(claimInfo.getProductId() == ClaimConstants.HEALTH_GMC_PRODUCT_CODE && null != claimInfo.getTpaDetails() && Strings.isNullOrEmpty(claimInfo.getTpaDetails().getTpaName())) {
            uriBuild.append("&description=" + "Claim Registration Alert. Please note that the TPA is not updated for this Claim as the TPA isn't tagged in ABS. Kindly look into this.");
        }else {
            uriBuild.append("&description=" + "Claim Registration Alert.");
        }
        uriBuild.append("&fields[teamname_id]=DIGIT");
        uriBuild.append("&fields[claimnumber]=" + claimInfo.getClaimNumber() + "");
        uriBuild.append("&fields[policynumber]=" + claimInfo.getPolicyNumber() + "");
        // Assigning Person Communication details
        if (!ObjectUtils.isEmpty(claimInfo.getPerson())
                && !ObjectUtils.isEmpty(claimInfo.getPerson().getCommunication())) {
            uriBuild.append("&fields[emailaddress]=" + claimInfo.getPerson().getCommunication().getEmail() + "");
            uriBuild.append("&fields[phonenumber]=" + claimInfo.getPerson().getCommunication().getMobile() + "");
        }
        return uriBuild.toString();
    }
   
	
	public static String createDigitDeskTicketForHealthClaims(String digitDeskTicketCreationURL, String claimNo,
			HealthClaim claimInfo) {
		logger.info("createDigitDeskTicket for the claimnumber : " + claimNo);
		StringBuilder uriBuild = new StringBuilder(digitDeskTicketCreationURL);
		uriBuild.append("issuetype=healthclaim");
		uriBuild.append("&project_key=healthclaims");
		uriBuild.append("&format=json");
		uriBuild.append("&project_id=34");
		uriBuild.append("&status=FNOL_SSL Link Sent");
		uriBuild.append("&substatus_id=SSL Link Sent");
		if(claimInfo.getTpaDetails() != null && !Strings.isNullOrEmpty(claimInfo.getTpaDetails().getTpaName()))
			uriBuild.append("&tpaname_value=" + claimInfo.getTpaDetails().getTpaName());
		uriBuild.append("&reason_id=Claim Registered");
		if(claimInfo.getPolicyHolderDetails() != null && !Strings.isNullOrEmpty(claimInfo.getPolicyHolderDetails().getPhName()))
			uriBuild.append("&policyholderphname_value=" + claimInfo.getPolicyHolderDetails().getPhName());
		uriBuild.append("&title=" + claimInfo.getPolicyNumber() + "/" + claimNo + "/"
				+ claimInfo.getPerson().getFirstName() + "Claim Intimation");
		if (claimInfo.getProductId() == ClaimConstants.HEALTH_GMC_PRODUCT_CODE && null != claimInfo.getTpaDetails()
				&& Strings.isNullOrEmpty(claimInfo.getTpaDetails().getTpaName())) {
			uriBuild.append("&description="
					+ "Claim Registration Alert. Please note that the TPA is not updated for this Claim as the TPA isn't tagged in ABS. Kindly look into this.");
		} else {
			uriBuild.append("&description=" + "Claim Registration Alert.");
		}
		uriBuild.append("&claimnumber_value=" + claimNo + "");
		uriBuild.append("&policynumber_value=" + claimInfo.getPolicyNumber() + "");
		
		// Assigning Person Communication details
		if (!ObjectUtils.isEmpty(claimInfo.getPerson())
				&& !ObjectUtils.isEmpty(claimInfo.getPerson().getCommunication())) {
			uriBuild.append("&emailaddress_value=" + claimInfo.getPerson().getCommunication().getEmail() + "");
			uriBuild.append("&phonenumber_value=" + claimInfo.getPerson().getCommunication().getMobile() + "");
		}
		return uriBuild.toString();
	}
	
	public static boolean validateDate(String dateStr) {
		try {
			DateTimeFormatter dateFormatter = DateTimeFormatter.BASIC_ISO_DATE;
			LocalDate.parse(dateStr, dateFormatter);
		} catch (DateTimeParseException e) {
			return false;
		}
		return true;
	}
	
	public static void validateFromToDate(String frmDate, String toDate) {
		DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		LocalDateTime fromDateTime = null;
		LocalDateTime toDateTime = null;
		try {
			fromDateTime = LocalDateTime.parse(frmDate, dateFormatter);
			toDateTime = LocalDateTime.parse(frmDate, dateFormatter);
		} catch (DateTimeParseException e) {
			throw new ValidationViolationException("Please pass correct Date's", e);
		}
		if (toDateTime.compareTo(fromDateTime) < 0) {
			throw new ValidationViolationException("Start Date should be less than or equal to EndDate");
		}
	}
	
	public static String getTypeOfClaim(String typeCode) {
		switch (typeCode) {
		case "KH":
			return "Motor Third Party Liability insurance";
		case "KK":
			return "Motor Own Damage";
		case "IU":
			return "PA Owner Driver - IMT 15";
		case "HP":
			return "Legal Liability to Paid Driver - IMT 28";
		default:
			return typeCode;
		}
	}

	public static String getRepoterOfClaim(String reporterCode) {
		switch (reporterCode) {
		case "PA":
			return "Police authority";
		case "SV":
			return "Expert";
		case "OC":
			return "Occupants";
		case "NU":
			return "Non-Notified User";
		case "VN":
			return "Policy holder";
		case "SO":
			return "Other";
		case "WS":
			return "Repair shop";
		case "TP":
			return "Third party vehicle driver";
		case "UR":
			return "User";
		case "BP":
			return "Business partner";
		case "VD":
			return "PH Vehicle driver";
		case "HG":
			return "Injured party";
		default:
			return reporterCode;
		}
	}

	public static Boolean setTrueFalseFromString(String trueFalse) {
		if (trueFalse.trim().equalsIgnoreCase("true"))
			return true;
		else if (trueFalse.trim().equalsIgnoreCase("false"))
			return false;
		return null;
	}
	
	public static String createFRTicketForHealthClaims(String digitDeskTicketCreationURL, String claimNo,
			HealthClaim claimInfo, Abs64VbStatus abs64VbStatus) {
		logger.info("createDigitDeskTicket for the claimnumber : " + claimNo);
		StringBuilder uriBuild = new StringBuilder(digitDeskTicketCreationURL);
		uriBuild.append("issuetype=floatreplenishment");
		uriBuild.append("&project_key=floatreplenishment");
		uriBuild.append("&format=json");
		uriBuild.append("&project_id=48");
		uriBuild.append("&title=" + claimInfo.getPolicyNumber() + "/" + claimNo + "/"
				+ "64VB is not cleared");
		//uriBuild.append("&description=" + "Policy Number :" + claimInfo.getPolicyNumber());
		uriBuild.append("&claimnumber_value=" + claimNo + "");
		uriBuild.append("&policynumber_value=" + claimInfo.getPolicyNumber() + "");
		
		// Assigning Person Communication details
		if (!ObjectUtils.isEmpty(claimInfo.getPerson())
				&& !ObjectUtils.isEmpty(claimInfo.getPerson().getCommunication())) {
			uriBuild.append("&emailaddress_value=" + claimInfo.getPerson().getCommunication().getEmail() + "");
			uriBuild.append("&phonenumber_value=" + claimInfo.getPerson().getCommunication().getMobile() + "");
		}
		return uriBuild.toString();
	}
	
	public static String createSRDigitDeskTicketForEndorsement(String createDDTickerUrl, EndorsementRequest er) {
		Integer requestId = er.getRequestId();
		String policyNumber = er.getPolicyNumber();
		logger.debug(MessageFormat.format("createDigitDeskTicket for the endorsement, id: {0}, policynumber: {1}",
				requestId, policyNumber));
		StringBuilder uriBuild = new StringBuilder(createDDTickerUrl);
		uriBuild.append("issuetype=sr&project_key=sr&format=json&project_id=55&workflow_transition=Issue created&product_id=Motor&subproduct_id=Endorsement&title=")
		.append(requestId==null?policyNumber:requestId)
		.append("&description=").append(er.getRemarks())
		.append("&policynumber_value=").append(policyNumber)
		.append("&emailaddress_value=").append(er.getAgentEmail())
		.append("&phonenumber_value=").append(er.getAgentMobile())
		.append("&ticketcreatedby_value=").append(er.getCreatedBy());
		String reasonForCalling = er.getReasonForCallingId();
		if(!Strings.isNullOrEmpty(reasonForCalling)) {
			uriBuild.append("&reasonforcalling_id=").append(reasonForCalling);
		}
		String additionalRequest = er.getAdditionalRequestId();
		if(!Strings.isNullOrEmpty(additionalRequest)) {
			uriBuild.append("&additionalrequest_id=").append(additionalRequest);
		}
		Optional<ProductMapping> productMapping = EndorsementServiceImpl.productMappings.stream()
				.filter(pm -> er.getProductId().equalsIgnoreCase(pm.getProductCode())).findFirst();
		if(productMapping.isPresent()) {
			uriBuild.append("&producttype_id=").append(productMapping.get().getProductName());
		}
		return uriBuild.toString();
	}
	
	public static String updateDigitDeskTicketForEndorsement(String updateDDTickerUrl, EndorsementRequest er) {
		Integer requestId = er.getRequestId();
		String policyNumber = er.getPolicyNumber();
		logger.debug(MessageFormat.format("updateDigitDeskTicket for the endorsement, id: {0}, policynumber: {1}",
				requestId, policyNumber));
		StringBuilder uriBuild = new StringBuilder(updateDDTickerUrl);
		uriBuild.append("issuetype=endorsementretail&project_key=endorsementretail&format=json&project_id=55&workflow_transition=Issue updated&reason_id=Endorsement status updated&message=")
		.append(er.getRemarks())
		.append("&issue_id=").append(er.getDigitDeskTicketId())
		.append("&fields[endorsementportalstatus]=").append(er.getStatus());
		return uriBuild.toString();
	}
	
	public static String updateDigitDeskTicketForEndorsement(String updateDDTickerUrl, Integer requestId, Integer issueId, String remarks, String status) {
		logger.debug(MessageFormat.format("updateDigitDeskTicket for the endorsement, issue id: {0}",
				issueId));
		StringBuilder uriBuild = new StringBuilder(updateDDTickerUrl);
		uriBuild.append("project_key=sr&format=json&project_id=36&message=")
		.append(remarks)
		.append("&issue_id=").append(issueId)
		.append("&fields[endorsementportalstatus]=").append(status)
		.append("&fields[requestid]=").append(requestId);
		return uriBuild.toString();
	}
	
	public static String createDDTicketFor64VBMotorClaims(String digitDeskTicketCreationURL,
			AdmissibilityRequest claimInfo, Abs64VbStatus abs64VbStatus) {
		logger.info(MessageFormat.format("createDigitDeskTicket for the claimnumber : ", claimInfo.getClaimNumber()));
		String amount_value = abs64VbStatus.getVb64Output().getReceiptStatusData().get(0).getAmount() != null
				? abs64VbStatus.getVb64Output().getReceiptStatusData().get(0).getAmount()
				: "--";
		String imdcode_value = abs64VbStatus.getVb64Output().getReceiptStatusData().get(0).getAgentCode();
		String receiptnumber_value = abs64VbStatus.getVb64Output().getReceiptStatusData().get(0).getReceiptNumber();
		String chequetransactionnumber_value = "cheque"
				.equalsIgnoreCase(abs64VbStatus.getVb64Output().getReceiptStatusData().get(0).getInstrumentType())
						? abs64VbStatus.getVb64Output().getReceiptStatusData().get(0).getInstrumentNumber()
						: "--";

		StringBuilder uriBuild = new StringBuilder(digitDeskTicketCreationURL);
		uriBuild.append(
				"format=json&issuetype=floatreplenishment&project_key=floatreplenishment&project_id=48&title=64VB is not cleared for the claim number :")
				.append(claimInfo.getClaimNumber()).append("&description=Policy Number :")
				.append(claimInfo.getPolicyNumber()).append(" </br> Receipt No :").append(receiptnumber_value)
				.append(" </br> IMD Code :").append(imdcode_value).append(" </br> Cheque Number :")
				.append(chequetransactionnumber_value).append(" </br> Amount :").append(amount_value)
				.append("&workflow_transition=Issue created&process_id=Finance Action&subprocess_id=&reason_id=&vehicleregnumber_value=&claimnumber_value=")
				.append(claimInfo.getClaimNumber()).append("&policynumber_value=").append(claimInfo.getPolicyNumber())
				.append("&emailaddress_value=&phonenumber_value=&producttype_id=&callertype_id=&queryreason_id=64VB - Claims Tkt&responsiblefunction_id=Finance&amount_value=")
				.append(amount_value).append("&imdcode_value=").append(imdcode_value)
				.append("&tickettype_id=NSTP&receiptnumber_value=").append(receiptnumber_value)
				.append("&chequetransactionnumber_value=").append(chequetransactionnumber_value)
				.append("&claimnumber_value=").append(claimInfo.getClaimNumber())
				.append("&policynumber_value=").append(claimInfo.getPolicyNumber());
		return uriBuild.toString();
	}

	public static String updateDigitDeskTicketForEndorsement(DigitDeskRequest ddReq) {
		logger.debug(MessageFormat.format("updateDigitDeskTicket for the endorsement, issue id: {0}",
				ddReq.getIssueId()));
		StringBuilder uriBuild = new StringBuilder(ddReq.getUrl());
		String status = ddReq.getStatus();
		uriBuild.append("format=json&project_key=").append(ddReq.getProjectKey())
		.append("&project_id=").append(ddReq.getProjectId())
		.append("&message=").append(ddReq.getRemarks())
		.append("&issue_id=").append(ddReq.getIssueId())
		.append("&fields[endorsementportalstatus]=").append(status)
		.append("&fields[requestid]=").append(ddReq.getEndoRequestId());
//		if(EndorsementConstants.CLOSED_STATUS.equalsIgnoreCase(status)) {
//			uriBuild.append("&fields[status]=Closed");
//		}
		return uriBuild.toString();
	}
	
	public static String updateDigitDeskTicketForMotorClaimsUri(String mainUri, String claimNumber, Claim claimInfo,
			String userNameApi) throws ActivityServiceException {
		logger.info("updateDigitDeskTicket for the claimnumber : " + claimNumber);
		StringBuilder uriBuild = new StringBuilder(mainUri);
		uriBuild.append("issuetype=sr").append("&project_key=sr").append("&project_id=36").append("&claimnumber=")
				.append(claimNumber);

		if (!ObjectUtils.isEmpty(claimInfo.getUpdateTicket())) {
			uriBuild.append("&issue_no=").append(claimInfo.getUpdateTicket().getIssue_no());
			uriBuild.append("&reasonforcalling=").append(claimInfo.getUpdateTicket().getReasonforcalling());
			uriBuild.append("&preferredlanguage=").append(claimInfo.getUpdateTicket().getPreferredlanguage());
			uriBuild.append("&callertype=").append(claimInfo.getUpdateTicket().getCallertype());
			uriBuild.append("&callername=").append(claimInfo.getUpdateTicket().getCallername());
			uriBuild.append("&callingnumber=").append(claimInfo.getUpdateTicket().getCallingnumber());
		}
		return uriBuild.toString();
	}

	@PostConstruct
	public void fetchEndtReasonWithPriority() {
		String url = MessageFormat.format(camundaDecision, "endtReasons");
		Map<String, String> headerMap = new ConcurrentHashMap<>();
		headerMap.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
		headerMap.put(HttpHeaders.AUTHORIZATION, getBasicAuth("motorPI", "Digit@123"));

		try {
			ResponseEntity<String> response = springRestClient.call(url, headerMap, createVariablesPayload("input", "endtReason")
					, HttpMethod.POST, String.class);
			JsonArray jsonArray = JsonParser.parseString(Objects.requireNonNull(response.getBody())).getAsJsonArray();
			logger.info("Successfully fetched {} endorsement reasons with priority", jsonArray.size());

			jsonArray.forEach(element -> {
				JsonObject jsonObject = element.getAsJsonObject();
				String fieldName = jsonObject.getAsJsonObject("fieldName").get("value").getAsString();
				String endtReasons = jsonObject.getAsJsonObject("endtReasons").get("value").getAsString();
				Integer priority = jsonObject.getAsJsonObject("Priority").get("value").getAsInt();

				endtReasonWithPriority.computeIfAbsent(fieldName, k -> new HashMap<>()).put(endtReasons, priority);
			});
		} catch (Exception e) {
			endtReasonWithPriority.clear();
			logger.error("Error fetching endorsement reasons. URL: {}, Headers: {}, Message: {}, Error: {}", url, headerMap, e.getMessage(), e);
		}
	}

	private Map<String, Map<String, Map<String, Object>>> createVariablesPayload(String variable, Object value) {
		Map<String, Object> valueMap = new ConcurrentHashMap<>();
		valueMap.put("value", value);
		Map<String, Map<String, Object>> inputMap = Map.of(variable, valueMap);
		return Map.of("variables", inputMap);
	}

	public String getBasicAuth(String userId, String password) {
		try {
			if(!Strings.isNullOrEmpty(userId) && !Strings.isNullOrEmpty(password)) {
				return "Basic "+ Base64.getEncoder().encodeToString((userId+":"+password).getBytes("utf-8"));
			}
		}
		catch(Exception e) {
			if(logger.isErrorEnabled()) {
				logger.error("Exception in getBasicAuth method while getting basic auth error message : {}, error : {}",e.getMessage(), e);
			}
		}
		return null;
	}

	public static Map<String, Map<String, Integer>> endtReasonWithPriorityMap() {
		return endtReasonWithPriority;
	}
}
