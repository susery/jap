/*
 * Copyright (c) 2020-2040, 北京符节科技有限公司 (support@fujieid.com & https://www.fujieid.com).
 * <p>
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE 3.0;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.gnu.org/licenses/lgpl.html
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.fujieid.jap.simple;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.extra.servlet.ServletUtil;
import com.fujieid.jap.core.AuthenticateConfig;
import com.fujieid.jap.core.JapConfig;
import com.fujieid.jap.core.JapUser;
import com.fujieid.jap.core.JapUserService;
import com.fujieid.jap.core.cache.JapCache;
import com.fujieid.jap.core.exception.JapUserException;
import com.fujieid.jap.core.strategy.AbstractJapStrategy;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * The local authentication strategy authenticates requests based on the credentials submitted through an HTML-based
 * login form.
 *
 * @author yadong.zhang (yadong.zhang0415(a)gmail.com)
 * @version 1.0.0
 * @since 1.0.0
 */
public class SimpleStrategy extends AbstractJapStrategy {

    /**
     * `Strategy` constructor.
     *
     * @param japUserService japUserService
     * @param japConfig      japConfig
     */
    public SimpleStrategy(JapUserService japUserService, JapConfig japConfig) {
        super(japUserService, japConfig);
    }

    /**
     * `Strategy` constructor.
     *
     * @param japUserService japUserService
     * @param japConfig      japConfig
     * @param japCache       japCache
     */
    public SimpleStrategy(JapUserService japUserService, JapConfig japConfig, JapCache japCache) {
        super(japUserService, japConfig, japCache);
    }

    @Override
    public void authenticate(AuthenticateConfig config, HttpServletRequest request, HttpServletResponse response) {
        // Convert AuthenticateConfig to SimpleConfig
        this.checkAuthenticateConfig(config, SimpleConfig.class);
        SimpleConfig simpleConfig = (SimpleConfig) config;

        if (this.checkSessionAndCookie(simpleConfig, request, response)) {
            return;
        }

        UsernamePasswordCredential credential = this.doResolveCredential(request, simpleConfig);
        JapUser user = japUserService.getByName(credential.getUsername());
        if (null == user) {
            throw new JapUserException("The user does not exist.");
        }

        boolean valid = japUserService.validPassword(credential.getPassword(), user);
        if (!valid) {
            throw new JapUserException("Passwords don't match.");
        }

        this.loginSuccess(simpleConfig, credential, user, request, response);
    }

    /**
     * 登录成功
     *
     * @param simpleConfig Authenticate Config
     * @param credential   Username password credential
     * @param user         Jap user
     * @param request      The request to authenticate
     * @param response     The response to authenticate
     */
    private void loginSuccess(SimpleConfig simpleConfig, UsernamePasswordCredential credential, JapUser user, HttpServletRequest request, HttpServletResponse response) {
        if (credential.isRememberMe()) {
            String cookieDomain = ObjectUtil.isNotEmpty(simpleConfig.getRememberMeCookieDomain()) ? simpleConfig.getRememberMeCookieDomain() : null;
            // add cookie
            ServletUtil.addCookie(response,
                simpleConfig.getRememberMeCookieKey(),
                this.encodeCookieValue(user, simpleConfig),
                simpleConfig.getRememberMeCookieExpire(),
                "/",
                cookieDomain
            );
        }
        this.loginSuccess(user, request, response);
    }

    /**
     * check session and cookie
     *
     * @param simpleConfig Authenticate Config
     * @param request      The request to authenticate
     * @param response     The response to authenticate
     * @return true to login success, false to login
     */
    private boolean checkSessionAndCookie(SimpleConfig simpleConfig, HttpServletRequest request, HttpServletResponse response) {
        if (this.checkSession(request, response)) {
            return true;
        }
        if (!RememberMeUtils.enableRememberMe(request, simpleConfig)) {
            return false;
        }

        Cookie cookie = ServletUtil.getCookie(request, simpleConfig.getRememberMeCookieKey());
        if (ObjectUtil.isNull(cookie)) {
            return false;
        }

        UsernamePasswordCredential credential = this.decodeCookieValue(simpleConfig, cookie.getValue());
        if (ObjectUtil.isNull(credential)) {
            return false;
        }

        JapUser user = japUserService.getByName(credential.getUsername());
        if (null == user) {
            throw new JapUserException("The user does not exist.");
        }
        // redirect login successful
        this.loginSuccess(user, request, response);
        return true;
    }

    /**
     * decode Username password credential
     *
     * @param simpleConfig Authenticate Config
     * @param cookieValue  Cookie value
     * @return Username password credential
     */
    private UsernamePasswordCredential decodeCookieValue(SimpleConfig simpleConfig, String cookieValue) {
        RememberMeDetails details = RememberMeUtils.decode(simpleConfig, cookieValue);
        if (ObjectUtil.isNotNull(details)) {
            // return no longer password and remember me
            return new UsernamePasswordCredential()
                .setUsername(details.getUsername());
        }
        return null;
    }

    /**
     * The value of the encrypted cookie
     *
     * @param user         Jap user
     * @param simpleConfig Authenticate Config
     * @return Encode cookie value string
     */
    private String encodeCookieValue(JapUser user, SimpleConfig simpleConfig) {
        return RememberMeUtils.encode(simpleConfig, user.getUsername()).getEncodeValue();
    }

    /**
     * @param request      The request to authenticate
     * @param simpleConfig Authenticate Config
     * @return Username password credential
     */
    private UsernamePasswordCredential doResolveCredential(HttpServletRequest request, SimpleConfig simpleConfig) {
        String username = request.getParameter(simpleConfig.getUsernameField());
        String password = request.getParameter(simpleConfig.getPasswordField());
        if (null == username || null == password) {
            throw new JapUserException("Missing credentials");
        }
        return new UsernamePasswordCredential()
            .setUsername(username)
            .setPassword(password)
            .setRememberMe(
                BooleanUtil.toBoolean(
                    request.getParameter(simpleConfig.getRememberMeField()))
            );
    }
}
