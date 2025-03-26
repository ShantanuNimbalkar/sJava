
package com.godigit.abs.dispatcher.base.filter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.godigit.abs.dispatcher.base.datamodel.impl.AbstractOutputImpl;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.godigit.abs.dispatcher.base.adapter.DataCheckService;
import com.godigit.abs.dispatcher.base.context.DigitContext;
import com.godigit.abs.dispatcher.base.log.DispatcherLogging;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RequestResponseLoggingFilter implements Filter {

	private static final String PROPERTY_START_TIME = "start-time";
	private static final String REQUEST_TIME = "request_time";
	private static final String COMMON_DATA_SERVICE_BASE_PATH = "/LogService/rest/absQuoteInfoLog";
	private static final String VALIDATE_API_KEYWORDS = "CP Module Failed|IIB Down|Vahan Down|CP Kye Failed";
	private static final String APPROVED = "APPROVED";
	private static String urlPath="";
	private static final Map<Integer,String > serviceUrlMap = new ConcurrentHashMap();
	private static final int SUCCESS = 200;

	@Value("${logging.pi.info}")
	private boolean PI_INFO_SWITCH;

	@Value("${aws.nonabs.log.service.server.url}")
	private String COMMON_DATA_SERVICE_HOST_URL;

	@Autowired
	private RestTemplate restTemplate;

	@Qualifier("customObjectMapper")
	@Autowired
	ObjectMapper objectMapper;
	
	@Autowired
	private DataCheckService dataCheckService;

	@Override
	public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
			throws IOException, ServletException {

		HttpServletRequest request = (HttpServletRequest) req;
		urlPath = request.getRequestURL().toString();
		HttpServletResponse response = (HttpServletResponse) res;
		ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
		ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
		LocalDateTime requestTime = LocalDateTime.now();
		request.setAttribute(REQUEST_TIME, requestTime);
		request.setAttribute(PROPERTY_START_TIME, requestTime.toInstant(ZoneOffset.ofTotalSeconds(0)).toEpochMilli());
		if (null == request.getHeaders("sourceIP"))
			request.setAttribute("sourceIP", request.getRemoteAddr());

		if (null == request.getHeaders("privateIP"))
			request.setAttribute("privateIP", request.getRemoteAddr());
		DigitContext.putBasicAuth(request.getHeader(HttpHeaders.AUTHORIZATION));
		chain.doFilter(requestWrapper, responseWrapper);
		
		String requestBody=getReqOrRespBody(requestWrapper.getContentAsByteArray(),request.getCharacterEncoding());
		String responseBody=getReqOrRespBody(responseWrapper.getContentAsByteArray(),response.getCharacterEncoding());

		Map<String, String> queryParamsMap = Collections.list(request.getParameterNames()).stream().collect(
				Collectors.toMap(parameterName -> parameterName, parameterName -> Arrays.toString(request.getParameterValues(parameterName))));

		Map<String, Object> headersMap = Collections.list(request.getHeaderNames()).stream()
				.collect(Collectors.toMap(Function.identity(), h -> Collections.list(request.getHeaders(h))));

		int code = response.getStatus();
		String method = request.getMethod();

		String path = request.getRequestURL().toString();
		
		if (path.contains("integration/v2/quickquote") || path.contains("integration/v2/quote")||path.contains("integration/phonepe/v1/quotation")||path.contains("integration/phonepe/v1/proposal")) {
			List<String> validationMessagesForUW = DigitContext.getValidationMessagesForUW();
			if ((!validationMessagesForUW.isEmpty()) && validationMessagesForUW.size() >= 2) {
				// responseBody=responseBody.concat("\n").concat(validationMessagesForUW.toString());

				JSONObject json = new JSONObject(responseBody);
				json.put("ReferralMessage",
						validationMessagesForUW.get(0) != null ? validationMessagesForUW.get(0) : "");
				json.put("DecisionID", validationMessagesForUW.get(1) != null ? validationMessagesForUW.get(1) : "");
				responseBody = json.toString();

			}

		}
		
		if (path.contains("/integration/v2/validate/quote")
			&& APPROVED.equalsIgnoreCase(getValueFormKey(responseBody, "validationStatus"))) {
		    int count = 1;
		    JSONObject json = new JSONObject(responseBody);
		    for (String reason : DigitContext.getValidationMessagesForUW()) {
			if (VALIDATE_API_KEYWORDS.contains(reason)) {
			    json.put("APPROVED_" + count, reason);
			    count++;
			}
		    }
		    responseBody = json.toString();
		}
		
		if(!CollectionUtils.isEmpty(DigitContext.getValidationMessagesForPremium()) && DigitContext.getValidationMessagesForPremium().contains("Bifurcation")&& code > SUCCESS) {
			JSONObject json = new JSONObject(responseBody);
			int index = DigitContext.getValidationMessagesForPremium().indexOf("Bifurcation");
			json.put("Bifurcation", "Coverage premium : " + DigitContext.getValidationMessagesForPremium().get(index + 1) + 
					", Collected Premium : " + DigitContext.getValidationMessagesForPremium().get(index + 2) + 
					", Required Minimum Premium : " + DigitContext.getValidationMessagesForPremium().get(index + 3) + 
					", NCB discountPercent : " + DigitContext.getValidationMessagesForPremium().get(index + 4) +
					", IDV : " + DigitContext.getValidationMessagesForPremium().get(index + 5) +
					", userSpecialDiscountPercent : " + DigitContext.getValidationMessagesForPremium().get(index + 6) + 
					", gst : " + DigitContext.getValidationMessagesForPremium().get(index + 7));
			responseBody = json.toString();
		}
		if(!CollectionUtils.isEmpty(DigitContext.getValidationMessagesForPremium()) && DigitContext.getValidationMessagesForPremium().contains("IIB Response")&& code > SUCCESS) {
			JSONObject json = new JSONObject(responseBody);
			int index = DigitContext.getValidationMessagesForPremium().indexOf("IIB Response");
			json.put("IIB Response", DigitContext.getValidationMessagesForPremium().get(index + 1));
			responseBody = json.toString();
		}
		if(!CollectionUtils.isEmpty(DigitContext.getValidationMessagesForPremium()) && DigitContext.getValidationMessagesForPremium().contains("Motor Discount")&& code > SUCCESS) {
			JSONObject json = new JSONObject(responseBody);
			int index = DigitContext.getValidationMessagesForPremium().indexOf("Motor Discount");
			json.put("Motor Discount", DigitContext.getValidationMessagesForPremium().get(index + 1));
			responseBody = json.toString();
		}
		

		LocalDateTime responseDateTime = LocalDateTime.now();
		long duration = calcDuration(request, responseDateTime.toInstant(ZoneOffset.ofTotalSeconds(0)).toEpochMilli());

		String publicIP = "";
		String privateIP = "";
		if (request.getAttribute("sourceIP") != null) {
			publicIP = request.getAttribute("sourceIP").toString();
		} else {
			publicIP = headersMap.get("host").toString();
		}

		if (request.getAttribute("privateIP") != null) {
			privateIP = request.getAttribute("privateIP").toString();
		}
		else {
			privateIP = headersMap.get("host").toString();
		}
		DispatcherLogging dispatcherLogging = new DispatcherLogging();

		dispatcherLogging.setPublicIP(publicIP);
		dispatcherLogging.setCallerIP(privateIP);

		String listOfSourceChannel = null != queryParamsMap ? queryParamsMap.get("sourceChannel") : null;
		
		boolean storePathIfSensitiveDataFound = dataCheckService.storePathIfSensitiveDataFound(responseBody);

		if (!ObjectUtils.isEmpty(listOfSourceChannel)) {
			dispatcherLogging.setSourceName(listOfSourceChannel);
		}

		dispatcherLogging.setMethod(method);
		dispatcherLogging.setServicePath(path);
		dispatcherLogging.setWebuser(DigitContext.getWebuser());
		dispatcherLogging.setEnquiryId(DigitContext.getEnquiryID());
		dispatcherLogging.setRequestTime((LocalDateTime) request.getAttribute(REQUEST_TIME)); 
		dispatcherLogging.getHeaders().putAll(headersMap);		
		if(queryParamsMap.containsKey("password"))
			queryParamsMap.remove("password");
		if (null != queryParamsMap)
			dispatcherLogging.getQueryParams().putAll(queryParamsMap);
		dispatcherLogging.setRequestBody(requestBody.trim());
		dispatcherLogging.setResponseBody(responseBody.trim());
		dispatcherLogging.setResponseTime(responseDateTime);
		dispatcherLogging.setHeaders(headersMap);
		dispatcherLogging.setHttpStatus(code);
		dispatcherLogging.setExecutionTime(duration);
		dispatcherLogging.setImdCode(DigitContext.getImdCode());
		dispatcherLogging.setPolicyNumber(getValueFormKey(responseBody, "policyNumber"));
		
		if (storePathIfSensitiveDataFound && PI_INFO_SWITCH && !serviceUrlMap.containsKey(request.getRequestURL().toString().trim().hashCode())) {
            dispatcherLogging.getQueryParams().put("PII_INFO",null!=queryParamsMap ? queryParamsMap.values().toString() : StringUtils.EMPTY);
            serviceUrlMap.put(request.getRequestURL().toString().trim().hashCode(), "Updated");
           // dispatcherLogging.setEnquiryId(DigitContext.getEnquiryID() == null ? "PII_Info" : DigitContext.getEnquiryID() + "PII_Info");
        }
        
        if ((path.contains("external/services/digit/search/policysummaries") || path.contains("external/services/digit/report/policyPDF")) && responseBody != null) {
			
        	String piiEncrypted = dataCheckService.encrypt(responseBody);
			
			responseWrapper.resetBuffer();
		    responseWrapper.getOutputStream().write(piiEncrypted.getBytes());
		}


		storeInLoggingTable(dispatcherLogging);

		if( HttpStatus.INTERNAL_SERVER_ERROR.value() ==code){
			ratingErrorMasking(responseWrapper,response);
		}else {
			responseWrapper.copyBodyToResponse();
		}

		DigitContext.clear();

	}

	private void ratingErrorMasking(ContentCachingResponseWrapper responseWrapper,HttpServletResponse response) throws IOException {
		String responseBody=getReqOrRespBody(responseWrapper.getContentAsByteArray(),response.getCharacterEncoding());

		AbstractOutputImpl abstractOutput = objectMapper.readValue(responseBody, AbstractOutputImpl.class);

		if(null!=abstractOutput.getError() &&  HttpStatus.INTERNAL_SERVER_ERROR.value() == abstractOutput.getError().getErrorCode()) {
			for(int i =0; i < abstractOutput.getError().getValidationMessages().size();i++) {
				if(validationMessageContains(abstractOutput.getError().getValidationMessages().get(i),"formula=")) {
					abstractOutput.getError().getValidationMessages().set(i,"Rating Error");
				}
			}
			resetAndCopyResponse(responseWrapper, new JSONObject(abstractOutput).toString());
		}
		else {
			resetAndCopyResponse(responseWrapper, responseBody);
		}
		
	}
	
	private void resetAndCopyResponse(ContentCachingResponseWrapper responseWrapper, String responseBody) throws IOException {
	    responseWrapper.resetBuffer();
	    responseWrapper.getOutputStream().write(responseBody.getBytes());
	    responseWrapper.copyBodyToResponse();
	}

	private boolean validationMessageContains(String validationMessage, String keyword) {
		return  validationMessage.contains(keyword);
	}

	private String getValueFormKey(String jsonString, String key) {

		if (jsonString != null && !jsonString.equalsIgnoreCase("null")) {
			
			try {
				JsonElement jsonElement = JsonParser.parseString(jsonString);
				if (jsonElement instanceof JsonArray) {
					JsonArray jsonArray = JsonParser.parseString(jsonString).getAsJsonArray();
					for (JsonElement jsonArrayElement : jsonArray) {
						if (jsonArrayElement instanceof JsonObject) {
							JsonObject jsonObject = jsonArrayElement.getAsJsonObject();
							if (jsonObject.has(key) && !jsonObject.get(key).isJsonNull()) {
								if (jsonObject.get(key).getAsString() == null)
									return null;
								else
									return jsonObject.get(key).getAsString();
							}
						}
					}
				} else if (jsonElement instanceof JsonObject) {
					JsonObject jsonObject = JsonParser.parseString(jsonString).getAsJsonObject();
					if (jsonObject.has(key) && !jsonObject.get(key).isJsonNull()) {
						if (jsonObject.get(key).getAsString() == null)
							return null;
						else
							return jsonObject.get(key).getAsString();
					}
				}
			} catch (JsonSyntaxException e) {
				return StringUtils.EMPTY;
			}
		}
		return null;
	}


	private long calcDuration(HttpServletRequest requestContext, long responseTimeMillis) {

		Object property = requestContext.getAttribute(PROPERTY_START_TIME);

		if (!(property instanceof Long)) {
			return -1;
		}

		long startTime = (Long) property;

		return responseTimeMillis - startTime;
	}

	@Async
	private void storeInLoggingTable(DispatcherLogging dispatcherLogging) throws JsonProcessingException {
		String path = COMMON_DATA_SERVICE_HOST_URL + COMMON_DATA_SERVICE_BASE_PATH;
		String dispatcherLoggingStr = objectMapper.writeValueAsString(dispatcherLogging);
		try {
			HttpHeaders httpHeaders = new HttpHeaders();
			httpHeaders.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<?> httpEntity = new HttpEntity<>(dispatcherLoggingStr,
					httpHeaders);
			restTemplate.exchange(path, HttpMethod.POST, httpEntity, String.class);
		} catch (Exception e) {
			if (log.isInfoEnabled())
				log.info(String.format("EnquiryId: %s, ABS dispatcher logging failed, Data: %s, Error Message: %s",
						DigitContext.getEnquiryID(), dispatcherLoggingStr, e.getMessage()));

		}
	}

	public static String getUrlPath() {
		return urlPath;
	}

	private String getReqOrRespBody(byte[] contentAsByteArray, String characterEncoding) {
		// TODO Auto-generated method stub
		try {
			return new String(contentAsByteArray,0,contentAsByteArray.length,characterEncoding);
		} catch (UnsupportedEncodingException e) {
			if(log.isErrorEnabled()) {
				log.error("Exception while reading Body"+e.getMessage());
			}
		}
		return "".concat("\n");
	}
}
