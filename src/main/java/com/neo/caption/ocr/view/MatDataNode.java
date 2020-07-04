package com.neo.caption.ocr.view;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;

import java.net.URL;
import java.util.ResourceBundle;

import static com.neo.caption.ocr.constant.LayoutName.LAYOUT_MAT_NODE;
import static com.neo.caption.ocr.util.BaseUtil.fxmlURL;

@Getter
@Setter
@Slf4j
public class MatDataNode {

    private final int nid;
    private double startTime;
    private double endTime;
    private final Mat mat;

    public MatDataNode(int nid, double startTime, double endTime, Mat mat) {
        this.nid = nid;
        this.startTime = startTime;
        this.endTime = endTime;
        this.mat = mat.clone();
    }

    public MatDataNode(int nid, double startTime, Mat mat) {
        this(nid, startTime, startTime, mat);
    }

    public MatNode getMatNode() {
        return new MatNode(nid, startTime, endTime, mat);
    }
}
