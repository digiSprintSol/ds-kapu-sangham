package com.digisprint.serviceImpl;

import java.io.IOException;
import java.net.MalformedURLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import javax.mail.MessagingException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.digisprint.EmailUtils.EmailService;
import com.digisprint.bean.AccessBean;
import com.digisprint.bean.EventsImagesAnnouncements;
import com.digisprint.bean.Image;
import com.digisprint.bean.MarketPlaces;
import com.digisprint.bean.RegistrationForm;
import com.digisprint.bean.UserResponse;
import com.digisprint.exception.UserNotFoundException;
import com.digisprint.filter.JwtTokenUtil;
import com.digisprint.repository.AccessBeanRepository;
import com.digisprint.repository.EventsImagesAnnouncementsRepo;
import com.digisprint.repository.ImageRepository;
import com.digisprint.repository.MarketPlaceRepository;
import com.digisprint.responseBody.AwardsResponse;
import com.digisprint.responseBody.EventsResponse;
import com.digisprint.responseBody.FilterMemberResponse;
import com.digisprint.responseBody.GalleryResponse;
import com.digisprint.responseBody.GetDocumentURL;
import com.digisprint.responseBody.LoginResponse;
import com.digisprint.service.AccessBeanService;
import com.digisprint.utils.ApplicationConstants;
import com.digisprint.utils.EmailConstants;
import com.digisprint.utils.ErrorResponseConstants;
import com.digisprint.utils.RegistrationFormConstants;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AccessBeanServiceImpl implements AccessBeanService{

	private AccessBeanRepository accessBeanRepository;

	private	EventsImagesAnnouncementsRepo eventsImagesAnnouncementsRepo; 

	private JwtTokenUtil jwtTokenUtil;

	private MarketPlaceRepository marketPlaceRepository;

	private ImageRepository imageRepository;

	private EmailService email;

	public AccessBeanServiceImpl(AccessBeanRepository accessBeanRepository,
			EventsImagesAnnouncementsRepo eventsImagesAnnouncementsRepo, JwtTokenUtil jwtTokenUtil,
			MarketPlaceRepository marketPlaceRepository, ImageRepository imageRepository, EmailService email) {
		super();
		this.accessBeanRepository = accessBeanRepository;
		this.eventsImagesAnnouncementsRepo = eventsImagesAnnouncementsRepo;
		this.jwtTokenUtil = jwtTokenUtil;
		this.marketPlaceRepository = marketPlaceRepository;
		this.imageRepository = imageRepository;
		this.email = email;
	}

	@Value("${config.secretKey}")
	private  String secretKey;
	
	@Value("${spring.mail.username}")
	private String ADMIN_USERNAME;

	@Autowired
	HttpServletResponse response;

	@Override
	public ResponseEntity saveInternalUsers(AccessBean accessBean) {

		if(accessBeanRepository.findByEmail(accessBean.getEmail()).isPresent()) {
			return new ResponseEntity(ErrorResponseConstants.EMAIL_ALREADY_EXISTS,HttpStatus.INTERNAL_SERVER_ERROR);
		}
		else {
			accessBean.setDeleted(false);
			accessBeanRepository.save(accessBean);
			return new ResponseEntity(accessBean,HttpStatus.OK);
		}
	}

	@Override
	public ResponseEntity getAllInternalUsers() {

		List<AccessBean> getAllUsers = accessBeanRepository.findAll();
		if(getAllUsers.size()==0) {
			return new ResponseEntity(ErrorResponseConstants.NO_USERS_FOUND,HttpStatus.INTERNAL_SERVER_ERROR);
		}
		else {
			getAllUsers	= getAllUsers.stream().filter(user -> user.isDeleted()==false).collect(Collectors.toList());
			return new ResponseEntity(getAllUsers,HttpStatus.OK);
		}

	}

	@Override
	public ResponseEntity fetchInternalUsersById(String id) {
		AccessBean internalUsers = new AccessBean();
		try {
			internalUsers = accessBeanRepository.findById(id).orElseThrow(()->new UserNotFoundException(ErrorResponseConstants.USER_NOT_FOUND));
		} catch (Exception e) {
			return new ResponseEntity(ErrorResponseConstants.USER_NOT_FOUND,HttpStatus.INTERNAL_SERVER_ERROR);
		}

		return new ResponseEntity(internalUsers,HttpStatus.OK);

	}

	@Override
	public ResponseEntity softDeleteInternalUsers(String id) {
		AccessBean internalUsers = new AccessBean();

		try {
			internalUsers = accessBeanRepository.findById(id)
					.orElseThrow(()->new UserNotFoundException(ErrorResponseConstants.USER_NOT_FOUND));
			internalUsers.setDeleted(true);
			accessBeanRepository.save(internalUsers);
		} catch (Exception e) {
			return new ResponseEntity(ErrorResponseConstants.USER_NOT_FOUND,HttpStatus.INTERNAL_SERVER_ERROR);
		}

		return new ResponseEntity(internalUsers,HttpStatus.OK);
	}		


	private List<String> getAccessList(AccessBean accessBean){

		List<String> accessList = new ArrayList();
		if(accessBean.isPresident()){
			accessList.add(ApplicationConstants.PRESIDENT);
		}
		if(accessBean.isAccountant()){
			accessList.add(ApplicationConstants.ACCOUNTANT);
		}
		if(accessBean.isCommitee()){
			accessList.add(ApplicationConstants.COMMITEE);
		}
		if(accessBean.isUser()){
			accessList.clear();
			accessList.add(ApplicationConstants.USER);
		}

		return accessList;

	} 

	@Override
	public ResponseEntity  login(String userName, String password) {
		AccessBean accessBean = accessBeanRepository.findByEmailAndPassword(userName, password);
		String cookie = jwtTokenUtil.generateToken(userName, accessBean.getAccessId(), getAccessList(accessBean), password);
		Cookie cookie1 = new Cookie("token",cookie);
		cookie1.setHttpOnly(true); // Make the coo kie HTTP-only
		cookie1.setSecure(false); // Secure flag ensures cookie is sent over HTTPS
		cookie1.setMaxAge(60 * 60 * 24); // Set cookie expiration (in seconds)
		response.addCookie(cookie1);
		cookie1.setPath("/"); 
		//			System.out.println("cookies get values::"+cookie1.getValue());
		LoginResponse loginResponse = new LoginResponse();
		loginResponse.setToken(cookie);
		return new ResponseEntity(loginResponse,HttpStatus.OK);
	}

	public  Claims decodeAndValidateToken(String token) {
		try {
			return Jwts.parser()
					.setSigningKey(secretKey.getBytes())
					.parseClaimsJws(token)
					.getBody();
		} catch (Exception e) {
			log.error(ErrorResponseConstants.INVALID_TOKEN + e.getMessage());
			return null;
		}
	}

	@Override
	public ResponseEntity validateAndGenerateToken(String token) {

		AccessBean internalUsers = new AccessBean();
		UserResponse userresponse = new UserResponse();
		try {
			Claims claims = decodeAndValidateToken(token);
			if (claims == null) {
				throw new UserNotFoundException(ErrorResponseConstants.USER_NOT_FOUND);
			}

			String userName = String.valueOf(claims.get(ApplicationConstants.USERNAME)).replace(ApplicationConstants.REPLACE_WITH_FORWARDSLASH, ApplicationConstants.EMPTY_QUOTATION_MARK).trim().toLowerCase();
			internalUsers = accessBeanRepository.findByEmail(userName).orElseThrow(()-> new  UserNotFoundException(ErrorResponseConstants.USER_NOT_FOUND));
			userresponse.setAccessId(internalUsers.getAccessId());
			userresponse.setName(internalUsers.getName());
			userresponse.setPresident(internalUsers.isPresident());
			userresponse.setCommitee(internalUsers.isCommitee());
			userresponse.setAccountant(internalUsers.isAccountant());
			userresponse.setUser(internalUsers.isUser());
			userresponse.setAdmin(internalUsers.isAdmin());
			userresponse.setEmail(internalUsers.getEmail());
			userresponse.setToken(jwtTokenUtil.generateToken(internalUsers.getName(), internalUsers.getAccessId(), getAccessList(internalUsers),
					String.valueOf(claims.get(ApplicationConstants.OID)).replace(ApplicationConstants.REPLACE_WITH_FORWARDSLASH, ApplicationConstants.EMPTY_QUOTATION_MARK).trim().toLowerCase()));
		} catch (Exception e) {
			return new ResponseEntity(ErrorResponseConstants.USER_NOT_FOUND,HttpStatus.INTERNAL_SERVER_ERROR);
		}

		if (userresponse != null) {
			return new ResponseEntity(userresponse,HttpStatus.OK);
		}
		else {
			return new ResponseEntity(ErrorResponseConstants.USER_NOT_FOUND,HttpStatus.INTERNAL_SERVER_ERROR);
		}

	}

	@Override
	public ResponseEntity postingAnnouncements(String title, String description) {

		if(title!=null && description !=null) {
			EventsImagesAnnouncements announcement = new EventsImagesAnnouncements();
			announcement.setAnnouncementTitle(title);
			announcement.setAnnouncementDescription(description);
			announcement.setAnnouncement(true);
			eventsImagesAnnouncementsRepo.save(announcement);
			return new ResponseEntity("Announcements created",HttpStatus.OK);
		}
		else {
			return new ResponseEntity("Inputs are not proper",HttpStatus.INTERNAL_SERVER_ERROR);

		}
	}

	@Override
	public ResponseEntity getAllAnnouncement() {
		List<EventsImagesAnnouncements>announcements =eventsImagesAnnouncementsRepo.findByAnnouncement(true);
		if(announcements.size()==0) {
			return new ResponseEntity("No Announcements found",HttpStatus.NOT_FOUND);
		}
		else {
			return new ResponseEntity(announcements,HttpStatus.OK);
		}
	}

	@Override
	public ResponseEntity getEvents() throws MalformedURLException {
		EventsImagesAnnouncements event= eventsImagesAnnouncementsRepo.findById("1").get();
		EventsResponse eventsResponse = new EventsResponse();
		BeanUtils.copyProperties(event, eventsResponse);
		return new ResponseEntity (eventsResponse,HttpStatus.OK);
	}

	@Override
	public ResponseEntity getSelectedMarketPlace(String marketPlaceId) {
		MarketPlaces marketPlaces = marketPlaceRepository.findById(marketPlaceId).get();
		GetDocumentURL documentURL = new GetDocumentURL();
		documentURL.setPathOfDocumnet(marketPlaces.getImageUrl());
		return new ResponseEntity<>(documentURL,HttpStatus.OK);
	}

	public JSONObject decodeToken(String jwtToken) {
		return JwtTokenUtil.decodeUserToken(jwtToken);
	}

	@Override
	public ResponseEntity postMarketPlace(String token, MarketPlaces marketPlaces) throws IOException {

		JSONObject jsonObject = decodeToken(token);
		if (!jsonObject.has("userId") || !jsonObject.has("access")) {
			throw new IllegalArgumentException("Token must contain 'id' and 'access' fields");
		}

		List accessList = jwtTokenUtil.getAccessList(token);

		if(accessList.contains(ApplicationConstants.PRESIDENT)){

			marketPlaces.setCreatedDate(LocalDateTime.now());

			 MarketPlaces marketPlace = marketPlaceRepository.save(marketPlaces);
			return new ResponseEntity(marketPlace,HttpStatus.OK);
		}
		else {
			return new ResponseEntity("No access found",HttpStatus.NOT_FOUND);
		}
	}

	@Override
	public ResponseEntity getAllMarketPlaces() {

		List<MarketPlaces> marketPlacesList	= marketPlaceRepository.findAll();

		if(marketPlacesList.size()==0) {
			return new ResponseEntity("No data found",HttpStatus.NOT_FOUND);
		}
		else {
			return new ResponseEntity(marketPlacesList,HttpStatus.OK);
		}
	}

	@Override
	public List<String> getAllCategories() {

		List<MarketPlaces> list = marketPlaceRepository.findAll();

		return list.stream()
				.map(MarketPlaces::getCategory)
				.distinct()             
				.collect(Collectors.toList());
	}

	@Override
	public List<String> getAllCities() {

		List<MarketPlaces> list = marketPlaceRepository.findAll();

		return list.stream()
				.map(MarketPlaces::getCity)
				.distinct()
				.collect(Collectors.toList());
	}

	@Override
	public ResponseEntity deleteAnnouncement(String id) {
		try {
			Optional<EventsImagesAnnouncements> imageOptional = eventsImagesAnnouncementsRepo.findById(id);

			if (imageOptional.isPresent()) {
				eventsImagesAnnouncementsRepo.deleteById(id);

				return ResponseEntity.ok("Image with id " + id + " has been successfully deleted.");
			} else {
				return ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body("Image with id " + id + " not found.");
			}
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("An error occurred while trying to delete the image.");
		}
	}

	@Override
	public ResponseEntity<String> uploadEventsAnnouncementsGalleryAwardsQRCodeImages(String title, String description, String imageUrl) throws MalformedURLException {
		try {
			if (imageUrl == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or missing image URL");
			}

			Image image = imageRepository.findByUrl(imageUrl);

			if (image == null) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
			}

			String folderName = image.getFolderName();

			if (folderName == null || folderName.trim().isEmpty()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder name is missing or invalid");
			}

			EventsImagesAnnouncements eventsImagesAnnouncements = new EventsImagesAnnouncements();

			switch (folderName) {
			case ApplicationConstants.EVENTS:
				eventsImagesAnnouncements.setId("1");
				eventsImagesAnnouncements.setEventDescription(description);
				eventsImagesAnnouncements.setEventTitle(title);
				eventsImagesAnnouncements.setEventImageURL(imageUrl);
				eventsImagesAnnouncements.setEvents(true);
				break;
			case ApplicationConstants.GALLERY:
				eventsImagesAnnouncements.setGalleryDescription(description);
				eventsImagesAnnouncements.setGalleryTitle(title);
				eventsImagesAnnouncements.setGalleryURL(imageUrl);
				eventsImagesAnnouncements.setGallery(true);
				break;
			case ApplicationConstants.AWARDS:
				eventsImagesAnnouncements.setAwardDescription(description);
				eventsImagesAnnouncements.setAwardsTitle(title);
				eventsImagesAnnouncements.setAwardImageURL(imageUrl);
				eventsImagesAnnouncements.setAwards(true);
				break;
			case ApplicationConstants.QR_CODE:
				eventsImagesAnnouncements.setId("2");
				eventsImagesAnnouncements.setQrCodeImageUrl(imageUrl);
				eventsImagesAnnouncements.setQrCode(true);
				break;
			case ApplicationConstants.DONATIONS_QR_CODE:
				eventsImagesAnnouncements.setId("3");
				eventsImagesAnnouncements.setQrCodeImageUrl(imageUrl);
				eventsImagesAnnouncements.setQrCode(true);
				break;
			default:
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid folder name");
			}

			eventsImagesAnnouncementsRepo.save(eventsImagesAnnouncements);

			return new ResponseEntity<>("Files Uploaded Successfully", HttpStatus.OK);

		} catch (ResponseStatusException e) {
			return new ResponseEntity<>(e.getReason(), e.getStatus());
		} catch (DataAccessException e) {
			return new ResponseEntity<>("Database error occurred while saving the data", HttpStatus.INTERNAL_SERVER_ERROR);
		} catch (Exception e) {
			return new ResponseEntity<>("An unexpected error occurred: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Override
	public ResponseEntity getAllGallery() {
		List<EventsImagesAnnouncements> galleryItems = eventsImagesAnnouncementsRepo.findByGalleryTrue();
		if(galleryItems.size()==0) {
			return new ResponseEntity(ErrorResponseConstants.ERROR_NO_DATA_FOUND,HttpStatus.NOT_FOUND);
		}
		else {
		List<GalleryResponse> galleryResponsesList = galleryItems.stream().map(p->{
			GalleryResponse galleryResponse = new GalleryResponse();
			BeanUtils.copyProperties(p, galleryResponse);
			return galleryResponse;
		}).collect(Collectors.toList());
		return  new ResponseEntity(galleryResponsesList,HttpStatus.OK);
		}
	}

	@Override
	public ResponseEntity getAllAwards() {
		List<EventsImagesAnnouncements> awardsItems = eventsImagesAnnouncementsRepo.findByAwardsTrue();
		if(awardsItems.size()==0) {
			return new ResponseEntity(ErrorResponseConstants.ERROR_NO_DATA_FOUND,HttpStatus.NOT_FOUND);
		}
		else {
			List<AwardsResponse> awardsResponseList = awardsItems.stream().map(p->{
				AwardsResponse awardsResponse = new AwardsResponse();
				BeanUtils.copyProperties(p, awardsResponse);
				return awardsResponse;
			}).collect(Collectors.toList());
			return  new ResponseEntity(awardsResponseList,HttpStatus.OK);
		}
	}

	char[] OTP(int length) {
		String numbers = "0123456";
		Random random = new Random();
		char[] otp = new char[4];
		for(int i=0; i<4; i++) {
			otp[i]=numbers.charAt(random.nextInt(numbers.length()));
		}
		System.out.println(otp);
		return otp;
	}

	@Override
	public ResponseEntity verifyEmail(String email) throws UserNotFoundException, IOException, MessagingException {

		AccessBean accessBean =verifyEmailEntered(email);
		long generatedOTP = OTP(6).hashCode();
		Long longOtp = generatedOTP;
		CharSequence otp = longOtp.toString().subSequence(0, 6);
		accessBean.setOtp(otp);

		String[] newUser= new String[1];
		newUser[0] = accessBean.getEmail();
		String	body = EmailConstants.OTP_BODY
				.replace(EmailConstants.REPLACE_PLACEHOLDER_NAME, accessBean.getName())
				.replace(EmailConstants.REPLACE_GENERATED_OTP, otp);
		this.email.MailSendingService(ADMIN_USERNAME, newUser,body, EmailConstants.FORGET_PASSWORD_OTP);

		accessBeanRepository.save(accessBean);
		return new ResponseEntity(email,HttpStatus.OK);
	}

	@Override
	public ResponseEntity verifyOtp(String email,String otp) throws UserNotFoundException {

		AccessBean accessBean =verifyEmailEntered(email);
		if(accessBean.getOtp().equals(otp)) {
			return new ResponseEntity("email verified",HttpStatus.OK);
		}
		else {
			return new ResponseEntity("OTP incorrect",HttpStatus.NO_CONTENT);
		}
	}

	@Override
	public ResponseEntity forgotPassword(String email,String newPassword) throws UserNotFoundException {
		AccessBean accessBean =verifyEmailEntered(email);
		accessBean.setPassword(newPassword);
		accessBean.setOtp(null);
		accessBeanRepository.save(accessBean);
		return new ResponseEntity("Your new password is reset try to ",HttpStatus.OK);
	}

	private AccessBean verifyEmailEntered(String email) throws UserNotFoundException {
		AccessBean accessBean = accessBeanRepository.findByEmail(email).orElseThrow(()-> new  UserNotFoundException(ErrorResponseConstants.USER_NOT_FOUND));
		return accessBean;
	}

	@Override
	public ResponseEntity getQRCode(String id) {
		
		if(id.equals("2")) {
		
		EventsImagesAnnouncements qrCode = eventsImagesAnnouncementsRepo.findById(id).get();
		GetDocumentURL documentURL = new GetDocumentURL();
		documentURL.setPathOfDocumnet(qrCode.getQrCodeImageUrl());
		return new ResponseEntity(documentURL,HttpStatus.OK);
		}
		else {
			EventsImagesAnnouncements qrCode = eventsImagesAnnouncementsRepo.findById("3").get();
			GetDocumentURL documentURL = new GetDocumentURL();
			documentURL.setPathOfDocumnet(qrCode.getQrCodeImageUrl());
			return new ResponseEntity(documentURL,HttpStatus.OK);
		}
	}

	
}

