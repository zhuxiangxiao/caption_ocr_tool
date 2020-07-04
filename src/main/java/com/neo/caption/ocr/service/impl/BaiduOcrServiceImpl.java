package com.neo.caption.ocr.service.impl;

import com.baidu.aip.ocr.AipOcr;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.util.concurrent.RateLimiter;
import com.neo.caption.ocr.config.BaiduOcrConfig;
import com.neo.caption.ocr.exception.TessException;
import com.neo.caption.ocr.pojo.AppHolder;
import com.neo.caption.ocr.service.OCRService;
import com.neo.caption.ocr.util.FxUtil;
import com.neo.caption.ocr.view.MatNode;
import javafx.scene.control.ProgressBar;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.HashMap;

import static com.google.common.collect.ImmutableList.toImmutableList;

@Service
@Slf4j
public class BaiduOcrServiceImpl implements OCRService {


    private final FxUtil fxUtil;
    private final AppHolder appHolder;
    private final AipOcr client;
    private boolean ready;
    private final RateLimiter rateLimiter;

    public BaiduOcrServiceImpl(FxUtil fxUtil, AppHolder appHolder, BaiduOcrConfig baiduOcrConfig) {
        this.fxUtil = fxUtil;
        this.appHolder = appHolder;
        this.client = new AipOcr(baiduOcrConfig.getAppId(), baiduOcrConfig.getApiKey(), baiduOcrConfig.getSecretKey());
        this.rateLimiter = RateLimiter.create(Double.valueOf(baiduOcrConfig.getQpslimit()));
    }

    @PostConstruct
    public void init() {
        this.ready = false;
    }

    @Override
    public void apiInit() throws TessException {
    }

    @Override
    public String doOCR(Mat mat) {
        HashMap<String, String> options = new HashMap<>();
        options.put("detect_direction", "false");
        options.put("probability", "false");
        MatOfByte mob = new MatOfByte();
        Imgcodecs.imencode(".bmp", mat, mob);
        byte[] file = mob.toArray();
        rateLimiter.acquire();
        JSONObject res = client.basicGeneral(file, options);
        log.info("ocr result:" + res.toString());
        if (res.has("error_code") && res.getInt("error_code") == 18) {
            log.error(res.getString("error_msg"));
            return doOCR(mat);
        }
        if (res.has("error_msg") && !StringUtils.isEmpty(res.getString("error_msg"))) {
            log.error(res.getString("error_msg"));
            return "";
        }
        JSONArray wordResult = res.getJSONArray("words_result");
        StringBuilder words = new StringBuilder("");
        wordResult.forEach(word -> {
            String tmp = ((JSONObject) word).getString("words");
            words.append(tmp);
            words.append("\n");
        });
        return words.toString();
    }

    @Override
    public Integer doOCR(ProgressBar jfxProgressBar) {
        StringBuilder stringBuilder = appHolder.getStringBuilder();
        UnmodifiableIterator<MatNode> unmodifiableIterator = appHolder.getMatNodeList()
                .stream()
                .collect(toImmutableList())
                .iterator();
        int count = 0;
        int len = appHolder.getMatNodeList().size() - 1;
        while (unmodifiableIterator.hasNext() && !Thread.currentThread().isInterrupted()) {
            fxUtil.onFXThread(jfxProgressBar.progressProperty(), (double) count / len);
            stringBuilder.append(doOCR(unmodifiableIterator.next().getMat()));
            count++;
        }
        appHolder.setOcr(stringBuilder.toString());
        return unmodifiableIterator.hasNext() ? 0 : 1;
    }

    @Override
    public boolean isReady() {
        return ready;
    }
}
