package com.ke.bella.files.utils;

public class StringUtils {
    // 移植String类的hashCode方法，避免jdk升级可能带来的算法变化；
    public static int hashCode(String str) {
        if(str == null) {
            throw new IllegalArgumentException("Input string cannot be null.");
        }
        int h = 0;
        int length = str.length();
        for (int i = 0; i < length; i++) {
            h = 31 * h + str.charAt(i);
        }
        return h;
    }
}
