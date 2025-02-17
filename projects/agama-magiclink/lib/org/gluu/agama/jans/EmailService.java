package org.gluu.agama.jans;

import org.gluu.agama.jans.service.JansEmailService;
import org.gluu.agama.jans.model.ContextData;

public abstract class EmailService {

    public abstract Map<String, String> getUserMapUsingEmail(String email);
    public abstract String sendEmail(String to, ContextData context);
    public abstract String onboardUser(Map<String, String> profile, Set<String> attributes, String extUid) throws Exception;

    public static EmailService getInstance(){
        return  JansEmailService.getInstance();
    }
}