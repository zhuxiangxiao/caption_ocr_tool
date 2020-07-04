package com.neo.caption.ocr.service.impl;

import com.neo.caption.ocr.aspect.AopException;
import com.neo.caption.ocr.exception.ModuleException;
import com.neo.caption.ocr.pojo.AppHolder;
import com.neo.caption.ocr.pojo.VideoHolder;
import com.neo.caption.ocr.service.OpenCVService;
import com.neo.caption.ocr.service.VideoService;
import com.neo.caption.ocr.util.DecimalUtil;
import com.neo.caption.ocr.util.FxUtil;
import com.neo.caption.ocr.view.MatDataNode;
import com.neo.caption.ocr.view.MatNode;
import javafx.scene.control.ProgressBar;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.neo.caption.ocr.constant.PrefKey.*;
import static com.neo.caption.ocr.util.BaseUtil.convertTime;
import static org.opencv.videoio.Videoio.*;

@Service
@Slf4j
public class VideoServiceImpl implements VideoService {

    private final OpenCVService openCVService;
    private final VideoHolder videoHolder;
    private final FxUtil fxUtil;
    private final AppHolder appHolder;

    private VideoCapture vc;
    private AtomicInteger count;
    private boolean finish;
    private final Map<Integer, Mat> sampleMap = new ConcurrentHashMap<>();
    private List<MatDataNode> matNodeList;
    private List<ArchiveMatNode> archiveMatNodeList;

    public VideoServiceImpl(OpenCVService openCVService, VideoHolder videoHolder,
                            FxUtil fxUtil, AppHolder appHolder) {
        this.openCVService = openCVService;
        this.videoHolder = videoHolder;
        this.fxUtil = fxUtil;
        this.appHolder = appHolder;
    }

    @PostConstruct
    public void init() {
        this.vc = null;
        this.archiveMatNodeList = new CopyOnWriteArrayList<>();
        this.matNodeList = new CopyOnWriteArrayList<>();

    }

    @Override
    public Integer loadVideo(File videoFile) {
        this.vc = new VideoCapture(videoFile.getAbsolutePath());
        videoHolder.setWidth((int) vc.get(CAP_PROP_FRAME_WIDTH))
                .setHeight((int) vc.get(CAP_PROP_FRAME_HEIGHT))
                .setFps(vc.get(CAP_PROP_FPS))
                .setTotalFrame((int) vc.get(CAP_PROP_FRAME_COUNT) - 1)
                .setRatio(DecimalUtil.divide(videoHolder.getHeight(), videoHolder.getWidth()).doubleValue());
        this.count = new AtomicInteger(0);
        this.finish = false;
        synchronized (archiveMatNodeList) {
            archiveMatNodeList.clear();
        }
        appHolder.getMatNodeList().clear();
        return 1;
    }

    @Override
    public boolean readFrame(Mat mat, double count) {
        if (!vc.isOpened()) {
            return false;
        }
        return vc.set(CAP_PROP_POS_FRAMES, count) && vc.read(mat);
    }

