package org.gluu.agama.jans.service;

import io.jans.as.common.model.common.User;
import io.jans.as.common.service.common.EncryptionService;
import io.jans.as.common.service.common.UserService;
import io.jans.model.SmtpConfiguration;
import io.jans.orm.exception.operation.EntryNotFoundException;
import io.jans.service.MailService;
import io.jans.service.cdi.util.CdiUtil;
import io.jans.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import io.jans.as.common.service.common.ConfigurationService;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;

import org.gluu.agama.jans.EmailTemplate;
import org.gluu.agama.jans.MagicLinkService;
import org.gluu.agama.jans.model.ContextData;
import static org.gluu.agama.smtp.jans.Attrs.*;

public class Service extends MagicLinkService{

    private static final Logger logger = LoggerFactory.getLogger(MagicLinkService.class);

    private static final String HOST = "https://mmrraju-set-werewolf.gluu.info/";
    private static final String MAIL = "mail";
    private static final String UID = "uid";
    private static final String DISPLAY_NAME = "displayName";
    private static final String GIVEN_NAME = "givenName";
    private static final String PASSWORD = "userPassword";
    private static final String INUM_ATTR = "inum";
    private static final String EXT_ATTR = "jansExtUid";
    private static final String USER_STATUS = "jansStatus";
    private static final String EXT_UID_PREFIX = "github:";
    private static final SecureRandom RAND = new SecureRandom();
    private static final String SECRET_KEY = "vfFYsdCNEreUsHKyl38b1wbIlf7PSxRm431ypSh6T3U=";
    private static final String SUBJECT_TEMPLATE = "MagicLink for authentication";
    private static final String MSG_TEMPLATE_TEXT = "%s is the magiclink to complete your verification";

    private static Service INSTANCE = null;
    private Service(){}

    public static synchronized Service getInstance(){
        if (INSTANCE== null) {
            INSTANCE = new Service();
        }
        return INSTANCE;
    }

    public String generateMagicLink(String token) throws Exception {

        return HOST + "jans-auth/magic-link?token=" + token;
    }

    public boolean verifyMagicLink(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(SECRET_KEY.getBytes());

            if (signedJWT.verify(verifier)) {
                Date expirationTime = signedJWT.getJWTClaimsSet().getExpirationTime();
                return expirationTime != null && expirationTime.after(new Date());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }    

    public String generateToken(String email){
        long expirationTime = System.currentTimeMillis() + (10 * 60 * 1000); // 10 minutes expiry

        JWSSigner signer = new MACSigner(SECRET_KEY.getBytes());
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder()
                        .subject(email)
                        .expirationTime(new Date(expirationTime))
                        .issueTime(new Date())
                        .build()
        );

        signedJWT.sign(signer);
        String token = signedJWT.serialize();

        return token;
    }

    public Map<String, String> getUserEntity(String email) {
        User user = getUser(MAIL, email);
        boolean local = user != null;
        logger.debug("There is {} local account for {}", local ? "a" : "no", email);
    
        if (local) {
            String uid = getSingleValuedAttr(user, UID);
            String inum = getSingleValuedAttr(user, INUM_ATTR);
            String name = getSingleValuedAttr(user, GIVEN_NAME);
    
            if (name == null) {
                name = getSingleValuedAttr(user, DISPLAY_NAME);
                if (name == null && email != null && email.contains("@")) {
                    name = email.substring(0, email.indexOf("@"));
                }
            }
    
            // Creating a truly modifiable map
            Map<String, String> userMap = new HashMap<>();
            userMap.put(UID, uid);
            userMap.put(INUM_ATTR, inum);
            userMap.put("name", name);
            userMap.put("email", email);
    
            return userMap;
        }
    
        return new HashMap<>();     
    }

    public String sendMail(String to, ContextData context) throws Exception {
        SmtpConfiguration smtpConfiguration = getSmtpConfiguration();

        String token = generateToken(to);
        String magicLink = generateMagicLink(token);

        String from = smtpConfiguration.getFromEmailAddress();
        String subject = String.format(SUBJECT_TEMPLATE);
        String textBody = String.format(MSG_TEMPLATE_TEXT, magicLink);
        String htmlBody = EmailTemplate.get(magicLink, context);

        MailService mailService = CdiUtil.bean(MailService.class);

        if (mailService.sendMailSigned(from, from, to, null, subject, textBody, htmlBody)) {
            logger.debug("E-mail has been delivered to {} with code {}", to, token);
            return token;
        }else{
            throw new EntryNotFoundException("Email sending failed. Please re-try");
        }
        logger.debug("E-mail delivery failed, check jans-auth logs");
        return null;      
    }

    private SmtpConfiguration getSmtpConfiguration() {
        ConfigurationService configurationService = CdiUtil.bean(ConfigurationService.class);
        SmtpConfiguration smtpConfiguration = configurationService.getConfiguration().getSmtpConfiguration();
        return smtpConfiguration;

    }    

    private String getSingleValuedAttr(User user, String attribute) {

        Object value = null;
        if (attribute.equals(UID)) {
            //user.getAttribute("uid", true, false) always returns null :(
            value = user.getUserId();
        } else {
            value = user.getAttribute(attribute, true, false);
        }
        return value == null ? null : value.toString();

    }    

    private static User getUser(String attributeName, String value) {
        UserService userService = CdiUtil.bean(UserService.class);
        return userService.getUserByAttribute(attributeName, value, true);
    }    

}
