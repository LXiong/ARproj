package com.mango.arproj.util;

/**
 * Created by mangguojun on 2018/5/30.
 */

public class ARurl {

    /**
     * 网络请求基地址
     */
    private static String baseurl = "http://120.78.177.77/ar/api/";

    /**
     * 获取用户接口URL
     * @return url
     */
    public static String getUserURL(){
        return baseurl+"user";
    }

    /**
     * 获取验证码接口URL
     * @return url
     */
    public static String getAuthCodeURL(){
        return baseurl+"authcode";
    }

    /**
     * 获取登录接口URL
     * @return url
     */
    public static String getLoginURL(){
        return baseurl+"login";
    }

    /**
     * 获取房间接口url
     * @return url
     */
    public static String getRoomURL(){
        return baseurl+"room";
    }

    /**
     * 获取队伍池接口URL
     * @return
     */
    public static String getTeamPoolURL(){
        return baseurl+"teampool";
    }

    /**
     * 获取AR信物接口URL
     * @return
     */
    public static String getARPackURL(){
        return baseurl+"arpack";
    }
}