    @Override
    @AopException
    public void videoToCOCR(ProgressBar progressBar) throws ModuleException {
        if (!vc.isOpened()) {
            return;
        }
        boolean isSSIM = SIMILARITY_TYPE.intValue() == 0;
        double threshold = isSSIM
                ? MIN_SSIM_THRESHOLD.doubleValue()
                : MIN_PSNR_THRESHOLD.doubleValue();
        int frameInterval = FRAME_INTERVAL.intValue();
        // Since opencv 4.2.0, after setting the CAP_PROP_POS_FRAMES,
        // it needs to reset to 0, otherwise, it will start reading from where you previewed.
        vc.set(CAP_PROP_POS_FRAMES, 0);
//        List<CompletableFuture> futures = new ArrayList<>();
//        AtomicInteger index = new AtomicInteger(0);
        double fps = vc.get(CAP_PROP_FPS);
        System.out.println("fps---------" + fps);
        if (fps <= 0) {
            throw new ModuleException("fps get fail");
        }
        IntStream.range(0, videoHolder.getTotalFrame()).parallel().mapToObj(i -> {
            Mat mat = new Mat();
            vc.read(mat);
            int curFrame = count.getAndIncrement();
            double time = curFrame / fps * 1000;
            Mat dst = null;
            try {
                dst = openCVService.filter(mat);
                mat.release();
                int blackPixel = openCVService.countBlackPixel(dst);
                if (blackPixel > MIN_PIXEL_COUNT.intValue()) {
                    return new ArchiveMatNode(curFrame, time, dst, blackPixel);
                } else {
                    return null;
                }
            } catch (ModuleException e) {
                e.printStackTrace();
                mat.release();
                return null;
            } finally {
                fxUtil.onFXThread(progressBar.progressProperty(), (double) curFrame / videoHolder.getTotalFrame());
                mat.release();
                return null;
            }
        }).filter(Objects::nonNull).sorted(Comparator.comparingInt(ArchiveMatNode::getNid)).reduce((last, cur) -> {
            double similarity;
            similarity = isSSIM
                    ? openCVService.meanSSIM(last.getMatNode().getMat(), cur.getMatNode().getMat()).val[0]
                    : openCVService.psnr(last.getMatNode().getMat(), cur.getMatNode().getMat());
            if (similarity > threshold) {
                synchronized (archiveMatNodeList) {
                    archiveMatNodeList.add(cur);
                }
                return cur;
            } else {
                mergeArchiveMatNode();
                synchronized (archiveMatNodeList) {
                    archiveMatNodeList.add(cur);
                };
                return cur;
            }
        });


//        while (vc.grab() && !Thread.currentThread().isInterrupted()) {
//            if (frameInterval != 1) {
//                if (count.get() % frameInterval != 0) {
//                    fxUtil.onFXThread(progressBar.progressProperty(), (double) count.get() / videoHolder.getTotalFrame());
//                    count.incrementAndGet();
//                    continue;
//                }
//            }
//            CompletableFuture future = CompletableFuture.runAsync(() -> {
//                int curFrame = count.getAndIncrement();
//
//                fxUtil.onFXThread(progressBar.progressProperty(), (double) curFrame / videoHolder.getTotalFrame());
//                Mat mat = null;
//                Mat dst = null;
//                Mat sampleMat = null;
//                int curIndex = -1;
//                double time;
//                try {
//                    mat = new Mat();
//                    vc.retrieve(mat);
//                    curIndex = index.getAndIncrement();
//                    System.out.println("curindex---------" + curIndex);
//                    sampleMap.put(curIndex, mat.clone());
//                    time = curFrame / fps * 1000;
//                    dst = openCVService.filter(mat);
//                    int blackPixel = openCVService.countBlackPixel(dst);
//                    if (blackPixel > MIN_PIXEL_COUNT.intValue()) {
//                        if (curIndex < 1) {
//                            addToMergeGroup(time, dst, blackPixel);
//                        }
//                        while (!sampleMap.containsKey(curIndex - 1)) {
//                            Thread.sleep(10);
//                        }
//                        sampleMat = sampleMap.getOrDefault(curIndex - 1, new Mat());
//                        if (sampleMat.empty()) {
//                            addToMergeGroup(time, dst, blackPixel);
//                        } else {
//                            double similarity;
//                            similarity = isSSIM
//                                    ? openCVService.meanSSIM(sampleMat, dst).val[0]
//                                    : openCVService.psnr(sampleMat, dst);
//                            if (similarity > threshold) {
//                                synchronized (archiveMatNodeList) {
//                                    archiveMatNodeList.add(new ArchiveMatNode(count.get(), time, dst, blackPixel));
//                                }
//                            } else {
//                                mergeArchiveMatNode();
//                                addToMergeGroup(time, dst, blackPixel);
//                            }
//                        }
//                    }
//                } catch (ModuleException e) {
//                    log.error("filter error", e);
//                } finally {
//                    if (mat != null) {
//                        mat.release();
//                    }
//                    if (dst != null) {
//                        dst.release();
//                    }
//                    if (sampleMat != null) {
//                        sampleMat.release();
//                    }
//                    if (curIndex > -1) {
//                        sampleMap.get(curIndex-1).release();
//                        sampleMap.remove(curIndex - 1);
//                    }
//                    return;
//                }
//            });
//            futures.add(future);
//        }
        if (!vc.grab()) {
            finish = true;
        }
        log.info("grab finish");
//        futures.stream().map(CompletableFuture::join).collect(Collectors.toList());
        log.info("filter finish");
        mergeArchiveMatNode();
        log.info("merge finish");
        appHolder.setMatNodeList(matNodeList
                .stream().map(MatDataNode::getMatNode)
                .sorted(Comparator.comparingInt(MatNode::getNid))
                .collect(Collectors.toList()));
    }

