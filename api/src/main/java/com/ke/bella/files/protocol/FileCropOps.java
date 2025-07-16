package com.ke.bella.files.protocol;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class FileCropOps {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FileCropOp {
        private String fileId;
        /**
         * PDF裁剪区域边界框坐标
         * 格式: [x1, y1, x2, y2]
         * 坐标系统: 左上角原点 (0,0)，X轴向右递增，Y轴向下递增
         * 坐标单位: PDF用户单位 (User Units)，默认 1用户单位 = 1/72英寸
         * 坐标来源: PyMuPDF (fitz) 提取的标准PDF坐标
         * 示例: [201.0, 79.7, 410.8, 111.1] 表示
         * - 左上角: (201.0, 79.7) PDF用户单位
         * - 右下角: (410.8, 111.1) PDF用户单位
         * - 区域宽度: 410.8 - 201.0 = 209.8 用户单位
         * - 区域高度: 111.1 - 79.7 = 31.4 用户单位
         */
        private List<Double> bbox;

        /**
         * PDF页面编号，从1开始
         * 默认值: 1 (第一页)
         */
        private Integer page = 1;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class FileCropResponse {
        private String imageBase64;
    }
}
