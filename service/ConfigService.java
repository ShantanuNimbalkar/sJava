package com.digit.sms.service;

import com.digit.sms.entity.MobileNumberAccess;
import com.digit.sms.entity.SmsConfigProperty;
import com.digit.sms.entity.SmsTemplate;
import com.digit.sms.exception.AuthorizationException;
import com.digit.sms.exception.InvalidInputException;
import com.digit.sms.exception.SmsServiceException;
import com.digit.sms.model.request.ConfigPropertiesModel;
import com.digit.sms.model.request.MobileStatusRequest;
import com.digit.sms.model.request.SmsTemplateModel;
import com.digit.sms.model.request.SmsTemplateUpdate;
import com.digit.sms.repository.BlackListRepository;
import com.digit.sms.repository.SmsConfigPropertiesRepository;
import com.digit.sms.repository.SmsTemplateRepository;
import com.digit.sms.util.Constants;
import com.digit.sms.util.JwtTokenUtil;
import com.digit.sms.util.SmsUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static com.digit.sms.config.ApplicationProperties.activeProfile;
import static com.digit.sms.util.Constants.CompanyCode.GENERAL;
import static com.digit.sms.util.Constants.ERROR_MESSAGE.INVALID_USER;
import static com.digit.sms.util.Constants.PROD_PROFILE;

