package com.neo.caption.ocr.config;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.gson.Gson;
import com.neo.caption.ocr.CaptionOCR;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.tesseract.TessBaseAPI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

@Data
@Configuration
@Slf4j
public class BaiduOcrConfig {

    @Value("${com.baidu.aip.appid}")
    private String appId;
    @Value("${com.baidu.aip.appkey}")
    private String apiKey;
    @Value("${com.baidu.aip.appsecret}")
    private String secretKey;
    @Value("${com.baidu.aip.qpslimit}")
    private String qpslimit;

}
