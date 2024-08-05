package com.digisprint.serviceImpl;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.mail.MessagingException;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

import com.digisprint.EmailUtils.EmailService;
import com.digisprint.EmailUtils.EmailTemplates;
import com.digisprint.EmailUtils.LoadHtmlTemplates;
import com.digisprint.bean.AccessBean;
import com.digisprint.bean.EventsImagesAnnouncements;
import com.digisprint.bean.PaymentInfo;
import com.digisprint.bean.ProgressBarReport;
import com.digisprint.bean.RegistrationFrom;
import com.digisprint.exception.UserNotFoundException;
import com.digisprint.filter.JwtTokenUtil;
import com.digisprint.repository.AccessBeanRepository;
import com.digisprint.repository.EventsImagesAnnouncementsRepo;
import com.digisprint.repository.PaymentRepository;
import com.digisprint.repository.ProgressBarRepository;
import com.digisprint.repository.RegistrationFromRepository;
import com.digisprint.requestBean.ApprovalFrom;
import com.digisprint.service.RegistrationService;
import com.digisprint.utils.ApplicationConstants;
import com.digisprint.utils.EmailConstants;
import com.digisprint.utils.ErrorResponseConstants;
import com.digisprint.utils.GeneratingCredentials;
import com.digisprint.utils.RegistrationFormConstants;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RegistrationServiceImpl  implements RegistrationService{

	private RegistrationFromRepository registrationFromRepository;

	private ProgressBarRepository progressBarRepository;

	private EmailService email;

	private EmailTemplates emailTemplates;

	private GeneratingCredentials generatingCredentials;

	private PaymentRepository paymentRepository;

	private JwtTokenUtil jwtTokenUtil;

	private LoadHtmlTemplates htmlTemplates;

	private AccessBeanRepository accessBeanRepository;

	public RegistrationServiceImpl(RegistrationFromRepository registrationFromRepository,
			ProgressBarRepository progressBarRepository, EmailService email, EmailTemplates emailTemplates,
			GeneratingCredentials generatingCredentials, PaymentRepository paymentRepository, JwtTokenUtil jwtTokenUtil,
			LoadHtmlTemplates htmlTemplates, AccessBeanRepository accessBeanRepository) {
		super();
		this.registrationFromRepository = registrationFromRepository;
		this.progressBarRepository = progressBarRepository;
		this.email = email;
		this.emailTemplates = emailTemplates;
		this.generatingCredentials = generatingCredentials;
		this.paymentRepository = paymentRepository;
		this.jwtTokenUtil = jwtTokenUtil;
		this.htmlTemplates = htmlTemplates;
		this.accessBeanRepository = accessBeanRepository;
	}

	@Value("${org.uploadFiles}")
	private String UPLOAD_DIR;

	@Value("${spring.mail.username}")
	private String ADMIN_USERNAME;
	
	@Value("${org.transcation}")
	private String UPLOAD_TRANSCATION;

	@Override
	public RegistrationFrom registerUser(RegistrationFrom registrationForm) throws IOException, MessagingException {

		ProgressBarReport progressBarReport = new ProgressBarReport();
		progressBarReport.setUserId(registrationForm.getUserId());
		progressBarReport.setRegistrationOneFormCompleted(RegistrationFormConstants.TRUE);
		progressBarRepository.save(progressBarReport);
		//Sending mail to user.
		List<String> membersList = new ArrayList<>();
		String body = htmlTemplates.loadTemplate(emailTemplates.getWelcomeMailAfterFillingFirstRegistrationFrom());
		body = body.replaceAll(EmailConstants.REPLACE_PLACEHOLDER_NAME, registrationForm.getFullName());
		membersList.add(registrationForm.getEmailAddress());
		String[] newUser= new String[1];
		newUser[0] = registrationForm.getEmailAddress();
		email.MailSendingService(ADMIN_USERNAME, newUser, body, EmailConstants.REGISTRATOIN_1_EMAIL_SUBJECT);
		//Sending mail to committee members
		body = htmlTemplates.loadTemplate(emailTemplates.getNewUserNotifyToCommittee());
		List<AccessBean> committeList= accessBeanRepository.findByCommitee(true);
		System.out.println(committeList);
		List<String> emailOfCommittee= committeList.stream().map(object->object.getEmail()).collect(Collectors.toList());
		String[] emailsForCommiteeArray = new String[emailOfCommittee.size()];
		for(int i = 0 ; i<emailOfCommittee.size();i++) {
			emailsForCommiteeArray[i] = emailOfCommittee.get(i);
		}
		email.MailSendingService(ADMIN_USERNAME, emailsForCommiteeArray, body, EmailConstants.NEW_USER_REGISTERED_SUBJECT);

		registrationFromRepository.save(registrationForm);
		return registrationForm;
	}

	@Override
	public Page<RegistrationFrom> getAllRegisteredUsers(int page, int size) {
		Pageable pageable = PageRequest.of(page, size);
		return registrationFromRepository.findAll(pageable);
	}

	private String saveFileIfValid(MultipartFile file, String folderPath, RegistrationFrom user_from, String fileType,
			String formattedDate) throws IOException {
		if (!file.isEmpty()) {
			String originalFileName = file.getOriginalFilename();
			String extension = originalFileName.substring(originalFileName.lastIndexOf(ApplicationConstants.FULL_STOP));

			//			if (!extension.equalsIgnoreCase(ApplicationConstants.PDF)) {
			//				return originalFileName + ErrorResponseConstants.INVALID_FILE_TYPE;
			//			}

			String newFileName = user_from.getUserId() + ApplicationConstants.UNDERSCORE + fileType
					+ ApplicationConstants.UNDERSCORE
					+ formattedDate.replace(ApplicationConstants.COMMA, ApplicationConstants.HYPHEN) + extension;
			System.out.println(newFileName);
			String filePath = folderPath + ApplicationConstants.DOUBLE_SLASH + newFileName;
			Path path = Paths.get(filePath);
			Files.write(path, file.getBytes());

			switch (fileType) {
			case ApplicationConstants.AADHAR_CARD:
				user_from.setAadharCard(newFileName);
				break;
			case ApplicationConstants.VOTERID_CARD:
				user_from.setVoterIdCard(newFileName);
				break;
			case ApplicationConstants.PROFILE_PIC:
				user_from.setProfilePic(newFileName);
				break;	

			default:
				return ErrorResponseConstants.UNSUPPORTED_FILE_TYPE + fileType;
			}

			registrationFromRepository.save(user_from);
		}
		return null;
	}

	public JSONObject decodeToken(String jwtToken) {
		return JwtTokenUtil.decodeUserToken(jwtToken);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public ResponseEntity upload(String userId, MultipartFile aadharCard, MultipartFile voterIdCard, MultipartFile profilePic) throws Exception {
		RegistrationFrom userRegister = registrationFromRepository.findById(userId).orElseThrow(()-> new Exception(ErrorResponseConstants.USER_NOT_FOUND));

		if (userRegister != null) {
			String folderPath = UPLOAD_DIR ;
			File folder = new File(UPLOAD_DIR);
			String fileType = null;
			if (folder.exists()) {
				try {
					String strinFormateLocalDate = LocalDate.now().toString();
					if(aadharCard!=null) {
						fileType = ApplicationConstants.AADHAR_CARD;
						saveFileIfValid(aadharCard, folderPath, userRegister,fileType,strinFormateLocalDate);

					}
					if(voterIdCard!=null){
						fileType = ApplicationConstants.VOTERID_CARD;
						saveFileIfValid(voterIdCard, folderPath, userRegister,fileType,strinFormateLocalDate);

					}
					if(profilePic!=null) {
						fileType = ApplicationConstants.PROFILE_PIC;
						saveFileIfValid(profilePic, folderPath, userRegister,fileType,strinFormateLocalDate);

					}
					return new ResponseEntity(ApplicationConstants.FILE_UPLOADED_SUCCESSFULLY,HttpStatus.OK);
				} catch (IOException e) {
					log.error(ErrorResponseConstants.FAILED_TO_UPLOAD_FILE + e.getMessage());
					return new ResponseEntity(ErrorResponseConstants.FAILED_TO_UPLOAD_FILE,HttpStatus.INTERNAL_SERVER_ERROR);
				}
			} else {
				log.error(ErrorResponseConstants.FOLDER_NOT_FOUND + userRegister);
				return new ResponseEntity(ErrorResponseConstants.FOLDER_NOT_FOUND + userRegister,HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}

		return new ResponseEntity(ErrorResponseConstants.USER_NOT_FOUND + userRegister,HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@Override
	public void committeePresidentAccountantApproval(String token, String userId,ApprovalFrom from  ) throws Exception {

		if (token == null || token.isEmpty()) {
			throw new IllegalArgumentException("Token cannot be null or empty");
		}

		JSONObject jsonObject = decodeToken(token);
		if (!jsonObject.has("userId") || !jsonObject.has("access")) {
			throw new IllegalArgumentException("Token must contain userId and access fields");
		}

		String identityNumber = jsonObject.getString("userId");
		List accessList = jwtTokenUtil.getAccessList(token);
		String userType = null;

		if(accessList.contains(ApplicationConstants.PRESIDENT) && accessList.contains(ApplicationConstants.COMMITEE)
				&& accessList.contains(ApplicationConstants.ACCOUNTANT)) {
			userType = ApplicationConstants.PRESIDENT;
		}

		else if(accessList.contains(ApplicationConstants.COMMITEE)) {
			userType = ApplicationConstants.COMMITEE;	
		}
		else {
			userType = ApplicationConstants.ACCOUNTANT;
		}

		RegistrationFrom specificUserDetails = registrationFromRepository.findById(userId).get();
		if (specificUserDetails == null) {
			throw new IllegalArgumentException("No user found with the provided phone number");
		}

		Optional<ProgressBarReport> optionalProgressBarReport = progressBarRepository.findById(specificUserDetails.getUserId());
		if (!optionalProgressBarReport.isPresent()) {
			throw new IllegalArgumentException("No progress bar report found for the user");
		}
		String[] user= new String[1];
		user[0] = specificUserDetails.getEmailAddress();
		ProgressBarReport progressBarReport = optionalProgressBarReport.get();
		
		if (userType.equalsIgnoreCase(ApplicationConstants.COMMITEE)) {
			if (progressBarReport.isRegistrationOneFormCompleted() == RegistrationFormConstants.TRUE) {
				specificUserDetails.setCommitteeChoosenMembershipForApplicant(from.getMembership());
				specificUserDetails.setCommitteeRemarksForApplicant(from.getRemarks());
				String body = null;
				// Sending credentials to the Applicant as Committee approved.
				String username = specificUserDetails.getEmailAddress();
				String passcode = generatingCredentials.generatePasscode(specificUserDetails.getCategory(), specificUserDetails.getPhoneNumber());
				body = htmlTemplates.loadTemplate(emailTemplates.getLoginCredentialsEmail());
				body = body.replace("[]", username)
						.replace("[]", passcode);
				
				email.MailSendingService(ADMIN_USERNAME,user , body, EmailConstants.LOGIN_CREDENTIALS_SUBJECT);
				progressBarReport.setCommitteeApproval(true);
			} else if (from.getStatusOfApproval().equalsIgnoreCase(RegistrationFormConstants.REJECTED)) {
				progressBarReport.setCommitteeApproval(false);
				String body = null;
				body = htmlTemplates.loadTemplate(emailTemplates.getCommitteeRejectEmail());
				
				email.MailSendingService(ADMIN_USERNAME, user, body, EmailConstants.COMMITTEE_REJECTED_SUBJECT);
				progressBarReport.setCommitteeApproval(false);
				
			} else {
				progressBarReport.setCommitteeApproval(false);

				// waiting email
			}
		} else if (userType.equalsIgnoreCase(ApplicationConstants.PRESIDENT)) {
			if (specificUserDetails != null && progressBarReport != null 
					&& progressBarReport.isRegistrationOneFormCompleted() == RegistrationFormConstants.TRUE
				) {
				String body = null;
				progressBarReport.setPresidentApproval(RegistrationFormConstants.TRUE);
				specificUserDetails.setPresidentRemarksForApplicant(from.getRemarks());
				specificUserDetails.setPresidentChoosenMembershipForApplicant(from.getMembership());
				body = htmlTemplates.loadTemplate(emailTemplates.getPresidentApprovalEmail());
				email.MailSendingService(ADMIN_USERNAME, user, body, EmailConstants.PRESIDENT_APPROVED_SUBJECT);
				progressBarReport.setPresidentApproval(RegistrationFormConstants.TRUE);
			} else if (from.getStatusOfApproval().equalsIgnoreCase(RegistrationFormConstants.REJECTED)) {
				progressBarReport.setPresidentApproval(RegistrationFormConstants.FALSE);
				String body = null;
				// rejection mail from president
				body = htmlTemplates.loadTemplate(emailTemplates.getPresidentRejectionEmail());
				email.MailSendingService(ADMIN_USERNAME, user, body, EmailConstants.PRESIDENT_REJECTED_SUBJECT);
			} else {
				progressBarReport.setPresidentApproval(RegistrationFormConstants.FALSE);
				// waiting mail from president
			}

		} else if (userType.equalsIgnoreCase(ApplicationConstants.ACCOUNTANT)) {
			if (progressBarReport.isCommitteeApproval() && progressBarReport.isRegistrationOneFormCompleted() == RegistrationFormConstants.TRUE
					&& progressBarReport.isPayment() && progressBarReport.isPresidentApproval()
					&& progressBarReport.isRegistrationThreeFormCompleted() == RegistrationFormConstants.TRUE) {

				progressBarReport.setAccountantAcknowledgement(RegistrationFormConstants.TRUE);

				String memberIdentityNumber = generatingCredentials.generateMemberId();
				// send congratulations mail with generated memberID 
				String body = null;
				body = htmlTemplates.loadTemplate(emailTemplates.getMembershipApproved());
				email.MailSendingService(ADMIN_USERNAME, user, body, EmailConstants.MEMBERSHIP_APPROVED);

			} else {
				throw new IllegalArgumentException("All conditions for accountant approval are not met");
			}

		} else {
			throw new Exception("You don't have access !!");
		}

		registrationFromRepository.save(specificUserDetails);
		progressBarRepository.save(progressBarReport);

	}

	@Override
	public ProgressBarReport progressBarForAUser(String id) {

		return progressBarRepository.findById(id).get();
	}

	@Override
	public List<RegistrationFrom> committeePresidentAccountantViewListOfApplicants(String token) {

		if (token == null || token.isEmpty()) {
			throw new IllegalArgumentException("Token cannot be null or empty");
		}

		JSONObject jsonObject = decodeToken(token);
		if (!jsonObject.has("id") || !jsonObject.has("type")) {
			throw new IllegalArgumentException("Token must contain 'id' and 'type' fields");
		}

		String userType = jsonObject.getString("type");

		if(userType.equalsIgnoreCase(ApplicationConstants.COMMITEE) || userType.equalsIgnoreCase(ApplicationConstants.PRESIDENT)) {
			return registrationFromRepository.findAll();
		}else if(userType.equalsIgnoreCase(ApplicationConstants.ACCOUNTANT)) {
			return registrationFromRepository.findAll().stream()
					.filter(registrationForm -> registrationForm.getPaymentInfo() != null)
					.collect(Collectors.toList());
		}else {
			throw new IllegalArgumentException("Invalid user type");
		}

	}

	@Override
	public RegistrationFrom presidentFillingRegistrationThreeForm(String token, String appicantId, String categoryOfMemberShipRecomendedByPresident, String remarks) {

		if (token == null || token.isEmpty()) {
			throw new IllegalArgumentException("Token cannot be null or empty");
		}

		JSONObject jsonObject = decodeToken(token);
		if (!jsonObject.has("id") || !jsonObject.has("type")) {
			throw new IllegalArgumentException("Token must contain 'id' and 'type' fields");
		}

		String userType = jsonObject.getString("type");

		if(userType.equalsIgnoreCase(ApplicationConstants.PRESIDENT)) {
			RegistrationFrom form = registrationFromRepository.findById(appicantId).get();

			form.setPresidentChoosenMembershipForApplicant(categoryOfMemberShipRecomendedByPresident);
			form.setPresidentRemarksForApplicant(remarks);

			registrationFromRepository.save(form);

		}else {
			throw new IllegalArgumentException("Invalid access");
		}

		return null;
	}

	@Override
	public RegistrationFrom userFillingRegistrationThreeForm(String applicantId, boolean isMemberOfOtherCommunity
			, boolean isDecleration, String nativePlace) {

		RegistrationFrom specificUserDetails = registrationFromRepository.findById(applicantId).get();
		if (specificUserDetails == null) {
			throw new IllegalArgumentException("No user found with the provided phone number");
		}

		Optional<ProgressBarReport> optionalProgressBarReport = progressBarRepository.findById(applicantId);
		if (!optionalProgressBarReport.isPresent()) {
			throw new IllegalArgumentException("No progress bar report found for the user");
		}

		ProgressBarReport progressBarReport = optionalProgressBarReport.get();

		specificUserDetails.setNativePlace(nativePlace);
		specificUserDetails.setMemberOfOtherCommunity(isMemberOfOtherCommunity);
		specificUserDetails.setApplicationForMembershipDeclaration(isDecleration);

		registrationFromRepository.save(specificUserDetails);

		progressBarReport.setRegistrationThreeFormCompleted(RegistrationFormConstants.TRUE);
		progressBarRepository.save(progressBarReport);

		return specificUserDetails;
	}

	@Override
	public Page<PaymentInfo> accountFirstView(int page, int size) {
		Pageable pageable = PageRequest.of(page, size);
		return paymentRepository.findAll(pageable);
	}

	@Override
	public ResponseEntity getDocumentOfUser(String userId) throws MalformedURLException {

		RegistrationFrom user= registrationFromRepository.findById(userId).get();
		String documentName=null;
		if(user.getAadharCard()!=null) {
			documentName =user.getAadharCard();
			System.out.println(documentName);
		}
		else if(user.getVoterIdCard()!=null) {
			documentName = user.getVoterIdCard();
		}
		else  {
			documentName = user.getProfilePic();
		}
		Path filePath = Paths.get(UPLOAD_DIR +"\\"+documentName);
		System.out.println(filePath);
		Resource resource = new UrlResource(filePath.toUri());
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("application/octet-stream"))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
				.body(resource);
	}

	@Override
	public ResponseEntity uploadTranscationRecepit(String token, MultipartFile transcationRecepit) {
		return null;
	}



}