/**
 * @Author : Saravana.Kumar01
 * @CreatedAt : 09 September 2022
 */

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigService {

	// Dependency Injection
	private final SmsConfigPropertiesRepository smsConfigPropertiesRepo;

	private final SmsTemplateRepository smsTemplateRepo;

	private final CacheService cacheService;

	private final ModelMapper modelMapper;

	private final BlackListRepository blackListRepo;

	public String createOrUpdateSmsProperty(ConfigPropertiesModel configPropertiesModel) {
		final String generalValue = configPropertiesModel.getValueForGeneral();
		final String lifeValue = configPropertiesModel.getValueForLife();

		if (!StringUtils.hasText(generalValue) && !StringUtils.hasText(lifeValue)) {
			throw new InvalidInputException("Fields ['valueForGeneral', 'valueForLife'] both cannot be empty!");
		}
		SmsConfigProperty existingProperty = smsConfigPropertiesRepo.findByPropertyName(configPropertiesModel.getKey());
		if (ObjectUtils.isEmpty(existingProperty)) {
			SmsConfigProperty smsConfigProperty = new SmsConfigProperty();
			smsConfigProperty.setPropertyName(configPropertiesModel.getKey());
			smsConfigProperty.setPropertyValueGeneral(generalValue);
			smsConfigProperty.setPropertyValueLife(lifeValue);
			smsConfigProperty.setCreatedUser(configPropertiesModel.getModifiedUser());
			smsConfigPropertiesRepo.save(smsConfigProperty);
			return "Property: '" + configPropertiesModel.getKey() + "' configured successfully.";
		} else {
			if (StringUtils.hasText(generalValue)) {
				existingProperty.setPropertyValueGeneral(generalValue);
			}
			if (StringUtils.hasText(lifeValue)) {
				existingProperty.setPropertyValueLife(lifeValue);
			}
			smsConfigPropertiesRepo.save(existingProperty);
			return "The values for Property: '" + configPropertiesModel.getKey() + "' updated successfully.";
		}
	}

	public void doConfigApiAuthentication(String apiToken) {

		if (log.isInfoEnabled()) {
			log.info("Validating the API Token : " + apiToken);
		}
		String authorizedUser = JwtTokenUtil.getUserNameFromToken(apiToken);

		if (!SmsUtil.findByKey("SMS_CLIENT_ID", GENERAL.getCode()).equalsIgnoreCase(authorizedUser)) {
			throw new AuthorizationException(INVALID_USER.getMessage());
		}
	}

	public SmsTemplateModel createTemplate(SmsTemplateModel smsTemplateModel) {
		smsTemplateModel.setEffectiveFrom(Timestamp.valueOf(LocalDateTime.now()));
		SmsTemplate existingTemplate = smsTemplateRepo.findByDigitTemplateId(smsTemplateModel.getDigitTemplateId());
		if (existingTemplate != null) {
			throw new InvalidInputException(
					"Template -'" + smsTemplateModel.getDigitTemplateId() + "' already exists !");
		}
		try {
			SmsTemplate smsTemplate = modelMapper.map(smsTemplateModel, SmsTemplate.class);
			cacheService.updateTemplateCache(smsTemplate);
		} catch (Exception exception) {
			String errorMessage = "Unable to create template, Reason : " + exception.getMessage();
			if (log.isErrorEnabled()) {
				log.error(errorMessage, exception);
			}
			throw new SmsServiceException(errorMessage);
		}

		return smsTemplateModel;
	}

	public void updateTemplateData(List<SmsTemplateUpdate> templateUpdateList) {
		templateUpdateList.forEach(updateRequest -> {
			SmsTemplate existingTemplate = smsTemplateRepo.findByDigitTemplateId(updateRequest.getDigitTemplateId());
			if (existingTemplate == null) {
				throw new InvalidInputException(
						"Template -'" + updateRequest.getDigitTemplateId() + "' doesn't exists !");
			}
            try {
                if (StringUtils.hasText(updateRequest.getTemplateName())) {
                    existingTemplate.setTemplateName(updateRequest.getTemplateName());
                }
                if (StringUtils.hasText(updateRequest.getCompanyCode())) {
                    existingTemplate.setCompanyCode(updateRequest.getCompanyCode());
                }
                if (StringUtils.hasText(updateRequest.getDltRegistrationId())) {
                    existingTemplate.setDltRegistrationId(updateRequest.getDltRegistrationId());
                }
                if (StringUtils.hasText(updateRequest.getPreferredGateway())) {
                    existingTemplate.setPreferredGateway(updateRequest.getPreferredGateway());
                }
                if (StringUtils.hasText(updateRequest.getBusinessScenario())) {
                    existingTemplate.setBusinessScenario(updateRequest.getBusinessScenario());
                }
                if (StringUtils.hasText(updateRequest.getLineOfBusiness())) {
                    existingTemplate.setLineOfBusiness(updateRequest.getLineOfBusiness());
                }
                if (StringUtils.hasText(updateRequest.getAuthorizedUser())) {
                    existingTemplate.setAuthorizedUser(updateRequest.getAuthorizedUser());
                }
                if (updateRequest.getTemplatePriority() != null) {
                    existingTemplate.setTemplatePriority(updateRequest.getTemplatePriority());
                }
                if (updateRequest.getEffectiveFrom() != null) {
                    existingTemplate.setEffectiveFrom(updateRequest.getEffectiveFrom());
                }
                if (updateRequest.getEffectiveTo() != null) {
                    existingTemplate.setEffectiveTo(updateRequest.getEffectiveTo());
                }
                if (updateRequest.getContentExposable() != null) {
                    existingTemplate.setContentExposable(updateRequest.getContentExposable());
                }
				if(StringUtils.hasText(updateRequest.getTemplateContent())){
					existingTemplate.setTemplateContent(updateRequest.getTemplateContent());
				}
                smsTemplateRepo.save(existingTemplate);
            } catch (Exception exception) {
                String errorMessage = "Unable to update template details, Reason : " + exception.getMessage();
                if (log.isErrorEnabled()) {
                    log.error(errorMessage, exception);
                }
                throw new SmsServiceException(errorMessage, exception);
            }
        });
    }
	
    public void updateMobileNumberStatus(List<MobileStatusRequest> mobileStatusRequest) {
		mobileStatusRequest.forEach(mobileStatus -> {
			MobileNumberAccess mobileNumberAccess = modelMapper.map(mobileStatus, MobileNumberAccess.class);

			mobileNumberAccess.setEffectiveFrom(Timestamp.valueOf(LocalDateTime.now()));

			Timestamp timestamp = Timestamp
					.valueOf(mobileNumberAccess.getEffectiveFrom().toLocalDateTime().plusMonths((Integer.parseInt(SmsUtil.findByKey("EFFECTIVE_TO_DATE", Constants.CompanyCode.GENERAL.getCode())))));
			mobileNumberAccess.setEffectiveTo(timestamp);

			if (PROD_PROFILE.equalsIgnoreCase(activeProfile)) {
				cacheService.updateBlacklistCache(mobileNumberAccess);
			} else {
				MobileNumberAccess existingEntry = blackListRepo
						.findTopByMobileNumberAndCompanyCodeOrderByCreatedDateDesc(mobileNumberAccess.getMobileNumber(),
								mobileNumberAccess.getCompanyCode());
				if (existingEntry == null
						|| !existingEntry.getMobileNumberStatus().equals(mobileStatus.getMobileNumberStatus())
				||!ObjectUtils.isEmpty(mobileStatus.getUserType())) {
					if(ObjectUtils.isEmpty(existingEntry.getUserType())){
						String userType="NA";
						if(!ObjectUtils.isEmpty(mobileStatus.getUserType())){
							mobileNumberAccess.setUserType(mobileStatus.getUserType());
						}else{
							mobileNumberAccess.setUserType(userType);
						}
					}
					cacheService.updateBlacklistCache(mobileNumberAccess);
				} else if (existingEntry.getEffectiveTo().before(Timestamp.valueOf(LocalDateTime.now()))
						&& existingEntry.getMobileNumberStatus().equals(mobileStatus.getMobileNumberStatus())) {
					mobileNumberAccess.setEffectiveFrom(Timestamp.valueOf(LocalDateTime.now()));
					if(!ObjectUtils.isEmpty(mobileStatus.getUserType())){
						mobileNumberAccess.setUserType(mobileStatus.getUserType());
					}else{
						mobileNumberAccess.setUserType("NA");
					}
					Timestamp timestampnew = Timestamp
							.valueOf(mobileNumberAccess.getEffectiveFrom().toLocalDateTime().plusMonths(Integer.parseInt(SmsUtil.findByKey("EFFECTIVE_TO_DATE", Constants.CompanyCode.GENERAL.getCode()))));
					mobileNumberAccess.setEffectiveTo(timestampnew);
					cacheService.updateBlacklistCache(mobileNumberAccess);
				}
			}
		});
	}
}