    @Override
    public boolean isVideoLoaded() {
        return vc != null && vc.isOpened();
    }

    @Override
    public boolean isVideoFinish() {
        return finish;
    }

    @Override
    public void closeVideo() {
        if (vc != null && vc.isOpened()) {
            vc.release();
            vc = null;
            System.gc();
        }
    }

    private void addToMergeGroup(final double time, final Mat mat, final int blackPixel) {
        synchronized (archiveMatNodeList) {
            archiveMatNodeList.add(new ArchiveMatNode(count.get(), time, mat, blackPixel));
        }
    }

    private void mergeArchiveMatNode() {
        synchronized (archiveMatNodeList) {
            ArchiveMatNode archiveMatNode;
            int size = archiveMatNodeList.size();
            if (size == 0) {
                return;
            }
            if (size > 1) {
                switch (STORAGE_POLICY.intValue()) {
                    //STORAGE_FIRST
                    case 3:
                        archiveMatNode = archiveMatNodeList.get(0);
                        break;
                    //STORAGE_LAST
                    case 4:
                        archiveMatNode = archiveMatNodeList.get(size - 1);
                        break;
                    //MIN/MAX/MED
                    default:
                        List<ArchiveMatNode> tp = archiveMatNodeList
                                .stream()
                                .sorted(Comparator.comparingInt(ArchiveMatNode::getPixelCount))
                                .collect(Collectors.toList());
                        switch (STORAGE_POLICY.intValue()) {
                            //STORAGE_MIN
                            case 0:
                                archiveMatNode = tp.get(0);
                                break;
                            //STORAGE_MAX
                            case 1:
                                archiveMatNode = tp.get(size - 1);
                                break;
                            //STORAGE_MED
                            default:
                                archiveMatNode = tp.get(size % 2 == 0 ? size / 2 : size / 2 - 1);
                                break;
                        }
                        break;
                }
            } else {
                archiveMatNode = archiveMatNodeList.get(0);
            }
            MatDataNode matNode = archiveMatNode.getMatNode();
            double startTime = archiveMatNodeList.get(0).getMatNode().getStartTime();
            double endTime = archiveMatNodeList.get(size - 1).getMatNode().getStartTime();
            matNode.setStartTime(startTime);
            matNode.setEndTime(endTime);
            log.debug("System merge. Start at {} ({}), end at {} ({}), last {} FrameInterval.",
                    archiveMatNodeList.get(0).getMatNode().getNid(),
                    convertTime(startTime),
                    archiveMatNodeList.get(archiveMatNodeList.size() - 1).getMatNode().getNid(),
                    convertTime(endTime),
                    archiveMatNodeList.size());
            matNodeList.add(matNode);
            archiveMatNodeList.clear();
        }
    }

    @Getter
    @Setter
    public static class ArchiveMatNode {

        private final MatDataNode matNode;
        private final int pixelCount;

        ArchiveMatNode(int count, double time, Mat mat, int pixelCount) {
            this(new MatDataNode(count, time, mat), pixelCount);
        }

        ArchiveMatNode(MatDataNode matNode, int pixelCount) {
            this.matNode = matNode;
            this.pixelCount = pixelCount;
        }

        int getNid(){
            return this.matNode.getNid();
        }

    }

}
