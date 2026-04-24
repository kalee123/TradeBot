package com.app.tradebot.authentication;

import com.app.tradebot.appsetup.UrlConstants;
import com.app.tradebot.config.KiteApiClient;
import com.app.tradebot.entity.AuthTokenEntity;
import com.app.tradebot.entity.UserEntity;
import com.app.tradebot.repository.UserRepository;
import com.app.tradebot.service.KiteAuthService;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.app.tradebot.appsetup.UrlConstants.BASE_URL;
import static com.app.tradebot.appsetup.UrlConstants.KITE_USER_PROFILE_PATH;

@Service
public class UserAuthService {

    @Autowired
    KiteAuthService authService;

    @Autowired
    @Lazy
    private UserRepository userRepository;

    @Autowired
    @Lazy
    private KiteApiClient kiteApiClient;

    public String getLoginUrl(String state) {
        return authService.getLoginUrl(state);
    }

    public String authenticateUser(String requestToken) {
        try{
            String accessToken = authService.exchangeRequestTokenForAccessToken(requestToken);
            getUserInfo(accessToken);
            return accessToken;
        }
        catch (Exception e) {
            throw new RuntimeException("access_token not found in response: " + e);
        }
    }

    public String getUserInfo(String accessToken) {
        String profileDetails = null;
        try {
            profileDetails = kiteApiClient.get(UrlConstants.KITE_USER_PROFILE_PATH, accessToken);
            JSONObject userInfo = new JSONObject(profileDetails);
            JSONObject userJson = userInfo.getJSONObject("data");
            String name= userJson.optString("user_name", "Unknown");
            String userId = userJson.optString("user_id", "Unknown");
            String email = userJson.optString("email", "Unknown");
            String broker = userJson.optString("broker", "Unknown");

            UserEntity user = new UserEntity();
            user.setUserName(name);
            user.setEmail(email);
            user.setBroker(broker);
            user.setUserBrokerId(userId);

            AuthTokenEntity token = new AuthTokenEntity();
            token.setAccessToken(accessToken);
            token.setApiKey("YOUR_API_KEY");
            token.setExpiresAt(LocalDateTime.now().plusHours(12));
            user.addAuthToken(token);

            userRepository.save(user);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return profileDetails;
    }
}
